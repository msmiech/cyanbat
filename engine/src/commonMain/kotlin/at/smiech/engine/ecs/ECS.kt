package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Input
import kotlin.reflect.KClass

/**
 * Marker interface for all components.
 */
interface Component

/**
 * A unique identifier for an entity.
 *
 * Ids are recycled once a removal is finalised, which only ever happens at the end of
 * [World.update]. Holding an id across frames after removing the entity is therefore unsafe: it
 * may by then name a different entity. Within a single update an id is stable.
 */
typealias EntityId = Int

/**
 * Base class for all systems that process entities.
 */
abstract class GameSystem {
    /**
     * Called once, when the system is handed to [World.addSystem]. Systems resolve their
     * [ComponentMapper]s here so the per-frame work never has to look a component type up by class.
     */
    open fun onAttach(world: World) {}

    abstract fun update(world: World, deltaTime: Float, input: Input?)
    open fun draw(world: World, graphics: Graphics) {}
}

/**
 * A pre-resolved handle to one component type's storage.
 *
 * Reading through a mapper is a plain array index, where [World.getComponent] has to hash the
 * [KClass] first. Systems resolve theirs in [GameSystem.onAttach] and keep them for the lifetime of
 * the world, which is what makes the per-entity work in the hot loops lookup-free.
 */
class ComponentMapper<T : Component> internal constructor(
    private val world: World,
    private val typeId: Int
) {
    /** Bit this type occupies in an entity signature. Query masks are built from these. */
    internal val bit: Long = 1L shl typeId

    @Suppress("UNCHECKED_CAST")
    operator fun get(id: EntityId): T? = world.componentAt(typeId, id) as T?

    /** The component of an entity a query has already proved carries it. */
    @Suppress("UNCHECKED_CAST")
    fun require(id: EntityId): T = world.componentAt(typeId, id) as T

    fun has(id: EntityId): Boolean = world.signatureOf(id) and bit != 0L
}

/**
 * The World manages entities, components, and systems.
 *
 * Storage is struct-of-arrays rather than nested maps: every array below is indexed by entity id,
 * so reading a component is one array load and a query is one bitmask test per live entity, with
 * no boxing of ids anywhere. Ids are dense and recycled precisely so those arrays stay the size of
 * the live population instead of growing with everything a run has ever spawned.
 */
class World {
    private val typeIds = HashMap<KClass<out Component>, Int>()
    private var typeCount = 0

    /** stores[typeId][entityId] - the component, or null when that entity lacks the type. */
    private var stores = arrayOfNulls<Array<Component?>>(INITIAL_TYPE_CAPACITY)

    private var capacity = INITIAL_ENTITY_CAPACITY

    /** Bitmask of the component types an entity carries, indexed by id. */
    private var signatures = LongArray(capacity)
    private var isAlive = BooleanArray(capacity)
    private var isPendingRemoval = BooleanArray(capacity)

    /** Live entity ids in creation order - the iteration order every query inherits. */
    private var live = IntArray(capacity)
    private var liveCount = 0

    private var freeIds = IntArray(capacity)
    private var freeCount = 0
    private var nextEntityId = 0

    private var pendingRemoval = IntArray(INITIAL_ENTITY_CAPACITY)
    private var pendingCount = 0

    private val systems = mutableListOf<GameSystem>()

    // region entities

    fun createEntity(): EntityId {
        val id: EntityId
        if (freeCount > 0) {
            id = freeIds[--freeCount]
        } else {
            id = nextEntityId++
            growTo(nextEntityId)
        }
        signatures[id] = 0L
        isAlive[id] = true
        isPendingRemoval[id] = false
        live[liveCount++] = id
        return id
    }

    /**
     * Marks an entity for removal at the end of the current [update].
     *
     * Removing something already gone, or already queued, is a no-op - a frame routinely lands two
     * hits on the same entity.
     */
    fun removeEntity(id: EntityId) {
        if (id < 0 || id >= capacity || !isAlive[id] || isPendingRemoval[id]) return
        isPendingRemoval[id] = true
        if (pendingCount == pendingRemoval.size) {
            pendingRemoval = pendingRemoval.copyOf(pendingCount * 2)
        }
        pendingRemoval[pendingCount++] = id
    }

    // endregion

    // region components

    fun <T : Component> addComponent(id: EntityId, component: T) {
        val typeId = typeIdOf(component::class)
        stores[typeId]!![id] = component
        signatures[id] = signatures[id] or (1L shl typeId)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponent(id: EntityId, type: KClass<T>): T? {
        val typeId = typeIds[type] ?: return null
        if (id < 0 || id >= capacity) return null
        return stores[typeId]!![id] as T?
    }

    fun <T : Component> hasComponent(id: EntityId, type: KClass<T>): Boolean {
        val typeId = typeIds[type] ?: return false
        if (id < 0 || id >= capacity) return false
        return signatures[id] and (1L shl typeId) != 0L
    }

    /**
     * Resolves a lasting handle to one component type. Call it once - from [GameSystem.onAttach] -
     * rather than per frame.
     */
    fun <T : Component> mapper(type: KClass<T>): ComponentMapper<T> =
        ComponentMapper(this, typeIdOf(type))

    internal fun componentAt(typeId: Int, id: EntityId): Component? {
        if (id < 0 || id >= capacity) return null
        return stores[typeId]!![id]
    }

    internal fun signatureOf(id: EntityId): Long =
        if (id < 0 || id >= capacity) 0L else signatures[id]

    // endregion

    // region systems

    fun addSystem(system: GameSystem) {
        systems.add(system)
        system.onAttach(this)
    }

    fun update(deltaTime: Float, input: Input?) {
        for (i in systems.indices) {
            systems[i].update(this, deltaTime, input)
        }
        finalizeRemovals()
    }

    fun draw(graphics: Graphics) {
        for (i in systems.indices) {
            systems[i].draw(this, graphics)
        }
    }

    // endregion

    // region queries

    /**
     * Snapshot of the entities carrying every one of [types], in creation order.
     *
     * Allocates a list; [forEach] is the allocation-free path the systems take.
     */
    fun query(vararg types: KClass<out Component>): List<EntityId> {
        var mask = 0L
        for (type in types) {
            // A type nothing has registered can never match, and registering it here would burn a
            // signature bit on a component the world does not use.
            val typeId = typeIds[type] ?: return emptyList()
            mask = mask or (1L shl typeId)
        }
        val result = ArrayList<EntityId>()
        for (i in 0 until liveCount) {
            val id = live[i]
            if (signatures[id] and mask == mask) result.add(id)
        }
        return result
    }

    /**
     * Runs [action] for every entity carrying all of the mapped components, in creation order.
     *
     * The live set is sampled up front, so entities spawned from inside [action] wait for the next
     * pass - the snapshot semantics [query] has always had. Entities removed from inside it are
     * still visited, because removal only lands at the end of the update.
     */
    fun forEach(a: ComponentMapper<*>, action: (EntityId) -> Unit) =
        forEachMatching(a.bit, action)

    fun forEach(a: ComponentMapper<*>, b: ComponentMapper<*>, action: (EntityId) -> Unit) =
        forEachMatching(a.bit or b.bit, action)

    fun forEach(
        a: ComponentMapper<*>,
        b: ComponentMapper<*>,
        c: ComponentMapper<*>,
        action: (EntityId) -> Unit
    ) = forEachMatching(a.bit or b.bit or c.bit, action)

    fun forEach(
        a: ComponentMapper<*>,
        b: ComponentMapper<*>,
        c: ComponentMapper<*>,
        d: ComponentMapper<*>,
        action: (EntityId) -> Unit
    ) = forEachMatching(a.bit or b.bit or c.bit or d.bit, action)

    /** How many live entities carry all of the mapped components. */
    fun count(vararg mappers: ComponentMapper<*>): Int {
        var mask = 0L
        for (mapper in mappers) mask = mask or mapper.bit
        var found = 0
        for (i in 0 until liveCount) {
            if (signatures[live[i]] and mask == mask) found++
        }
        return found
    }

    private inline fun forEachMatching(mask: Long, action: (EntityId) -> Unit) {
        val sampled = liveCount
        for (i in 0 until sampled) {
            val id = live[i]
            if (signatures[id] and mask == mask) action(id)
        }
    }

    // endregion

    private fun typeIdOf(type: KClass<out Component>): Int {
        typeIds[type]?.let { return it }

        val typeId = typeCount++
        require(typeId < MAX_COMPONENT_TYPES) {
            "A World supports at most $MAX_COMPONENT_TYPES component types, one per signature bit"
        }
        typeIds[type] = typeId
        if (typeId >= stores.size) {
            stores = stores.copyOf(stores.size * 2)
        }
        stores[typeId] = arrayOfNulls(capacity)
        return typeId
    }

    private fun growTo(needed: Int) {
        if (needed <= capacity) return

        var grown = capacity
        while (grown < needed) grown *= 2

        signatures = signatures.copyOf(grown)
        isAlive = isAlive.copyOf(grown)
        isPendingRemoval = isPendingRemoval.copyOf(grown)
        live = live.copyOf(grown)
        freeIds = freeIds.copyOf(grown)
        for (typeId in 0 until typeCount) {
            stores[typeId] = stores[typeId]!!.copyOf(grown)
        }
        capacity = grown
    }

    /**
     * Drops everything queued by [removeEntity] and hands the ids back to the free list.
     *
     * The live list is compacted rather than swap-removed, so creation order - which render
     * ordering and the collision pair order both rest on - survives.
     */
    private fun finalizeRemovals() {
        if (pendingCount == 0) return

        for (i in 0 until pendingCount) {
            val id = pendingRemoval[i]
            val signature = signatures[id]
            for (typeId in 0 until typeCount) {
                if (signature and (1L shl typeId) != 0L) stores[typeId]!![id] = null
            }
            signatures[id] = 0L
            isAlive[id] = false
            isPendingRemoval[id] = false
            freeIds[freeCount++] = id
        }
        pendingCount = 0

        var kept = 0
        for (i in 0 until liveCount) {
            val id = live[i]
            if (isAlive[id]) live[kept++] = id
        }
        liveCount = kept
    }

    private companion object {
        const val INITIAL_ENTITY_CAPACITY = 64
        const val INITIAL_TYPE_CAPACITY = 16

        /** One bit per component type in an entity signature, which is a Long. */
        const val MAX_COMPONENT_TYPES = 64
    }
}
