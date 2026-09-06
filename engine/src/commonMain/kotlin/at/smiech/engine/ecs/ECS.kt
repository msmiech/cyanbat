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
 */
typealias EntityId = Int

/**
 * Base class for all systems that process entities.
 */
abstract class GameSystem {
    abstract fun update(world: World, deltaTime: Float, input: Input?)
    open fun draw(world: World, graphics: Graphics) {}
}

/**
 * The World manages entities, components, and systems.
 */
class World {
    private var nextEntityId: EntityId = 0
    private val entities = mutableSetOf<EntityId>()
    private val components = mutableMapOf<KClass<out Component>, MutableMap<EntityId, Component>>()
    private val systems = mutableListOf<GameSystem>()
    
    private val entitiesToRemove = mutableSetOf<EntityId>()

    fun createEntity(): EntityId {
        val id = nextEntityId++
        entities.add(id)
        return id
    }

    fun removeEntity(id: EntityId) {
        entitiesToRemove.add(id)
    }

    fun <T : Component> addComponent(id: EntityId, component: T) {
        val type = component::class
        components.getOrPut(type) { mutableMapOf() }[id] = component
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponent(id: EntityId, type: KClass<T>): T? {
        return components[type]?.get(id) as? T
    }
    
    fun <T : Component> hasComponent(id: EntityId, type: KClass<T>): Boolean {
        return components[type]?.containsKey(id) == true
    }

    fun addSystem(system: GameSystem) {
        systems.add(system)
    }

    fun update(deltaTime: Float, input: Input?) {
        systems.forEach { it.update(this, deltaTime, input) }
        
        // Finalize removals
        entitiesToRemove.forEach { id ->
            entities.remove(id)
            components.values.forEach { it.remove(id) }
        }
        entitiesToRemove.clear()
    }

    fun draw(graphics: Graphics) {
        systems.forEach { it.draw(this, graphics) }
    }

    fun query(vararg types: KClass<out Component>): List<EntityId> {
        if (types.isEmpty()) return entities.toList()
        
        return entities.filter { id ->
            types.all { type -> components[type]?.containsKey(id) == true }
        }
    }
}
