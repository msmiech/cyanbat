package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2

/** Where an entity is: its axis-aligned box in frame pixels, which collisions are read from. */
data class TransformComponent(var rect: Rect) : Component

/** How far [MovementSystem] moves the entity each tick, in frame pixels. */
data class VelocityComponent(var velocity: Vector2) : Component

/**
 * What the entity is drawn as: a region of a sprite sheet, drawn by [RenderSystem] into its
 * [TransformComponent]'s box.
 *
 * @param baseSrcX x offset of the entity's frame strip within the sprite sheet. Animation frames
 *   are addressed relative to it, so several strips can share one sheet.
 * @param scale how many framebuffer pixels one source pixel covers, so the same artwork can be
 *   drawn large, as for a boss. Keep it in step with the entity's [TransformComponent].
 * @param rotationDegrees how far the artwork is turned clockwise about the sprite's center when
 *   drawn. Purely cosmetic: the [TransformComponent] stays the upright box collisions use, because
 *   a rotated hitbox would make a near miss depend on an angle the player cannot judge.
 *   [FacingSystem] keeps it pointing where an entity is going.
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
 * Turns an entity's sprite to point along its velocity, every frame.
 *
 * A marker: the angle lives on the [SpriteComponent] and [FacingSystem] does the work. Opt-in,
 * because most art has an up, and spinning the bat or an enemy to match a dodge would look like a
 * glitch. It suits projectiles, and the few hostiles whose flight is an arc, like a leap out of the
 * sand that has to go up nose first and come down the same way.
 *
 * @param artworkDegrees which way the unturned artwork points: 0 for a shot, drawn pointing right,
 *   and 180 for a hostile, drawn facing left. The sprite is turned by the heading minus this, so
 *   left-facing artwork flies left the right way up.
 */
class FacesVelocityComponent(val artworkDegrees: Float = 0f) : Component

/**
 * A second picture of the same sprite, from another row of its sheet, drawn over the first at
 * [alpha]. A sheet that stacks one drawing in several palettes, a row each, crossfades between them
 * this way: [SpriteComponent.srcY] picks the row underneath, and this the row fading in. Drawn by
 * [RenderSystem] right over its sprite.
 *
 * Only for sprites drawn upright at their own size, as all the scenery using it is; a turned or
 * magnified sprite ignores it.
 *
 * @param srcY where the second picture's row starts on the sheet.
 * @param alpha how far it has come in, as 0..1. At zero nothing extra is drawn.
 */
data class CrossfadeComponent(var srcY: Int = 0, var alpha: Float = 0f) : Component

/**
 * Steps a [SpriteComponent] through a strip of [frameCount] frames, one every [interval] seconds,
 * driven by [AnimationSystem].
 */
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

/**
 * Makes an entity collide, as [CollisionSystem] decides by its [group].
 *
 * @param tolerance how far the hit box is shrunk inside the entity's box on every side, in frame
 *   pixels, so a graze past a sprite's transparent corners is not a hit.
 */
data class CollisionComponent(
    val tolerance: Float = 0f,
    val group: CollisionGroup = CollisionGroup.OTHER
) : Component

/**
 * Which side something is on, which [CollisionSystem] reads to decide what can meet what.
 *
 * [PLAYER_CONTACT] is the player's weapons that hurt enemies by touch and are not
 * spent doing so. It meets enemies and nothing else, so it never needs telling
 * apart from a shot once a pair is reported.
 */
enum class CollisionGroup {
    PLAYER, ENEMY, PLAYER_PROJECTILE, ENEMY_PROJECTILE, OBSTACLE, OTHER, PLAYER_CONTACT
}

/**
 * How much damage an entity can still take.
 *
 * @param hitPoints what remains. The caller applying damage clears [alive] when it reaches zero,
 *   because what dying means differs from entity to entity.
 * @param maxHitPoints the full amount, so a [HealthBarComponent] can draw a fraction. Mutable
 *   because an entity can be made hardier mid-run, as when the player picks up more health.
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
 * Damage rides on the entity dealing it rather than in a global constant, so a late wave can hit
 * harder than an early one without touching anything already in flight: the spawner decides, and
 * the collision handler reads it off. Without one, the game applies its default.
 *
 * @param isCritical whether this is a critical hit. Kept with the amount because they are one fact:
 *   [amount] is already raised, and this lets whatever reports the hit say why. A piercing
 *   projectile carrying it crits everything it goes on to hit.
 */
data class DamageComponent(val amount: Int, val isCritical: Boolean = false) : Component

/**
 * Draws a bar of the entity's remaining [HealthComponent.fraction] just below it.
 *
 * Opt-in per entity, so a screen full of enemies stays uncluttered while the player still sees
 * their own health.
 *
 * @param height bar thickness, in framebuffer pixels.
 * @param offsetY gap between the bottom of the entity and the top of the bar.
 * @param fullColor the filled part, drawn from the left edge rightwards.
 * @param emptyColor the spent part, and so the whole bar once health runs out.
 * @param pinnedTo a fixed place on screen to draw it instead, for a boss that spends part of its
 *   fight off the frame, whose progress must stay visible. [offsetY] and [height] are ignored for a
 *   pinned bar.
 */
data class HealthBarComponent(
    val height: Float = 3f,
    val offsetY: Float = 2f,
    val fullColor: Int = EngineColors.RED,
    val emptyColor: Int = EngineColors.BLACK,
    val pinnedTo: Rect? = null,
) : Component

/**
 * Short-lived text that drifts along its [VelocityComponent] and fades out, such as damage numbers.
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
 *   pierce and lands one hit. Ids are recycled, so a long-lived projectile could mistake a new
 *   entity for one it passed; a shot crosses the screen in well under a second, and the cost of
 *   the mistake is a miss.
 */
data class PierceComponent(
    var remaining: Int,
    val hitIds: MutableSet<EntityId> = mutableSetOf(),
) : Component {
    /**
     * Records a meeting with [targetId] and reports whether it had already happened.
     *
     * One call on purpose: every caller that asks is also meeting the target, and a check that did
     * not record would let the same pair count again next frame.
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
 * Tracked by [BounceSystem], which must run after the movement that carries the entity into the
 * edge and before the culling that would remove it there.
 */
data class BounceComponent(var remaining: Int) : Component

/**
 * Lets [LifetimeSystem] remove the entity once it has left the frame (unless [removeIfOutOfBounds]
 * is off) or once its non-looping animation has finished.
 */
data class LifetimeComponent(val removeIfOutOfBounds: Boolean = true) : Component

// Specialized components for behavior

/**
 * Marks an entity as steered by the player, and holds the drag [PlayerInputSystem] is in the
 * middle of.
 *
 * The drag lives here rather than in the system so each steered entity owns its own, and it dies
 * with the entity instead of outliving it in a system reused across runs.
 *
 * @param hitCooldown seconds left before the entity can be hurt again.
 * @param activePointer pointer id currently steering this entity, or [NO_POINTER].
 * @param dragging true once the entity is pinned to that pointer and follows it one to one; false
 *   while it is still flying toward a touch that landed away from it.
 * @param grabOffsetX where the entity's center sits relative to the pointer, fixed at the moment of
 *   the grab so the sprite does not jump under the fingertip.
 * @param grabOffsetY as [grabOffsetX].
 * @param targetX last reported position of [activePointer], in framebuffer pixels.
 *   Held across updates because touch events are consumed once per frame while the
 *   world may tick several times.
 * @param targetY as [targetX].
 * @param pointerHeld whether [activePointer] was physically down when it claimed the entity. Only
 *   such a pointer can lift; a desktop mouse steers by hovering and must not be released just
 *   because no button is pressed.
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

/** How an enemy flies, run by [EnemyBehaviorSystem]. */
enum class EnemyMovementType {
    /** Crosses the screen easing along a sine wave around its lane. */
    SINE,

    /** Crosses the screen, reversing its vertical direction at a fixed interval. */
    ZIGZAG,

    /** Crosses the screen nearly straight, with a slight vertical bob. */
    SCOUT,

    /** A boss that closes to [EnemyBehaviorComponent.holdX] and weaves there. */
    BOSS,

    /**
     * One member of a swarm. A flock is spawned on one tick with one
     * [EnemyBehaviorComponent.initialY], so its members share a flight path; each buzzes around its
     * own place by its [EnemyBehaviorComponent.offsetY] and [EnemyBehaviorComponent.phase]. No
     * member needs to know where the others are.
     */
    SWARM,

    /** Lurches forward in surges and all but stops between them, like something heavy flying. */
    SURGE,

    /**
     * Flies in to [EnemyBehaviorComponent.holdX], hangs there drifting toward the player's lane
     * for a while, then leaves. The one that stops to shoot.
     */
    HOVER,

    /**
     * Cruises in to [EnemyBehaviorComponent.holdX], pauses for a moment as a tell, then dives in a
     * straight line at where the player was at that instant. Aimed once and never steered, so a
     * player who saw it coming can always sidestep it.
     */
    DIVE,

    /**
     * One member of a formation. Like [SWARM], members share a spawn tick and so a clock, and each
     * flies the same path from its own starting point, which holds the shape together without any
     * member steering by another.
     */
    FORMATION,

    /** A boss that holds station by tracing a figure eight around [EnemyBehaviorComponent.holdX]. */
    BOSS_FIGURE_EIGHT,

    /**
     * Cruises along [EnemyBehaviorComponent.initialY], just under the bottom edge with only its
     * back showing, until it reaches [EnemyBehaviorComponent.holdX], then leaps. The leap is aimed
     * once so its apex is at the player's height at that instant, then falls under gravity and
     * exits through the bottom. The cruise is the tell, and an unsteered leap can always be dodged.
     * One cruising right, in from behind, leaps once it has come as far right as its station.
     */
    LEAP,

    /**
     * Flies in to [EnemyBehaviorComponent.holdX], makes one full loop back through where it
     * started, then leaves at speed. The loop is hard to read; the straight stretches either side
     * are where it can be lined up and shot.
     */
    LOOP,

    /**
     * Glides in a straight line at [EnemyBehaviorComponent.baseSpeedX] per tick, easing in over the
     * last stretch, until its box's corner is at [EnemyBehaviorComponent.holdX],
     * [EnemyBehaviorComponent.initialY], then holds still. How a boss moves between stations; its
     * brain hands it the next one.
     */
    GLIDE,
}

/**
 * An enemy's flight pattern and its state, run by [EnemyBehaviorSystem].
 *
 * @param initialY the lane the enemy flies around. Mutable because a hovering enemy drifts its
 *   lane toward the player's.
 * @param holdX for [EnemyMovementType.BOSS] and its kin, the x it closes to and holds at, since a
 *   boss that kept advancing would pin the player to the left edge or fly off it.
 *   [EnemyMovementType.HOVER] and [EnemyMovementType.DIVE] stop there to shoot or to dive.
 * @param baseSpeedX the enemy's own closing speed, for patterns that set their horizontal velocity
 *   every tick rather than keeping the one they were spawned with.
 * @param offsetY where in its swarm or formation this member flies, relative to [initialY].
 * @param phase a per-member offset into the pattern's cycle, so a swarm does not buzz in unison.
 * @param tempo how fast the pattern's clock runs against real time. Raising it speeds a boss up
 *   smoothly into a faster version of the same pattern.
 * @param state for patterns with stages (hovering, diving, a boss on station), the current stage;
 *   [stateTime] is how long it has been in it.
 */
data class EnemyBehaviorComponent(
    val type: EnemyMovementType,
    var initialY: Float,
    var elapsedTime: Float = 0f,
    var verticalDirection: Float = 1f,
    var nextDirectionChange: Float = 0f,
    val holdX: Float = 0f,
    val baseSpeedX: Float = 0f,
    val offsetY: Float = 0f,
    val phase: Float = 0f,
    var tempo: Float = 1f,
    var state: Int = 0,
    var stateTime: Float = 0f,
) : Component

/**
 * A bubble around an entity that absorbs hits before its health does, drawn and recharged by
 * [ShieldSystem].
 *
 * A bubble takes a hit whole, however little it has left: the hit that breaks it is spent doing so.
 * That makes a shield read as a separate layer rather than extra health in another color: the first
 * shot at a shielded enemy pops it, and the next one hurts.
 *
 * @param points what it has left. At zero it is down and draws only the burst of it breaking.
 * @param regenPerSecond how fast it rebuilds once it has gone [regenDelay] seconds untouched, or
 *   zero for a bubble that stays broken.
 * @param flash set to 1 on a hit and decayed by [ShieldSystem], for a flare on the bubble rather
 *   than on the entity inside, which was not hit.
 * @param popTime counted down from [ShieldSystem.POP_SECONDS] once the bubble breaks, for the ring
 *   it bursts in.
 */
data class ShieldComponent(
    var points: Int,
    var maxPoints: Int = points,
    val color: Int = EngineColors.SHIELD,
    val regenPerSecond: Float = 0f,
    val regenDelay: Float = 0f,
    var sinceHit: Float = 0f,
    var flash: Float = 0f,
    var popTime: Float = 0f,
    var regenCarry: Float = 0f,
) : Component {
    val isUp: Boolean get() = points > 0

    /** What is left, as 0..1. */
    val fraction: Float
        get() = if (maxPoints <= 0) 0f else (points.toFloat() / maxPoints).coerceIn(0f, 1f)

    /**
     * Takes a hit of [amount] on the bubble, if it is up.
     *
     * @return true when the bubble absorbed all of it, even if that broke the bubble; false when it
     *   was already down and the hit goes through.
     */
    fun absorb(amount: Int): Boolean {
        if (!isUp) return false
        points = (points - amount).coerceAtLeast(0)
        sinceHit = 0f
        flash = 1f
        if (points == 0) popTime = ShieldSystem.POP_SECONDS
        return true
    }

    /** Puts the bubble back up at [points], as a boss does when it changes phase. */
    fun raise(points: Int) {
        this.points = points
        maxPoints = points
        sinceHit = 0f
        flash = 1f
        popTime = 0f
    }
}

/**
 * One segment of the wake an entity leaves, drawn as a plain block that thins and fades as it
 * ages. [TrailSystem] removes it once its time is up.
 *
 * The wake's length comes from [duration] and drift speed, not a segment count: segments are shed
 * on a cadence, so a longer life is a longer trail.
 *
 * @param color the segment at full strength; its alpha is scaled down with age.
 * @param duration how long the segment lives, in seconds.
 * @param minScale the fraction of its size a segment shrinks to by the end, 0..1, about its own
 *   center, so the wake tapers to a thread.
 * @param coreColor a brighter band along the middle, half the segment's height, fading with it;
 *   transparent for none. Marks a wake that does damage.
 */
data class TrailComponent(
    var color: Int,
    val duration: Float = 0.5f,
    val minScale: Float = 0.15f,
    var elapsed: Float = 0f,
    val coreColor: Int = 0,
) : Component

/**
 * Sheds a trail segment every [interval] seconds for as long as the entity is alive, tracked by
 * [TrailSystem].
 */
data class TrailEmitterComponent(
    val interval: Float,
    var timeSinceLastSegment: Float = 0f,
) : Component

/** Marks a scrolling background strip, which the game's scrolling system wraps around. */
class BackgroundComponent : Component

/** Draw order for [RenderSystem]: lower [zIndex] is drawn first, and ties keep creation order. */
data class ZIndexComponent(val zIndex: Int = 0) : Component

/**
 * Fires on a fixed cadence, tracked by [WeaponSystem].
 *
 * @param interval seconds between shots. Mutable so a weapon can be made faster mid-run; the
 *   elapsed time is kept, so a shortened interval can fire at once instead of waiting out the old
 *   one.
 */
data class WeaponComponent(
    var interval: Float,
    var timeSinceLastShot: Float = 0f
) : Component

/**
 * The charge an entity carries, drawn around it as a halo with sparks and lightning.
 *
 * Two dials, because they say different things. [intensity] is continuous, so the halo swells
 * smoothly. [tier] is a whole number that changes rarely, each step adding sparks and arcs, so
 * crossing a threshold lands as an event.
 *
 * [phase], advanced by [AuraSystem], is the only animation state; everything drawn is a pure
 * function of it, so a halo costs one float rather than a particle pool, and the aura can be drawn
 * both behind and in front of the entity from the same clock.
 *
 * @param surge set to 1 when a tier is crossed and decayed back to 0, for the flare that marks it.
 * @param colors what it is drawn in. Gold unless the game says otherwise.
 */
data class AuraComponent(
    var intensity: Float = 0f,
    var tier: Int = 0,
    var phase: Float = 0f,
    var surge: Float = 0f,
    val colors: AuraColors = AuraColors.GOLD,
) : Component

/**
 * The colors [AuraSystem] draws an [AuraComponent] in.
 *
 * The halo's rings run from [rim] outside to [core] in the middle. The rim carries most of the hue;
 * the core is what the middle settles toward once the rings stack over the background, which is why
 * a core should be light (see [GOLD]).
 *
 * @param spark the embers rising through the halo, and the bright point where a bolt earths.
 * @param bolt the lightning crawling over it.
 */
data class AuraColors(val rim: Int, val core: Int, val spark: Int, val bolt: Int) {
    companion object {
        /**
         * Amber into a gold so pale it is nearly white.
         *
         * Bright because of the blend: half-opacity amber over the cave's dark blue averages to
         * khaki. A lighter color at enough alpha keeps the halo's middle near its own gold.
         */
        val GOLD = AuraColors(
            rim = 0xFFFFC83C.toInt(),
            core = 0xFFFFFCE4.toInt(),
            spark = 0xFFFFFBDC.toInt(),
            bolt = 0xFFFFFFF0.toInt(),
        )
    }
}

/**
 * A point light at the middle of the entity's box, lighting its surroundings where a
 * [LightingSystem] makes the stage dark. An [OccluderComponent] between the light and a pixel puts
 * that pixel in shadow.
 *
 * @param color the light's color at full strength. The frame is multiplied by the light reaching
 *   it, so a pale color lights everything, and a saturated one mostly what shares its hue.
 * @param radius how far it reaches, in frame pixels; it falls off to nothing there.
 * @param intensity how strongly it shines, 0..1. Mutable, for a light turned up or down.
 * @param fadeSeconds when above zero, the light dies away over this long from creation, bright at
 *   first and quickly dimmer, like a flash.
 * @param removeWhenFaded whether the entity is removed once the light has faded: true for an entity
 *   that is only the light, such as the flash where a shot struck.
 */
class LightComponent(
    var color: Int,
    var radius: Int,
    var intensity: Float = 1f,
    val fadeSeconds: Float = 0f,
    val removeWhenFaded: Boolean = false,
) : Component {
    /** How long it has been fading, advanced by [LightingSystem]. */
    var elapsed: Float = 0f

    /** How brightly it shines now: its [intensity], reduced by its fade. */
    val strength: Float
        get() {
            if (fadeSeconds <= 0f) return intensity
            val left = 1f - (elapsed / fadeSeconds).coerceIn(0f, 1f)
            return intensity * left * left
        }

    /** Whether its fade has run all the way out. */
    val faded: Boolean get() = fadeSeconds > 0f && elapsed >= fadeSeconds
}

/**
 * Blocks every [LightComponent] reaching it, casting a shadow away from each in the shape of its
 * current sprite frame's opaque pixels, so a creature's shadow beats its wings with it.
 *
 * Only what is behind it is darkened, never the occluder itself: it stays as lit as the light
 * reaching it, and casts its shadow from its far side.
 *
 * @param shine how strongly a light glints off its edge on the lit side, 0..1; see
 *   [at.smiech.engine.Gloss]. Zero for something matte. Only an upright sprite glints.
 */
class OccluderComponent(val shine: Float = 0f) : Component

/**
 * A brief bloom of light over an entity that has just been hit, aged by [HitFlashSystem] and drawn
 * by [RenderSystem].
 *
 * Added when the hit lands, then decayed to nothing and left in place; see [HitFlashSystem] for why
 * a spent flash is clamped rather than removed.
 *
 * @param duration how long the flash lasts, in seconds. Short, so it reads as the moment of impact
 *   rather than continuous damage.
 * @param color what the entity lights up with; its alpha is the flash at full strength, faded out
 *   across [duration].
 * @param remaining time left, counted down. Re-arming it makes rapid fire on one target look like
 *   repeated hits instead of one long glow.
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
 * A color laid over an entity's sprite, in the sprite's shape, for as long as it is set: a lasting
 * state, where a [HitFlashComponent] is the moment of a hit. Drawn by [RenderSystem] right over the
 * sprite, under any flash.
 *
 * Disarmed rather than removed, like a flash: a [color] with no alpha draws nothing.
 *
 * @param color what the sprite is washed with; its alpha is how strongly.
 */
class TintComponent(var color: Int = 0) : Component

/**
 * Draws an entity's sprite see-through, with only [coverage] of its pixels, the rest left out in an
 * even pattern ([at.smiech.engine.Dither]) so the art stays crisp. Drawn by [RenderSystem] in place
 * of the plain sprite.
 *
 * Disarmed rather than removed, like a tint: at a [coverage] of 1 the sprite is drawn whole. Only for
 * an upright sprite, as everything using it is; a turned one is drawn whole. A crossfade is not laid
 * over it, and a tint or flash is laid over it whole.
 *
 * @param coverage how much of the sprite shows, 0..1.
 */
class DitherComponent(var coverage: Float = 1f) : Component

/**
 * Shows how badly an entity is hurt by which row of its sheet it is drawn from, picked by
 * [WoundSystem] from its [HealthComponent].
 *
 * Such a sheet stacks one picture per state, top to bottom: unhurt first, then a
 * row per mark in [thresholds], each more battered. Rows, because [AnimationSystem]
 * walks [SpriteComponent.srcX] and never touches [SpriteComponent.srcY], so a wound
 * takes hold mid wingbeat without the beat losing its place.
 *
 * The row is read off the health every tick rather than latched by the hit that crossed a mark, so
 * healing restores the picture.
 *
 * @param rowHeight the height of one picture: the sheet's height over its rows.
 * @param thresholds the health fractions at or below which each wounded row takes over, from the
 *   first wound to the worst: row n + 1 is drawn once [HealthComponent.fraction] is down to
 *   thresholds[n].
 */
class WoundComponent(val rowHeight: Int, val thresholds: FloatArray) : Component {
    /** The row it is drawn from now, kept by [WoundSystem]: 0 unhurt, then one per mark crossed. */
    var row: Int = 0

    /** The row an entity at [fraction] of its health is drawn from: 0 unhurt, then one per mark. */
    fun rowFor(fraction: Float): Int {
        var row = 0
        while (row < thresholds.size && fraction <= thresholds[row]) row++
        return row
    }
}

/**
 * How fast an entity goes about what it does, as a fraction of its own pace: 1 is full pace, lower
 * is slower.
 *
 * A clock of its own rather than a speed. [motion] is the rate its time runs at for everything
 * about how it flies: distance per tick ([MovementSystem]), its pattern's clock
 * ([EnemyBehaviorSystem]) and its wingbeat ([AnimationSystem]). A slowed pattern is the same
 * pattern played slower: a weave keeps its width and a leap its height, which scaling only the
 * velocity would shrink. [fire] does the same for its weapon's cadence ([WeaponSystem]); its shots,
 * once fired, fly at their own speed.
 *
 * Opt-in: what slows an entity, and how much, is the game's call.
 */
data class PaceComponent(var motion: Float = 1f, var fire: Float = 1f) : Component

/**
 * Which colorway of the shared projectile sheet this entity's shots are drawn from.
 *
 * Carried by the shooter and read when the shot spawns: a projectile often outlives its shooter, so
 * a shot asking its parent later would be asking a recycled id. Without one, the game's default
 * colorway applies.
 */
data class ProjectileStyleComponent(val variant: Int) : Component

/**
 * A death animation in progress: the entity tumbles, falls and throws off blasts as it goes.
 *
 * Carried only while an entity is dying, so [DeathSystem] owns its motion outright.
 *
 * @param gravity added to the downward velocity every second. Velocities are per tick, so this is
 *   pixels per tick, per second.
 * @param terminalVelocity the fastest it may fall, so a long drop does not end in a blur.
 * @param spinDegreesPerSecond how fast it turns as it falls. Cosmetic, like every rotation here:
 *   the collision box stays upright.
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
