package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.util.ORB_RADIUS
import at.smiech.cyanbat.util.ORB_SECONDS_PER_TURN
import at.smiech.cyanbat.util.ORB_SETTLE_SECONDS
import at.smiech.engine.Input
import at.smiech.engine.ecs.Component
import at.smiech.engine.ecs.ComponentMapper
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.math.cos
import kotlin.math.sin

/**
 * One of the orbs circling the bat; see [OrbitSystem].
 *
 * @param offset where on the ring it sits, in radians clockwise from the ring's own turn. Eased by
 *   [OrbitSystem] toward its share of the circle, which changes as orbs are added.
 */
class OrbComponent(var offset: Float = 0f) : Component

/**
 * Carries every [OrbComponent] clockwise round the box [center] returns (the bat's), [ORB_RADIUS]
 * out, once every [ORB_SECONDS_PER_TURN].
 *
 * The orbs share the circle evenly in creation order. A new orb is placed at its share ([shareOf]),
 * and the others ease to their new shares over about [ORB_SETTLE_SECONDS], so the ring spreads out
 * rather than every orb jumping at once.
 *
 * Add it after the movement that carries the bat, so the ring follows the bat this tick, and before
 * the collisions, so an orb hits what it is drawn over.
 *
 * @param center the box the orbs go round, or null to hold them where they are.
 */
class OrbitSystem(private val center: () -> Rect?) : GameSystem() {
    private lateinit var orbs: ComponentMapper<OrbComponent>
    private lateinit var transforms: ComponentMapper<TransformComponent>

    /** How far round the ring has turned, in radians, clockwise. */
    var turn = 0f
        private set

    override fun onAttach(world: World) {
        orbs = world.mapper(OrbComponent::class)
        transforms = world.mapper(TransformComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        if (center() == null) return
        turn = (turn + TWO_PI * deltaTime / ORB_SECONDS_PER_TURN) % TWO_PI
        arrange(world, settle = (deltaTime / ORB_SETTLE_SECONDS).coerceAtMost(1f))
    }

    /**
     * Puts every orb in place on the ring without turning it, for an orb just made, which would
     * otherwise sit on the bat until the next tick.
     *
     * @param settle how far of the way to its share each orb eases, as 0..1.
     */
    fun arrange(world: World, settle: Float = 0f) {
        val around = center() ?: return
        val count = world.count(orbs, transforms)
        if (count == 0) return
        var index = 0
        world.forEach(orbs, transforms) { id ->
            val orb = orbs.require(id)
            orb.offset += towards(shareOf(index++, count) - orb.offset) * settle

            // Clockwise, because the frame's y runs down.
            val angle = turn + orb.offset
            val transform = transforms.require(id)
            val rect = transform.rect
            transform.rect = Rect.fromLTWH(
                around.centerX + ORB_RADIUS * cos(angle) - rect.width / 2f,
                around.centerY + ORB_RADIUS * sin(angle) - rect.height / 2f,
                rect.width,
                rect.height,
            )
        }
    }

    companion object {
        private const val PI_F = 3.1415927f
        private const val TWO_PI = 6.2831855f

        /** Where orb [index] of [count] belongs on the ring: an even share of it, in radians. */
        fun shareOf(index: Int, count: Int): Float = TWO_PI * index / count

        /**
         * [radians] the short way round, into -pi..pi, so an orb easing to a share just past the
         * top of the ring does not go all the way back round.
         */
        private fun towards(radians: Float): Float {
            var turned = radians % TWO_PI
            if (turned > PI_F) turned -= TWO_PI
            if (turned <= -PI_F) turned += TWO_PI
            return turned
        }
    }
}
