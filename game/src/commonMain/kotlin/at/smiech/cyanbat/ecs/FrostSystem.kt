package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.util.FROST_DRIFT
import at.smiech.cyanbat.util.FROST_THAW_SECONDS
import at.smiech.cyanbat.util.FROST_TINT
import at.smiech.engine.EngineColors
import at.smiech.engine.Input
import at.smiech.engine.ecs.Component
import at.smiech.engine.ecs.ComponentMapper
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.PaceComponent
import at.smiech.engine.ecs.TintComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World

/**
 * How long an enemy has left frozen; see [FrostSystem]. Zero when not frozen: disarmed rather than
 * removed, like other components here.
 */
class FrostComponent(var seconds: Float = 0f) : Component {
    /** Whether any freeze is left. */
    val frozen: Boolean get() = seconds > 0f
}

/**
 * Holds every frozen enemy still (its flight, wingbeat and gun all run on its [PaceComponent]) and
 * drifts it with the scenery, tinted [FROST_TINT], until it thaws. That a frozen enemy is harmless
 * to the bat is the run's decision; see [isFrozen].
 *
 * The pace is zeroed every tick rather than once, since a wound sets it too; the run leaves a
 * frozen enemy's pace alone and restores it through [onThaw]. The drift is the only movement: the
 * enemy's own velocity is kept, and it carries on where it left off.
 *
 * Add it after the movement and before the collisions, so a frozen enemy is met
 * where it drifted to.
 *
 * @param onThaw called as each enemy thaws, to restore its pace, which depends on its wounds.
 */
class FrostSystem(private val onThaw: (EntityId) -> Unit) : GameSystem() {
    private lateinit var frosts: ComponentMapper<FrostComponent>
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var paces: ComponentMapper<PaceComponent>
    private lateinit var tints: ComponentMapper<TintComponent>

    override fun onAttach(world: World) {
        frosts = world.mapper(FrostComponent::class)
        transforms = world.mapper(TransformComponent::class)
        paces = world.mapper(PaceComponent::class)
        tints = world.mapper(TintComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(frosts, transforms) { id ->
            val frost = frosts.require(id)
            if (!frost.frozen) return@forEach

            frost.seconds -= deltaTime
            if (frost.seconds <= 0f) {
                frost.seconds = 0f
                tints[id]?.color = 0
                onThaw(id)
                return@forEach
            }

            paces[id]?.apply {
                motion = 0f
                fire = 0f
            }
            tints[id]?.color = tintFor(frost.seconds)
            val transform = transforms.require(id)
            transform.rect = transform.rect.offset(FROST_DRIFT, 0f)
            // Removed once it drifts off the left: the lifetime cull only takes what travels that
            // way, and something frozen mid-loop can be pointing back the way it came.
            if (transform.rect.right < 0f) world.removeEntity(id)
        }
    }

    companion object {
        /**
         * Freezes [id] for [seconds], or leaves it frozen longer if it already is: a second beam
         * never cuts a freeze short.
         *
         * Zeroes its pace at once, so nothing it was about to do this tick escapes the beam.
         */
        fun freeze(world: World, id: EntityId, seconds: Float) {
            val frost = world.getComponent(id, FrostComponent::class)
                ?: FrostComponent().also { world.addComponent(id, it) }
            frost.seconds = maxOf(frost.seconds, seconds)
            world.getComponent(id, PaceComponent::class)?.apply {
                motion = 0f
                fire = 0f
            }
            val tint = world.getComponent(id, TintComponent::class)
                ?: TintComponent().also { world.addComponent(id, it) }
            tint.color = tintFor(frost.seconds)
        }

        /** Whether [id] is frozen now. */
        fun isFrozen(world: World, id: EntityId): Boolean =
            world.getComponent(id, FrostComponent::class)?.frozen == true

        /** The tint for [seconds] of freeze left: full strength until the thaw begins. */
        fun tintFor(seconds: Float): Int =
            EngineColors.scaleAlpha(FROST_TINT, (seconds / FROST_THAW_SECONDS).coerceIn(0f, 1f))
    }
}
