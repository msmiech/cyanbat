package at.smiech.engine.ecs

import at.smiech.engine.Input

/**
 * Reports every overlapping pair of collidables to [onCollision], in creation order.
 *
 * The pair test is quadratic, so each pass first gathers the collidables into primitive arrays,
 * already shrunk by their tolerance; the inner loop then touches only floats and ints, with no
 * component lookups and no allocation.
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
                // Same or friendly groups never collide; see SKIP.
                if (SKIP[group1 * GROUP_COUNT + groups[j]]) continue

                if (left1 < rights[j] && right1 > lefts[j] &&
                    top1 < bottoms[j] && bottom1 > tops[j]
                ) {
                    onCollision(ids[i], ids[j])
                }
            }
        }
    }

    /**
     * Whether [a] and [b] overlap as a pass tests them: each box shrunk by its tolerance, with the
     * same arithmetic as [update], so the answer matches the pass exactly. For a handler that needs
     * to know mid-pass whether the pass meets (or has met) another pair. Groups are not checked.
     */
    fun overlaps(a: EntityId, b: EntityId): Boolean {
        val rectA = transforms[a]?.rect ?: return false
        val rectB = transforms[b]?.rect ?: return false
        val toleranceA = collisions[a]?.tolerance ?: return false
        val toleranceB = collisions[b]?.tolerance ?: return false
        return rectA.left + toleranceA < rectB.right - toleranceB &&
                rectA.right - toleranceA > rectB.left + toleranceB &&
                rectA.top + toleranceA < rectB.bottom - toleranceB &&
                rectA.bottom - toleranceA > rectB.top + toleranceB
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
         * Which ordered group pairs never collide, flattened to `g1 * GROUP_COUNT + g2`, so the
         * rules cost one lookup in the quadratic loop.
         */
        val SKIP = BooleanArray(GROUP_COUNT * GROUP_COUNT) { index ->
            val g1 = CollisionGroup.entries[index / GROUP_COUNT]
            val g2 = CollisionGroup.entries[index % GROUP_COUNT]
            when {
                g1 == g2 -> true
                g1 == CollisionGroup.PLAYER_CONTACT -> g2 != CollisionGroup.ENEMY
                g2 == CollisionGroup.PLAYER_CONTACT -> g1 != CollisionGroup.ENEMY
                g1 == CollisionGroup.PLAYER && g2 == CollisionGroup.PLAYER_PROJECTILE -> true
                g2 == CollisionGroup.PLAYER && g1 == CollisionGroup.PLAYER_PROJECTILE -> true
                g1 == CollisionGroup.ENEMY && g2 == CollisionGroup.ENEMY_PROJECTILE -> true
                g2 == CollisionGroup.ENEMY && g1 == CollisionGroup.ENEMY_PROJECTILE -> true
                else -> false
            }
        }
    }
}
