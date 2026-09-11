package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Pixmap
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
 */
data class SpriteComponent(
    val pixmap: Pixmap,
    val baseSrcX: Int = 0,
    var srcX: Int = baseSrcX,
    var srcY: Int = 0,
    var srcWidth: Int = pixmap.width,
    var srcHeight: Int = pixmap.height,
    var scale: Float = 1f,
) : Component

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
data class DamageComponent(val amount: Int) : Component

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
 *   while it is still flying towards a touch that landed away from it.
 * @param grabOffsetX/grabOffsetY where the entity's centre sits relative to the pointer, fixed at
 *   the moment of the grab so the sprite does not jump under the fingertip.
 * @param targetX/targetY last reported position of [activePointer], in framebuffer pixels. Held
 *   across updates because touch events are consumed once per frame while the world may tick
 *   several times.
 */
data class PlayerControlComponent(
    var hitCooldown: Float = 0f,
    var activePointer: Int = NO_POINTER,
    var dragging: Boolean = false,
    var grabOffsetX: Float = 0f,
    var grabOffsetY: Float = 0f,
    var targetX: Float = 0f,
    var targetY: Float = 0f,
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
 *   shrinks about its own centre, so the wake tapers to a thread rather than stopping at full
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
