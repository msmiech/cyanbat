package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.PlayerControlComponent.Companion.NO_POINTER
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2

/**
 * Basic components for game entities.
 */

data class TransformComponent(var rect: Rect) : Component

data class VelocityComponent(var velocity: Vector2) : Component

/**
 * @param baseSrcX x offset of the entity's frame strip within the sprite sheet. Animation frames
 *   are addressed relative to it, so several strips can share one sheet.
 * @param scale how many framebuffer pixels one source pixel covers. The sheet holds exactly one
 *   size of every sprite, so this is what lets a boss be that same artwork drawn large. Keep it in
 *   step with the entity's [TransformComponent], which is what collisions are read from.
 * @param rotationDegrees how far the artwork is turned when it is drawn, clockwise, about the
 *   sprite's own center. Purely cosmetic: the entity's [TransformComponent] stays the upright box
 *   collisions are read from, because a rotated hitbox would make a near miss depend on an angle
 *   the player cannot measure. [FacingSystem] keeps it pointing where an entity is going.
 */
data class SpriteComponent(
    val pixmap: Pixmap,
    val baseSrcX: Int = 0,
    var srcX: Int = baseSrcX,
    var srcY: Int = 0,
    var srcWidth: Int = pixmap.width,
    var srcHeight: Int = pixmap.height,
    var scale: Float = 1f,
    var rotationDegrees: Float = 0f,
) : Component

/**
 * Turns an entity's sprite to point wherever it is travelling, every frame.
 *
 * A marker: the angle itself lives on the [SpriteComponent] and the work is [FacingSystem]'s. It
 * is opt-in because most things should not do this - the bat and the enemies are drawn from
 * artwork that has an up, and spinning them to match a dodge would read as a glitch. What wants it
 * is anything whose direction of travel is the only thing its shape means, which is projectiles.
 */
class FacesVelocityComponent : Component

data class AnimationComponent(
    val frameWidth: Int,
    val frameHeight: Int,
    val frameCount: Int,
    val interval: Float,
    val isLooping: Boolean = true,
    var currentTime: Float = 0f,
    var currentFrame: Int = 0,
    var isFinished: Boolean = false
) : Component

data class CollisionComponent(
    val tolerance: Float = 0f,
    val group: CollisionGroup = CollisionGroup.OTHER
) : Component

enum class CollisionGroup {
    PLAYER, ENEMY, PLAYER_PROJECTILE, ENEMY_PROJECTILE, OBSTACLE, OTHER
}

/**
 * How much damage an entity has left in it.
 *
 * @param hitPoints what remains. Reaching zero is what clears [alive]; the caller applying the
 *   damage owns that step, because what dying means differs from entity to entity.
 * @param maxHitPoints the size of the bar, kept so a [HealthBarComponent] can draw a fraction
 *   rather than an absolute count. Mutable because an entity can be made hardier mid-run - the
 *   player picking up more health is exactly that - and the bar has to grow with it.
 */
data class HealthComponent(
    var hitPoints: Int = 1,
    var maxHitPoints: Int = hitPoints,
    var alive: Boolean = true,
) : Component {
    /** Health left, as 0..1. */
    val fraction: Float
        get() = if (maxHitPoints <= 0) 0f else (hitPoints.toFloat() / maxHitPoints).coerceIn(0f, 1f)
}

/**
 * What this entity takes off whatever it collides with.
 *
 * Damage rides on the entity dealing it rather than sitting in one global constant, because that
 * is what lets a late wave hit harder than an early one without touching anything already in
 * flight: the spawner decides how hard the enemies it makes hit, and the collision handler only
 * has to read it off. An entity without one falls back to whatever default the game applies.
 */
/**
 * @param isCritical whether this damage is a critical one. It rides with the amount rather than
 *   sitting in a component of its own because the two are one fact: [amount] is already the raised
 *   number by the time anything reads it, and this is what lets whatever reports the hit say *why*
 *   it was that big. A projectile carrying it crits everything it goes on to hit, which is what
 *   "the shot is critical" has to mean once shots can pierce.
 */
data class DamageComponent(val amount: Int, val isCritical: Boolean = false) : Component

/**
 * Draws a bar of the entity's remaining [HealthComponent.fraction] just below it.
 *
 * Opt-in per entity rather than automatic: that is what keeps a screen full of enemies bare while
 * the player still gets to see their own health.
 *
 * @param height bar thickness, in framebuffer pixels.
 * @param offsetY gap between the bottom of the entity and the top of the bar.
 * @param fullColor the filled part, drawn from the left edge rightwards.
 * @param emptyColor the spent part, and so the whole bar once health runs out.
 */
data class HealthBarComponent(
    val height: Float = 3f,
    val offsetY: Float = 2f,
    val fullColor: Int = EngineColors.RED,
    val emptyColor: Int = EngineColors.BLACK,
) : Component

/**
 * A short-lived piece of text that drifts along its [VelocityComponent] and fades as it goes -
 * damage numbers, and anything else that has to be read off the spot where it happened.
 *
 * @param duration how long it lives, in seconds. Alpha runs from full to nothing across it.
 * @param elapsed time already spent, advanced by [FloatingTextSystem].
 */
data class FloatingTextComponent(
    val text: String,
    val fontSize: Int = 12,
    val color: Int = EngineColors.WHITE,
    val outlineColor: Int = EngineColors.BLACK,
    val duration: Float = 0.6f,
    var elapsed: Float = 0f,
) : Component

/**
 * Lets a projectile survive the things it hits instead of being spent by the first one.
 *
 * @param remaining how many more targets it can pass through. At zero the next hit spends it.
 * @param hitIds what it has already gone through, so an overlap lasting several frames costs one
 *   pierce and lands one hit rather than one of each per frame. Ids are recycled, so in principle
 *   a long-lived projectile could mistake a new entity for one it has already passed; a shot
 *   crosses the screen in well under a second, and the cost of the mistake is a miss.
 */
data class PierceComponent(
    var remaining: Int,
    val hitIds: MutableSet<EntityId> = mutableSetOf(),
) : Component {
    /**
     * Records a meeting with [targetId] and reports whether it had already happened.
     *
     * The two halves are one call on purpose: every caller that asks is also meeting the target,
     * and a check that did not record would let the same pair count again on the next frame -
     * which is the whole failure this component exists to prevent.
     */
    fun meet(targetId: EntityId): Boolean = !hitIds.add(targetId)

    /**
     * Spends one pierce, if there is one.
     *
     * @return true when the projectile goes through, false when it has been stopped.
     */
    fun spend(): Boolean {
        if (remaining <= 0) return false
        remaining--
        return true
    }
}

/**
 * Reflects an entity off the edges of the frame instead of letting it leave, [remaining] times.
 *
 * Tracked by [BounceSystem], which has to run after the movement that carries the entity into the
 * edge and before the culling that would otherwise remove it there.
 */
data class BounceComponent(var remaining: Int) : Component

data class LifetimeComponent(val removeIfOutOfBounds: Boolean = true) : Component

// Specialized components for behavior

/**
 * Marks an entity as steered by the player, and holds the drag [PlayerInputSystem] is in the
 * middle of.
 *
 * The drag lives here rather than in the system so that each steered entity owns its own, and so
 * that it dies with the entity instead of outliving it in a system that is reused across runs.
 *
 * @param activePointer pointer id currently steering this entity, or [NO_POINTER].
 * @param dragging true once the entity is pinned to that pointer and follows it one to one; false
 *   while it is still flying toward a touch that landed away from it.
 * @param grabOffsetX/grabOffsetY where the entity's center sits relative to the pointer, fixed at
 *   the moment of the grab so the sprite does not jump under the fingertip.
 * @param targetX/targetY last reported position of [activePointer], in framebuffer pixels. Held
 *   across updates because touch events are consumed once per frame while the world may tick
 *   several times.
 * @param pointerHeld whether [activePointer] was physically down when it claimed the entity. Only
 *   such a pointer can be found to have lifted; a desktop mouse steers by hovering, with nothing
 *   held, and must not be let go just because no button is pressed.
 */
data class PlayerControlComponent(
    var hitCooldown: Float = 0f,
    var activePointer: Int = NO_POINTER,
    var dragging: Boolean = false,
    var grabOffsetX: Float = 0f,
    var grabOffsetY: Float = 0f,
    var targetX: Float = 0f,
    var targetY: Float = 0f,
    var pointerHeld: Boolean = false,
) : Component {
    companion object {
        const val NO_POINTER = -1
    }
}

enum class EnemyMovementType { SINE, ZIGZAG, SCOUT, BOSS }

/**
 * @param holdX for [EnemyMovementType.BOSS]: the x it closes to and then holds at. A boss that
 *   kept advancing would either pin the player against the left edge or sail off it, so it takes
 *   up a station instead and weaves there.
 */
data class EnemyBehaviorComponent(
    val type: EnemyMovementType,
    val initialY: Float,
    var elapsedTime: Float = 0f,
    var verticalDirection: Float = 1f,
    var nextDirectionChange: Float = 0f,
    val holdX: Float = 0f,
) : Component

/**
 * One segment of the wake an entity leaves behind it, drawn as a plain block that thins and fades
 * as it ages. [TrailSystem] reaps it once its time is up.
 *
 * How far the wake reaches is [duration] against how fast the segment drifts, not a segment count:
 * segments are shed on a cadence, so a longer life is a longer trail.
 *
 * @param color the segment at full strength. Its alpha is scaled down as the segment ages.
 * @param duration how long the segment lives, in seconds.
 * @param minScale the fraction of its size a segment is down to at the very end, as 0..1. It
 *   shrinks about its own center, so the wake tapers to a thread rather than stopping at full
 *   width.
 */
data class TrailComponent(
    var color: Int,
    val duration: Float = 0.5f,
    val minScale: Float = 0.15f,
    var elapsed: Float = 0f,
) : Component

/**
 * Sheds a trail segment every [interval] seconds for as long as the entity is alive, tracked by
 * [TrailSystem].
 */
data class TrailEmitterComponent(
    val interval: Float,
    var timeSinceLastSegment: Float = 0f,
) : Component

data class BackgroundComponent(val isLooping: Boolean = true) : Component

data class ZIndexComponent(val zIndex: Int = 0) : Component

/**
 * Fires on a fixed cadence, tracked by [WeaponSystem].
 *
 * @param interval seconds between shots. Mutable so a weapon can be made faster mid-run; the
 *   elapsed time is kept across the change, so a shortened interval can fire immediately rather
 *   than making the player wait out the old one first.
 */
data class WeaponComponent(
    var interval: Float,
    var timeSinceLastShot: Float = 0f
) : Component

/**
 * The charge an entity is carrying, drawn around it as a halo with sparks and lightning in it.
 *
 * Two dials rather than one, because the two things it says are different things. [intensity] is
 * continuous and rises smoothly, so the halo swells under the player without ever announcing
 * itself. [tier] is a whole number that changes rarely, and each step adds sparks and arcs - a
 * threshold crossed is meant to land as an event, the way a wave turning over does.
 *
 * [phase] is the only mutable state, advanced by [AuraSystem]. Everything the effect draws is a
 * pure function of it, so a halo costs one float of storage rather than a particle pool - and two
 * systems can draw the same aura, in front of the entity and behind it, from the same clock.
 *
 * @param surge set to 1 when a tier is crossed and decayed back to 0, for the flare that marks it.
 */
data class AuraComponent(
    var intensity: Float = 0f,
    var tier: Int = 0,
    var phase: Float = 0f,
    var surge: Float = 0f,
) : Component

/**
 * A brief bloom of light over an entity that has just been hit, aged by [HitFlashSystem] and
 * drawn by [RenderSystem].
 *
 * Added when the hit lands, then burned down to nothing by [HitFlashSystem] and left there. It is
 * not taken off again: see that system for why a spent flash is clamped rather than removed.
 *
 * @param duration how long the flash lasts, in seconds. Short - it has to read as the moment of
 *   impact, and a flash that outlives the shot that caused it reads as damage being taken
 *   continuously.
 * @param color what the entity is lit up with. Its alpha is the flash at full strength, faded out
 *   across [duration].
 * @param remaining what is left, counted down. Re-arming this rather than adding a second
 *   component is what makes rapid fire on one target look like repeated hits instead of one long
 *   glow.
 */
data class HitFlashComponent(
    val duration: Float,
    val color: Int,
    var remaining: Float = duration,
) : Component {
    /** The flash at this instant, as 0..1. Full on the frame it lands, nothing when it is spent. */
    val strength: Float
        get() = if (duration <= 0f) 0f else (remaining / duration).coerceIn(0f, 1f)
}

/**
 * Which colorway of the shared projectile sheet this entity's shots are drawn from.
 *
 * Carried by the *shooter*, not by the shot, and read when the shot is spawned. That is the only
 * place it can live: a projectile outlives the frame it was fired on and routinely outlives the
 * thing that fired it, so a shot that had to ask its parent what color to be would be asking a
 * recycled id by the time it mattered.
 *
 * An entity without one falls back to whatever default the game spawns with, which keeps this
 * opt-in - most things in a world never fire at all.
 */
data class ProjectileStyleComponent(val variant: Int) : Component

/**
 * What is happening to something that has just been killed: it tumbles, it falls, and it comes
 * apart as it goes.
 *
 * Carried only while an entity is dying, which is what lets [DeathSystem] own the motion outright.
 * Before this the dead player was given a flat downward drift by [PlayerInputSystem] - input code
 * deciding how a corpse moves - and it slid off the bottom of the frame at a constant speed, still
 * beating its wings.
 *
 * @param gravity added to the entity's downward velocity every second. Velocities here are per
 *   tick, so this is "pixels per tick, per second" - a rate of change of a rate.
 * @param terminalVelocity the fastest it may fall, so a long drop does not end in a blur.
 * @param spinDegreesPerSecond how fast it turns as it goes. Purely cosmetic, like every rotation
 *   in this engine: the collision box stays upright.
 * @param puffInterval seconds between the blasts thrown off on the way down, or zero for none.
 */
data class DeathThroesComponent(
    val gravity: Float,
    val terminalVelocity: Float,
    val spinDegreesPerSecond: Float,
    val puffInterval: Float,
    var elapsed: Float = 0f,
    var timeSincePuff: Float = 0f,
) : Component
