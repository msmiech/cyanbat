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
 * How long an enemy has left frozen; see [FrostSystem]. Zero for one that is not frozen, or is no
 * longer: it is disarmed rather than taken off, as components are here.
 */
class FrostComponent(var seconds: Float = 0f) : Component {
    val frozen: Boolean get() = seconds > 0f
}

/**
 * Holds every frozen enemy still - its flight, its wingbeat and its gun, all of which run on its
 * [PaceComponent] - and drifts it with the scenery, washed in [FROST_TINT], until its time is up.
 * What it does to the bat meanwhile, which is nothing, is the run's to decide; see [isFrozen].
 *
 * The pace is held at nothing every tick rather than once, since a wound sets it too; the run leaves a
 * frozen enemy's pace alone, and is handed it back through [onThaw] as the frost lets go. The drift is
 * the whole of how something frozen moves: its own velocity is still there, held still by its pace, and
 * it picks up where it left off.
 *
 * Add it after the movement, which a frozen pace has held still, and before the collisions, so a
 * frozen enemy is met where it has drifted to.
 *
 * @param onThaw called as each one comes free, to put its pace back: what that is - a wounded enemy is
 *   slower than a whole one - is the run's business.
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
            // Gone once it has drifted off the left. The lifetime cull only takes what is travelling
            // that way, and something frozen mid loop can be pointing back the way it came.
            if (transform.rect.right < 0f) world.removeEntity(id)
        }
    }

    companion object {
        /**
         * Freezes [id] for [seconds] from now, or leaves it frozen for longer if it already is: a
         * second beam never cuts a freeze short.
         *
         * Holds its pace at once rather than from the next tick, so nothing it was about to do this
         * tick - a step, a shot - gets out after the beam has caught it.
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

        /** The wash over something with [seconds] of its freeze left: full until the thaw sets in. */
        fun tintFor(seconds: Float): Int =
            EngineColors.scaleAlpha(FROST_TINT, (seconds / FROST_THAW_SECONDS).coerceIn(0f, 1f))
    }
}
