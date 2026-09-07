package at.smiech.engine.ecs

import at.smiech.engine.Input

/**
 * Reports every overlapping pair of collidables to [onCollision], in creation order.
 *
 * The pair test is quadratic, so it is the one place where per-entity bookkeeping really costs.
 * Each pass therefore gathers the collidables once into primitive arrays - already shrunk by their
 * tolerance - and the inner loop then touches nothing but floats and ints: no component lookups,
 * no [at.smiech.engine.math.Rect] per pair, no allocation at all.
 */
class CollisionSystem(
    private val onCollision: (EntityId, EntityId) -> Unit
) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var collisions: ComponentMapper<CollisionComponent>

    private var ids = IntArray(INITIAL_CAPACITY)
    private var groups = IntArray(INITIAL_CAPACITY)
    private var lefts = FloatArray(INITIAL_CAPACITY)
    private var tops = FloatArray(INITIAL_CAPACITY)
    private var rights = FloatArray(INITIAL_CAPACITY)
    private var bottoms = FloatArray(INITIAL_CAPACITY)

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        collisions = world.mapper(CollisionComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        val count = gather(world)

        for (i in 0 until count) {
            val group1 = groups[i]
            val left1 = lefts[i]
            val top1 = tops[i]
            val right1 = rights[i]
            val bottom1 = bottoms[i]

            for (j in i + 1 until count) {
                // Don't collide objects in the same group or friendly groups if needed
                if (SKIP[group1 * GROUP_COUNT + groups[j]]) continue

                if (left1 < rights[j] && right1 > lefts[j] &&
                    top1 < bottoms[j] && bottom1 > tops[j]
                ) {
                    onCollision(ids[i], ids[j])
                }
            }
        }
    }

    /** Snapshots the collidables into the parallel arrays, returning how many there are. */
    private fun gather(world: World): Int {
        var count = 0
        world.forEach(transforms, collisions) { id ->
            if (count == ids.size) grow(count * 2)

            val collision = collisions.require(id)
            val rect = transforms.require(id).rect
            val tolerance = collision.tolerance

            ids[count] = id
            groups[count] = collision.group.ordinal
            lefts[count] = rect.left + tolerance
            tops[count] = rect.top + tolerance
            rights[count] = rect.right - tolerance
            bottoms[count] = rect.bottom - tolerance
            count++
        }
        return count
    }

    private fun grow(size: Int) {
        ids = ids.copyOf(size)
        groups = groups.copyOf(size)
        lefts = lefts.copyOf(size)
        tops = tops.copyOf(size)
        rights = rights.copyOf(size)
        bottoms = bottoms.copyOf(size)
    }

    private companion object {
        const val INITIAL_CAPACITY = 64

        val GROUP_COUNT = CollisionGroup.entries.size

        /**
         * Which ordered group pairs never collide, flattened to `g1 * GROUP_COUNT + g2`. Resolving
         * this by table keeps the branchy rule set out of the quadratic loop.
         */
        val SKIP = BooleanArray(GROUP_COUNT * GROUP_COUNT) { index ->
            val g1 = CollisionGroup.entries[index / GROUP_COUNT]
            val g2 = CollisionGroup.entries[index % GROUP_COUNT]
            when {
                g1 == g2 -> true
                g1 == CollisionGroup.PLAYER && g2 == CollisionGroup.PLAYER_PROJECTILE -> true
                g2 == CollisionGroup.PLAYER && g1 == CollisionGroup.PLAYER_PROJECTILE -> true
                g1 == CollisionGroup.ENEMY && g2 == CollisionGroup.ENEMY_PROJECTILE -> true
                g2 == CollisionGroup.ENEMY && g1 == CollisionGroup.ENEMY_PROJECTILE -> true
                else -> false
            }
        }
    }
}
