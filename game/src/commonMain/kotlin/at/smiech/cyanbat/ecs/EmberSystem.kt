package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.util.CACO_IMP_EMBER_DRAG
import at.smiech.cyanbat.util.CACO_IMP_EMBER_FUSE_SECONDS
import at.smiech.cyanbat.util.EMBER_FLASH_TINT
import at.smiech.engine.EngineColors
import at.smiech.engine.Input
import at.smiech.engine.ecs.Component
import at.smiech.engine.ecs.ComponentMapper
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.TintComponent
import at.smiech.engine.ecs.VelocityComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Vector2
import kotlin.math.exp
import kotlin.math.sin

/**
 * An ember the Caco Imp spat: an enemy shot that slows to a stop and bursts into a ring once its
 * fuse has burnt down; see [EmberSystem].
 *
 * Shot down or spent on the bat first, it is gone like any shot, and never bursts: the ring is what
 * the player saves themselves by shooting it.
 *
 * @param fuse seconds left until it bursts.
 */
class EmberComponent(var fuse: Float = CACO_IMP_EMBER_FUSE_SECONDS) : Component

/**
 * Burns down every [EmberComponent]: slows it by its drag, flashes it white-hot quicker and
 * stronger as its fuse runs out (on its sprite, so the warning reads by day and in a cave full of
 * light alike), and at the end of its fuse hands it to [onBurst] and removes it.
 *
 * Holds no ember from one tick to the next, so an ember shot down in between is simply not found.
 *
 * @param onBurst called with an ember as it bursts, before it is removed; the game is what knows
 *   how to fire its ring.
 */
class EmberSystem(private val onBurst: (EntityId) -> Unit) : GameSystem() {
    private lateinit var embers: ComponentMapper<EmberComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>
    private lateinit var tints: ComponentMapper<TintComponent>

    override fun onAttach(world: World) {
        embers = world.mapper(EmberComponent::class)
        velocities = world.mapper(VelocityComponent::class)
        tints = world.mapper(TintComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        // Per second, not per tick, so the drag means the same at any tick length.
        val keep = exp(-CACO_IMP_EMBER_DRAG * deltaTime)
        world.forEach(embers, velocities) { id ->
            val ember = embers.require(id)
            val velocity = velocities.require(id)
            velocity.velocity = Vector2(velocity.velocity.x * keep, velocity.velocity.y * keep)
            ember.fuse -= deltaTime
            tints[id]?.color = flash(ember.fuse)
            if (ember.fuse <= 0f) {
                onBurst(id)
                world.removeEntity(id)
            }
        }
    }

    /**
     * The flash on an ember with [fuse] seconds left: none for the first stretch of its fuse, then
     * a pulse that quickens and strengthens toward the burst.
     */
    private fun flash(fuse: Float): Int {
        val left = (fuse / FLASHING_SECONDS).coerceIn(0f, 1f)
        if (left >= 1f) return 0
        val urgency = 1f - left
        val pulse = 0.5f + 0.5f * sin((FLASHING_SECONDS - fuse) * (PULSE + PULSE * 3f * urgency))
        return EngineColors.withAlpha(EMBER_FLASH_TINT, FLASH_ALPHA * urgency * pulse)
    }

    private companion object {
        /** How long before it bursts an ember starts to flash. */
        const val FLASHING_SECONDS = 1.1f

        /** How fast the flash pulses at first, in radians a second; it ends four times as fast. */
        const val PULSE = 12f

        /** How strongly it flashes at the last. */
        const val FLASH_ALPHA = 0.85f
    }
}
