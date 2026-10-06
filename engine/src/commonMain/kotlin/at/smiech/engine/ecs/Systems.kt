package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Moves entities by their velocity each tick, slowed by a [PaceComponent] where there is one. */
class MovementSystem : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>
    private lateinit var paces: ComponentMapper<PaceComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        velocities = world.mapper(VelocityComponent::class)
        paces = world.mapper(PaceComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(transforms, velocities) { id ->
            val transform = transforms.require(id)
            val velocity = velocities.require(id).velocity
            val pace = paces[id]?.motion ?: 1f

            transform.rect = transform.rect.offset(velocity.x * pace, velocity.y * pace)
        }
    }
}

/** How a [EnemyMovementType.BOSS] enters and then holds its ground, in framebuffer pixels per tick. */
private const val BOSS_APPROACH_SPEED = -1.1f

/**
 * Its weave on station: slow and wide enough that the player has to follow it, never so fast that a
 * thumb cannot track it.
 */
private const val BOSS_WEAVE_AMPLITUDE = 60f
private const val BOSS_WEAVE_FREQUENCY = 0.9f
private const val BOSS_WEAVE_TRACKING = 0.06f

/** A swarm's shared path: a slow, wide sway the whole flock follows. */
private const val SWARM_SWAY_AMPLITUDE = 42f
private const val SWARM_SWAY_FREQUENCY = 1.7f

/** Each member's buzz about its place in the flock: small, fast, and never in step with the rest. */
private const val SWARM_BUZZ_RADIUS = 6f
private const val SWARM_BUZZ_FREQUENCY = 7f
private const val SWARM_BUZZ_DRIFT = 0.45f
private const val SWARM_TRACKING = 0.2f

/** A surge: how fast the lurches come, and how much of its speed it keeps between them. */
private const val SURGE_FREQUENCY = 2.4f
private const val SURGE_FLOOR = 0.3f
private const val SURGE_GAIN = 1.4f

/**
 * A hover: how long it hangs on station, how quickly its lane drifts toward the player's (slowly,
 * so it tracks a player who sits still and loses one who keeps moving), and how it bobs there.
 */
private const val HOVER_SECONDS = 5f
private const val HOVER_LANE_DRIFT = 0.35f
private const val HOVER_BOB = 10f
private const val HOVER_LEAVE_FACTOR = 1.3f

/**
 * A dive: how long the tell lasts (it pauses and backs off a little, warning the player) and how
 * fast it comes once committed. Its closing speed never drops below [DIVE_MIN_CLOSING], so a player
 * who has slipped behind it cannot make it fly backwards.
 */
private const val DIVE_TELL_SECONDS = 0.45f
private const val DIVE_TELL_BACKOFF = 0.4f
private const val DIVE_SPEED = 3.4f
private const val DIVE_MIN_CLOSING = 1.2f

/** A formation's path: a wide sweep, with the whole shape surging forward and easing off in time. */
private const val FORMATION_AMPLITUDE = 36f
private const val FORMATION_FREQUENCY = 1.3f
private const val FORMATION_SURGE = 0.7f
private const val FORMATION_TRACKING = 0.15f

/** The figure eight a [EnemyMovementType.BOSS_FIGURE_EIGHT] traces on station, and how it chases it. */
private const val FIGURE_EIGHT_X = 36f
private const val FIGURE_EIGHT_Y = 64f
private const val FIGURE_EIGHT_FREQUENCY = 0.7f
private const val FIGURE_EIGHT_TRACKING = 0.06f

/**
 * A leap. Gravity is in pixels per tick, per second, like [DeathThroesComponent.gravity],
 * set so a leap from the sand to mid-frame and back takes about two and a half seconds,
 * long enough to see it coming down. The apex stays at least [LEAP_HIGHEST_TOP] below the
 * top of the frame, so hugging the ceiling is not a safe spot, and at least
 * [LEAP_LEAST_RISE] above the start, so a player near the ground still faces a leap
 * rather than a hop. With no player to aim at it rises [LEAP_BLIND_RISE].
 */
private const val LEAP_GRAVITY = 4.8f
private const val LEAP_HIGHEST_TOP = 48f
private const val LEAP_LEAST_RISE = 70f
private const val LEAP_BLIND_RISE = 150f
private const val LEAP_FORWARD_FACTOR = 1.25f

/**
 * A loop: how much faster than its cruise it flies the circle, and how long one turn takes. At the
 * desert's closing speeds that is a radius of about forty pixels: big enough to read as a loop,
 * small enough to stay on screen from any lane.
 */
private const val LOOP_SPEED_FACTOR = 1.6f
private const val LOOP_SECONDS = 1.8f
private const val LOOP_LEAVE_FACTOR = 1.5f

/**
 * A glide eases in over its last stretch: near its station it covers [GLIDE_EASING] of the
 * remaining distance per tick, but never less than [GLIDE_SLOWEST] of a pixel, so it settles rather
 * than creeping up on the last pixel forever.
 */
private const val GLIDE_EASING = 0.05f
private const val GLIDE_SLOWEST = 0.3f

/** How near its station a glide counts as arrived; its last step always lands well inside this. */
private const val GLIDE_ARRIVED = 0.01f

private const val PI_F = 3.1415927f
private const val TWO_PI_F = 6.2831855f

/** Stages of the patterns that have them; see [EnemyBehaviorComponent.state]. */
private const val STAGE_APPROACH = 0
private const val STAGE_HOLD = 1
private const val STAGE_COMMITTED = 2

/**
 * Runs enemy flight patterns; see [EnemyMovementType].
 *
 * Some patterns aim at the player, looked up once per update as the first living entity carrying a
 * [PlayerControlComponent]. With none (a test world, or a dead bat) they fly on as if nobody were
 * there.
 *
 * A [PaceComponent] slows an enemy's own clock: every timer here runs on its time, and
 * [MovementSystem] scales its velocity, so a slowed pattern is the same pattern played slower.
 */
class EnemyBehaviorSystem : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>
    private lateinit var behaviors: ComponentMapper<EnemyBehaviorComponent>
    private lateinit var players: ComponentMapper<PlayerControlComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>
    private lateinit var paces: ComponentMapper<PaceComponent>

    /** The player's center this update, or NaN when there is no living player to aim at. */
    private var targetX = Float.NaN
    private var targetY = Float.NaN

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        velocities = world.mapper(VelocityComponent::class)
        behaviors = world.mapper(EnemyBehaviorComponent::class)
        players = world.mapper(PlayerControlComponent::class)
        healths = world.mapper(HealthComponent::class)
        paces = world.mapper(PaceComponent::class)
    }

    private fun findTarget(world: World) {
        targetX = Float.NaN
        targetY = Float.NaN
        world.forEach(transforms, players) { id ->
            if (!targetX.isNaN()) return@forEach
            if (healths[id]?.alive == false) return@forEach
            val rect = transforms.require(id).rect
            targetX = rect.centerX
            targetY = rect.centerY
        }
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        findTarget(world)

        world.forEach(transforms, velocities, behaviors) { id ->
            val transform = transforms.require(id)
            val velocity = velocities.require(id)
            val behavior = behaviors.require(id)
            val pace = paces[id]?.motion ?: 1f

            behavior.elapsedTime += deltaTime * pace * behavior.tempo
            behavior.stateTime += deltaTime * pace

            when (behavior.type) {
                EnemyMovementType.SCOUT -> {
                    velocity.velocity =
                        velocity.velocity.copy(y = sin(behavior.elapsedTime * 2f) * 0.2f)
                }

                EnemyMovementType.SINE -> {
                    val amplitude = 80f
                    val frequency = 3f
                    val targetY =
                        behavior.initialY + sin(behavior.elapsedTime * frequency) * amplitude
                    val dy = (targetY - transform.rect.top) * 0.1f
                    velocity.velocity = velocity.velocity.copy(y = dy)
                }

                EnemyMovementType.ZIGZAG -> {
                    if (behavior.elapsedTime > behavior.nextDirectionChange) {
                        behavior.verticalDirection *= -1f
                        behavior.nextDirectionChange = behavior.elapsedTime + 0.8f
                    }
                    velocity.velocity =
                        velocity.velocity.copy(y = behavior.verticalDirection * 2.5f)
                }

                EnemyMovementType.BOSS -> {
                    // Closes from the edge it entered on, then stops dead at its station. Its x
                    // velocity is overwritten rather than decayed, so the entrance reads as one
                    // deliberate move instead of a drift.
                    val closing = transform.rect.left > behavior.holdX
                    val targetY = behavior.initialY +
                            sin(behavior.elapsedTime * BOSS_WEAVE_FREQUENCY) * BOSS_WEAVE_AMPLITUDE
                    velocity.velocity = Vector2(
                        x = if (closing) BOSS_APPROACH_SPEED else 0f,
                        // Chased rather than set outright, so the boss eases into the turns of its
                        // weave instead of snapping between them.
                        y = (targetY - transform.rect.top) * BOSS_WEAVE_TRACKING,
                    )
                }

                EnemyMovementType.SWARM -> swarm(transform, velocity, behavior)
                EnemyMovementType.SURGE -> surge(velocity, behavior)
                EnemyMovementType.HOVER -> hover(transform, velocity, behavior, pace)
                EnemyMovementType.DIVE -> dive(transform, velocity, behavior)
                EnemyMovementType.FORMATION -> formation(transform, velocity, behavior)
                EnemyMovementType.BOSS_FIGURE_EIGHT -> figureEight(transform, velocity, behavior)
                EnemyMovementType.LEAP -> leap(transform, velocity, behavior, deltaTime, pace)
                EnemyMovementType.LOOP -> loop(transform, velocity, behavior)
                EnemyMovementType.GLIDE -> glide(transform, velocity, behavior)
            }
        }
    }

    /**
     * Straight at the station, at full speed until the last stretch and easing in from there, so it
     * settles rather than stopping dead. Nothing else steers it on the way.
     */
    private fun glide(
        transform: TransformComponent,
        velocity: VelocityComponent,
        behavior: EnemyBehaviorComponent,
    ) {
        val rect = transform.rect
        val dx = behavior.holdX - rect.left
        val dy = behavior.initialY - rect.top
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < GLIDE_ARRIVED) {
            velocity.velocity = Vector2.Zero
            if (behavior.state == STAGE_APPROACH) enter(behavior, STAGE_HOLD)
            return
        }
        // Never more than what is left, so the last step lands on the station rather than past it.
        val speed = minOf(abs(behavior.baseSpeedX), distance * GLIDE_EASING)
            .coerceAtLeast(GLIDE_SLOWEST)
            .coerceAtMost(distance)
        velocity.velocity = Vector2(dx / distance * speed, dy / distance * speed)
    }

    /**
     * A slowed leaper is thrown as hard as ever and falls on its own time, so a slowed leap keeps
     * its height; only the climb and the fall take longer.
     *
     * Most leapers come in from the right and leap once they are as far left as their station. One
     * cruising right, in from behind the bat, leaps once it is as far right as its station.
     */
    private fun leap(
        transform: TransformComponent,
        velocity: VelocityComponent,
        behavior: EnemyBehaviorComponent,
        deltaTime: Float,
        pace: Float,
    ) {
        val rect = transform.rect
        if (behavior.state == STAGE_APPROACH) {
            val short =
                if (behavior.baseSpeedX > 0f) rect.left < behavior.holdX else rect.left > behavior.holdX
            if (short) {
                // Held to its lane rather than left to drift, so the back showing above the sand
                // stays the same height all the way in: that sliver is the only warning.
                velocity.velocity =
                    Vector2(behavior.baseSpeedX, (behavior.initialY - rect.top) * 0.2f)
                return
            }
            enter(behavior, STAGE_COMMITTED)
            velocity.velocity =
                Vector2(behavior.baseSpeedX * LEAP_FORWARD_FACTOR, launchSpeed(rect, deltaTime))
            return
        }
        // Committed: from here gravity alone shapes the arc.
        velocity.velocity =
            velocity.velocity.copy(y = velocity.velocity.y + LEAP_GRAVITY * deltaTime * pace)
    }

    /**
     * The upward speed that tops out with the enemy's middle at the player's, under [LEAP_GRAVITY]
     * applied once per tick. Computed for discrete ticks rather than a continuous throw, because
     * that is how it actually flies.
     */
    private fun launchSpeed(rect: Rect, deltaTime: Float): Float {
        val gravityPerTick = LEAP_GRAVITY * deltaTime
        val wanted = if (targetY.isNaN()) LEAP_BLIND_RISE else rect.centerY - targetY
        val rise = wanted.coerceIn(
            LEAP_LEAST_RISE,
            (rect.top - LEAP_HIGHEST_TOP).coerceAtLeast(LEAP_LEAST_RISE)
        )
        // A throw of v per tick, slowing by g per tick, climbs v + (v - g) + ..., which comes to
        // v²/2g + v/2. Solved for v.
        val speed =
            -gravityPerTick / 2f + sqrt(gravityPerTick * gravityPerTick / 4f + 2f * gravityPerTick * rise)
        return -speed
    }

    private fun loop(
        transform: TransformComponent,
        velocity: VelocityComponent,
        behavior: EnemyBehaviorComponent,
    ) {
        val rect = transform.rect
        when (behavior.state) {
            STAGE_APPROACH -> if (rect.left <= behavior.holdX) enter(behavior, STAGE_HOLD)
            STAGE_HOLD -> if (behavior.stateTime >= LOOP_SECONDS) enter(behavior, STAGE_COMMITTED)
        }

        velocity.velocity = when (behavior.state) {
            STAGE_APPROACH -> Vector2(
                behavior.baseSpeedX,
                sin(behavior.elapsedTime * 2.5f + behavior.phase) * 0.3f
            )

            STAGE_HOLD -> {
                // Heading round from due left, climbing first, so it leaves the loop on the line it
                // entered on.
                val heading = PI_F + TWO_PI_F * behavior.stateTime / LOOP_SECONDS
                val speed = -behavior.baseSpeedX * LOOP_SPEED_FACTOR
                Vector2(speed * cos(heading), speed * sin(heading))
            }

            else -> Vector2(behavior.baseSpeedX * LOOP_LEAVE_FACTOR, 0f)
        }
    }

    private fun swarm(
        transform: TransformComponent,
        velocity: VelocityComponent,
        behavior: EnemyBehaviorComponent,
    ) {
        val t = behavior.elapsedTime
        val buzz = t * SWARM_BUZZ_FREQUENCY + behavior.phase
        val targetY = behavior.initialY + behavior.offsetY +
                sin(t * SWARM_SWAY_FREQUENCY) * SWARM_SWAY_AMPLITUDE +
                sin(buzz) * SWARM_BUZZ_RADIUS
        velocity.velocity = Vector2(
            x = behavior.baseSpeedX + cos(buzz) * SWARM_BUZZ_DRIFT,
            y = (targetY - transform.rect.top) * SWARM_TRACKING,
        )
    }

    private fun surge(velocity: VelocityComponent, behavior: EnemyBehaviorComponent) {
        val t = behavior.elapsedTime * SURGE_FREQUENCY + behavior.phase
        val thrust = SURGE_FLOOR + SURGE_GAIN * sin(t).coerceAtLeast(0f)
        velocity.velocity = Vector2(
            x = behavior.baseSpeedX * thrust,
            // A heavy bob in time with the surges: it dips as it lunges.
            y = cos(t) * 0.35f,
        )
    }

    private fun hover(
        transform: TransformComponent,
        velocity: VelocityComponent,
        behavior: EnemyBehaviorComponent,
        pace: Float,
    ) {
        val rect = transform.rect
        when (behavior.state) {
            STAGE_APPROACH -> if (rect.left <= behavior.holdX) enter(behavior, STAGE_HOLD)
            STAGE_HOLD -> if (behavior.stateTime >= HOVER_SECONDS) enter(behavior, STAGE_COMMITTED)
        }

        if (behavior.state == STAGE_HOLD && !targetY.isNaN()) {
            // The lane creeps toward the player's, capped per tick, so a hoverer lines up on a
            // player who sits still and loses one who keeps moving.
            val wanted = targetY - rect.height / 2f
            behavior.initialY += (wanted - behavior.initialY)
                .coerceIn(-HOVER_LANE_DRIFT * pace, HOVER_LANE_DRIFT * pace)
        }

        val bobY = behavior.initialY + sin(behavior.elapsedTime * 2.2f + behavior.phase) * HOVER_BOB
        velocity.velocity = Vector2(
            x = when (behavior.state) {
                STAGE_APPROACH -> behavior.baseSpeedX
                STAGE_HOLD -> 0f
                else -> behavior.baseSpeedX * HOVER_LEAVE_FACTOR
            },
            y = (bobY - rect.top) * 0.08f,
        )
    }

    private fun dive(
        transform: TransformComponent,
        velocity: VelocityComponent,
        behavior: EnemyBehaviorComponent,
    ) {
        val rect = transform.rect
        // Checked first, so the tell starts on the very tick it reaches its station.
        if (behavior.state == STAGE_APPROACH && rect.left <= behavior.holdX) enter(
            behavior,
            STAGE_HOLD
        )

        when (behavior.state) {
            STAGE_APPROACH -> velocity.velocity = Vector2(
                behavior.baseSpeedX,
                sin(behavior.elapsedTime * 3f + behavior.phase) * 0.4f,
            )

            STAGE_HOLD -> {
                // The tell: it checks its swing and drifts back a touch before it commits.
                velocity.velocity = Vector2(DIVE_TELL_BACKOFF, 0f)
                if (behavior.stateTime >= DIVE_TELL_SECONDS) {
                    enter(behavior, STAGE_COMMITTED)
                    velocity.velocity = diveHeading(rect)
                }
            }

            // Committed: the heading was set once and is left alone. A dive that kept steering
            // would be a homing missile, which nothing can dodge.
        }
    }

    /** Straight at the player's center, at [DIVE_SPEED], never closing slower than [DIVE_MIN_CLOSING]. */
    private fun diveHeading(rect: Rect): Vector2 {
        if (targetX.isNaN()) return Vector2(-DIVE_SPEED, 0f)
        val dx = targetX - rect.centerX
        val dy = targetY - rect.centerY
        val length = sqrt(dx * dx + dy * dy)
        if (length < 1f) return Vector2(-DIVE_SPEED, 0f)
        val vx = (dx / length * DIVE_SPEED).coerceAtMost(-DIVE_MIN_CLOSING)
        val vy = dy / length * DIVE_SPEED
        return Vector2(vx, vy)
    }

    private fun formation(
        transform: TransformComponent,
        velocity: VelocityComponent,
        behavior: EnemyBehaviorComponent,
    ) {
        val t = behavior.elapsedTime * FORMATION_FREQUENCY
        val targetY = behavior.initialY + behavior.offsetY + sin(t) * FORMATION_AMPLITUDE
        velocity.velocity = Vector2(
            x = behavior.baseSpeedX + cos(t) * FORMATION_SURGE,
            y = (targetY - transform.rect.top) * FORMATION_TRACKING,
        )
    }

    private fun figureEight(
        transform: TransformComponent,
        velocity: VelocityComponent,
        behavior: EnemyBehaviorComponent,
    ) {
        val rect = transform.rect
        val t = behavior.elapsedTime * FIGURE_EIGHT_FREQUENCY
        if (behavior.state == STAGE_APPROACH) {
            if (rect.left <= behavior.holdX) {
                enter(behavior, STAGE_HOLD)
            } else {
                // Enters the way the cave's boss does, weaving as it closes.
                val weaveY =
                    behavior.initialY + sin(behavior.elapsedTime * BOSS_WEAVE_FREQUENCY) * BOSS_WEAVE_AMPLITUDE
                velocity.velocity =
                    Vector2(BOSS_APPROACH_SPEED, (weaveY - rect.top) * BOSS_WEAVE_TRACKING)
                return
            }
        }
        // Across at one frequency and up and down at twice it: a figure eight lying on its side.
        val targetX = behavior.holdX + sin(t) * FIGURE_EIGHT_X
        val targetY = behavior.initialY + sin(t * 2f) * FIGURE_EIGHT_Y
        velocity.velocity = Vector2(
            x = (targetX - rect.left) * FIGURE_EIGHT_TRACKING * behavior.tempo,
            y = (targetY - rect.top) * FIGURE_EIGHT_TRACKING * behavior.tempo,
        )
    }

    private fun enter(behavior: EnemyBehaviorComponent, state: Int) {
        behavior.state = state
        behavior.stateTime = 0f
    }
}

/**
 * Recharges [ShieldComponent]s and draws them as bubbles over whatever they protect.
 *
 * Add it after [RenderSystem], or the enemy would be drawn over its own bubble.
 *
 * The bubble is sized from the entity's box, so the same component suits a wasp and a boss.
 */
class ShieldSystem : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var shields: ComponentMapper<ShieldComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        shields = world.mapper(ShieldComponent::class)
        healths = world.mapper(HealthComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(shields) { id ->
            val shield = shields.require(id)
            shield.flash = (shield.flash - deltaTime / FLASH_SECONDS).coerceAtLeast(0f)
            shield.popTime = (shield.popTime - deltaTime).coerceAtLeast(0f)
            shield.sinceHit += deltaTime

            // Nothing dead recharges, and nothing without a recharge rate comes back.
            if (healths[id]?.alive == false) return@forEach
            if (shield.regenPerSecond <= 0f || shield.points >= shield.maxPoints) return@forEach
            if (shield.sinceHit < shield.regenDelay) return@forEach

            // Carried like the bat's regeneration, so a slow rate is not rounded away to nothing.
            shield.regenCarry += shield.regenPerSecond * deltaTime
            val whole = shield.regenCarry.toInt()
            if (whole > 0) {
                shield.regenCarry -= whole
                shield.points = (shield.points + whole).coerceAtMost(shield.maxPoints)
            }
        }
    }

    override fun draw(world: World, graphics: Graphics) {
        world.forEach(transforms, shields) { id ->
            val shield = shields.require(id)
            val rect = transforms.require(id).rect
            val radius = maxOf(rect.width, rect.height) / 2f + PADDING

            if (shield.isUp) {
                val size = (radius * 2f).roundToInt()
                val left = (rect.centerX - radius).roundToInt()
                val top = (rect.centerY - radius).roundToInt()
                // Faint inside, so the enemy stays readable; the rim shows the strength, fading as
                // the bubble wears down; a hit flares both.
                graphics.drawOval(
                    left, top, size, size,
                    EngineColors.withAlpha(
                        shield.color,
                        FILL_ALPHA + FLASH_FILL_ALPHA * shield.flash
                    ),
                )
                graphics.drawOvalOutline(
                    left, top, size, size,
                    EngineColors.withAlpha(
                        shield.color,
                        (RIM_MIN_ALPHA + RIM_RANGE_ALPHA * shield.fraction + shield.flash).coerceAtMost(
                            1f
                        ),
                    ),
                )
                // A glint on the upper left, the side the game's light comes from.
                val glint = (radius * 0.5f).roundToInt()
                graphics.drawOvalOutline(
                    left + glint / 2, top + glint / 2, glint, glint,
                    EngineColors.withAlpha(EngineColors.WHITE, GLINT_ALPHA),
                )
            } else if (shield.popTime > 0f) {
                // A ring that grows and fades, so breaking the bubble registers as an event.
                val progress = 1f - shield.popTime / POP_SECONDS
                val grown = radius * (1f + progress * POP_GROWTH)
                val size = (grown * 2f).roundToInt()
                graphics.drawOvalOutline(
                    (rect.centerX - grown).roundToInt(), (rect.centerY - grown).roundToInt(),
                    size, size,
                    EngineColors.withAlpha(shield.color, 1f - progress),
                )
            }
        }
    }

    companion object {
        /** How long a broken bubble's ring takes to fade. */
        const val POP_SECONDS = 0.25f

        private const val FLASH_SECONDS = 0.15f
        private const val PADDING = 3f
        private const val FILL_ALPHA = 0.14f
        private const val FLASH_FILL_ALPHA = 0.3f
        private const val RIM_MIN_ALPHA = 0.35f
        private const val RIM_RANGE_ALPHA = 0.5f
        private const val GLINT_ALPHA = 0.5f
        private const val POP_GROWTH = 0.6f
    }
}

/**
 * Turns the sprite of every [FacesVelocityComponent] entity to point where it is going.
 *
 * Run it after everything that can change a velocity (movement, bounce, steering), so each frame is
 * drawn at the direction it actually travels: a shot coming off a wall turns on the frame it
 * reverses, not a frame late. Which way the artwork itself points comes from
 * [FacesVelocityComponent.artworkDegrees].
 */
class FacingSystem : GameSystem() {
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>
    private lateinit var facings: ComponentMapper<FacesVelocityComponent>

    override fun onAttach(world: World) {
        sprites = world.mapper(SpriteComponent::class)
        velocities = world.mapper(VelocityComponent::class)
        facings = world.mapper(FacesVelocityComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(sprites, velocities, facings) { id ->
            val velocity = velocities.require(id).velocity
            // At a standstill there is no direction to face, so it keeps the last one rather than
            // snapping to zero, which for a bullet shape would be a visible flick.
            if (velocity.x == 0f && velocity.y == 0f) return@forEach

            val heading = atan2(velocity.y, velocity.x) * DEGREES_PER_RADIAN
            sprites.require(id).rotationDegrees =
                normalized(heading - facings.require(id).artworkDegrees)
        }
    }

    /**
     * Into -180..180, so left-facing artwork flying left sits at zero like an unturned sprite
     * rather than at a full turn.
     */
    private fun normalized(degrees: Float): Float {
        var turned = degrees % 360f
        if (turned > 180f) turned -= 360f
        if (turned <= -180f) turned += 360f
        return turned
    }

    private companion object {
        const val DEGREES_PER_RADIAN = 57.29578f
    }
}

/**
 * Steps [AnimationComponent]s and points their sprites at the current frame, on the entity's own
 * time where a [PaceComponent] slows it.
 */
class AnimationSystem : GameSystem() {
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var animations: ComponentMapper<AnimationComponent>
    private lateinit var paces: ComponentMapper<PaceComponent>

    override fun onAttach(world: World) {
        sprites = world.mapper(SpriteComponent::class)
        animations = world.mapper(AnimationComponent::class)
        paces = world.mapper(PaceComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(sprites, animations) { id ->
            val anim = animations.require(id)

            if (!anim.isFinished) {
                anim.currentTime += deltaTime * (paces[id]?.motion ?: 1f)
                if (anim.currentTime > anim.interval) {
                    if (anim.isLooping) {
                        anim.currentFrame = (anim.currentFrame + 1) % anim.frameCount
                    } else {
                        if (anim.currentFrame < anim.frameCount - 1) {
                            anim.currentFrame++
                        } else {
                            anim.isFinished = true
                        }
                    }
                    anim.currentTime -= anim.interval
                }

                val sprite = sprites.require(id)
                sprite.srcX = sprite.baseSrcX + anim.currentFrame * anim.frameWidth
            }
        }
    }
}

/**
 * Draws every entity with a [SpriteComponent], in [ZIndexComponent] order, dithered where it is
 * see-through, with its crossfade, tint and hit flash.
 *
 * @param layers the z indices this pass draws, all by default. A world that draws something between
 *   layers adds one pass per range with that in between; two passes over adjoining ranges draw
 *   exactly what one pass over both would, in the same order.
 */
class RenderSystem(private val layers: IntRange = Int.MIN_VALUE..Int.MAX_VALUE) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var zIndices: ComponentMapper<ZIndexComponent>
    private lateinit var flashes: ComponentMapper<HitFlashComponent>
    private lateinit var tints: ComponentMapper<TintComponent>
    private lateinit var crossfades: ComponentMapper<CrossfadeComponent>
    private lateinit var dithers: ComponentMapper<DitherComponent>

    /**
     * Draw order, rebuilt every frame into the same buffer.
     *
     * Each entry packs the z index above the entity's position in the query, so one sort of a
     * primitive array does the whole job: the z index drives the ordering and the position breaks
     * ties, which keeps entities on the same layer in creation order. Sorting ids instead would
     * shuffle a layer as ids get recycled.
     */
    private var order = LongArray(64)
    private var ids = IntArray(64)

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        sprites = world.mapper(SpriteComponent::class)
        zIndices = world.mapper(ZIndexComponent::class)
        flashes = world.mapper(HitFlashComponent::class)
        tints = world.mapper(TintComponent::class)
        crossfades = world.mapper(CrossfadeComponent::class)
        dithers = world.mapper(DitherComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        // Drawing only.
    }

    override fun draw(world: World, graphics: Graphics) {
        var count = 0
        world.forEach(transforms, sprites) { id ->
            val zIndex = zIndices[id]?.zIndex ?: 0
            if (zIndex !in layers) return@forEach
            if (count == ids.size) {
                ids = ids.copyOf(count * 2)
                order = order.copyOf(count * 2)
            }
            ids[count] = id
            order[count] = (zIndex.toLong() shl 32) or count.toLong()
            count++
        }

        // Sorting only the filled prefix keeps stale entries from earlier, busier frames out.
        order.sort(0, count)

        for (i in 0 until count) {
            val id = ids[(order[i] and 0xFFFFFFFFL).toInt()]
            val transform = transforms.require(id)
            val sprite = sprites.require(id)

            val left = transform.rect.left.toInt()
            val top = transform.rect.top.toInt()
            val dstWidth = (sprite.srcWidth * sprite.scale).roundToInt()
            val dstHeight = (sprite.srcHeight * sprite.scale).roundToInt()
            val coverage = dithers[id]?.coverage ?: 1f

            // Four paths, narrowest first. Most sprites are drawn upright at their own size, and
            // that case must not pay for a transform it does not use.
            when {
                sprite.rotationDegrees != 0f -> graphics.drawPixmap(
                    sprite.pixmap, left, top,
                    sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                    dstWidth, dstHeight, sprite.rotationDegrees,
                )

                coverage < 1f -> graphics.drawPixmapDithered(
                    sprite.pixmap, left, top,
                    sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                    dstWidth, dstHeight, coverage,
                )

                sprite.scale != 1f -> graphics.drawPixmap(
                    sprite.pixmap, left, top,
                    sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                    dstWidth, dstHeight,
                )

                else -> {
                    graphics.drawPixmap(
                        sprite.pixmap, left, top,
                        sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                    )
                    // Right over its own sprite, for the same reason as the flash below: anything
                    // later in the order has to cover both pictures.
                    val crossfade = crossfades[id]
                    if (crossfade != null && crossfade.alpha > 0f) {
                        graphics.drawPixmapFaded(
                            sprite.pixmap, left, top,
                            sprite.srcX, crossfade.srcY, sprite.srcWidth, sprite.srcHeight,
                            crossfade.alpha,
                        )
                    }
                }
            }

            // Right over the frame just drawn, while this sprite is still on top; that is why the
            // tint and flash are drawn here rather than in a system of their own (see
            // HitFlashSystem). The flash goes over the tint, so a hit on something tinted shows.
            val tint = tints[id]?.color ?: 0
            if (tint ushr 24 != 0) drawSilhouette(
                graphics,
                sprite,
                left,
                top,
                dstWidth,
                dstHeight,
                tint
            )

            val flash = flashes[id] ?: continue
            val strength = flash.strength
            if (strength <= 0f) continue
            drawSilhouette(
                graphics, sprite, left, top, dstWidth, dstHeight,
                EngineColors.scaleAlpha(flash.color, strength),
            )
        }
    }

    /**
     * [sprite]'s current frame filled with [color], where it was just drawn, and turned with the
     * sprite, since an upright flash over a turned boss segment would light up the wrong shape.
     */
    private fun drawSilhouette(
        graphics: Graphics,
        sprite: SpriteComponent,
        left: Int,
        top: Int,
        dstWidth: Int,
        dstHeight: Int,
        color: Int,
    ) {
        if (sprite.rotationDegrees != 0f) {
            graphics.drawPixmapSilhouette(
                sprite.pixmap, left, top,
                sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                dstWidth, dstHeight, color, sprite.rotationDegrees,
            )
        } else {
            graphics.drawPixmapSilhouette(
                sprite.pixmap, left, top,
                sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                dstWidth, dstHeight, color,
            )
        }
    }
}

/**
 * Fires weapons whose cadence has come round, more slowly for an entity whose [PaceComponent] has
 * slowed its fire.
 *
 * Spawning is delegated to [onFire] because what a projectile looks like is the game's concern, the
 * same split [CollisionSystem] uses for its handler.
 */
class WeaponSystem(private val onFire: (EntityId) -> Unit) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var weapons: ComponentMapper<WeaponComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>
    private lateinit var paces: ComponentMapper<PaceComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        weapons = world.mapper(WeaponComponent::class)
        healths = world.mapper(HealthComponent::class)
        paces = world.mapper(PaceComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(transforms, weapons) { id ->
            // A dead entity keeps its weapon but stops using it.
            val health = healths[id]
            if (health == null || health.alive) {
                val weapon = weapons.require(id)
                // Slowed by running the cadence's clock slower rather than stretching the interval,
                // which the game changes for its own reasons.
                weapon.timeSinceLastShot += deltaTime * (paces[id]?.fire ?: 1f)
                if (weapon.timeSinceLastShot >= weapon.interval) {
                    // Subtract rather than zero, so a long frame does not lose the remainder and
                    // drift the cadence.
                    weapon.timeSinceLastShot -= weapon.interval
                    onFire(id)
                }
            }
        }
    }
}

/**
 * Reflects [BounceComponent] entities off the edges of the frame, spending a bounce each time.
 *
 * Must run between [MovementSystem], which carries an entity into an edge, and [LifetimeSystem],
 * which would remove it there.
 *
 * Only the leading edge counts: an entity is reflected off a wall it is travelling into, never one
 * it is moving away from. Otherwise something still overlapping an edge would flip back and forth
 * and burn all its bounces in a few frames.
 *
 * @param worldWidth the framebuffer's width, which is where the edges are.
 * @param worldHeight the framebuffer's height.
 */
class BounceSystem(
    private val worldWidth: Int,
    private val worldHeight: Int,
) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>
    private lateinit var bounces: ComponentMapper<BounceComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        velocities = world.mapper(VelocityComponent::class)
        bounces = world.mapper(BounceComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(transforms, velocities, bounces) { id ->
            val bounce = bounces.require(id)
            if (bounce.remaining <= 0) return@forEach

            val transform = transforms.require(id)
            val rect = transform.rect
            val velocity = velocities.require(id).velocity

            // At most one reflection per entity per frame: a corner is two walls, and taking both
            // would send the entity back the way it came for two bounces.
            when {
                rect.top < 0f && velocity.y < 0f ->
                    reflect(id, transform, velocity.copy(y = -velocity.y), dy = -rect.top)

                rect.bottom > worldHeight && velocity.y > 0f ->
                    reflect(
                        id,
                        transform,
                        velocity.copy(y = -velocity.y),
                        dy = worldHeight - rect.bottom
                    )

                rect.right > worldWidth && velocity.x > 0f ->
                    reflect(
                        id,
                        transform,
                        velocity.copy(x = -velocity.x),
                        dx = worldWidth - rect.right
                    )

                rect.left < 0f && velocity.x > 0f -> Unit // travelling away from it already

                else -> return@forEach
            }
            bounce.remaining--
        }
    }

    /**
     * Turns the entity around and nudges it back inside, so it does not hit the same wall again
     * next frame and spend every bounce stuck to the edge.
     */
    private fun reflect(
        id: EntityId,
        transform: TransformComponent,
        velocity: Vector2,
        dx: Float = 0f,
        dy: Float = 0f,
    ) {
        transform.rect = transform.rect.offset(dx, dy)
        velocities.require(id).velocity = velocity
    }
}

/**
 * Removes entities that have left the frame, died, or finished a one-shot animation.
 *
 * @param worldHeight when given, entities leaving through the top or bottom are culled too, once
 *   [VERTICAL_MARGIN] clear of the edge, so aimed and radial fire does not fly on above the frame.
 *
 * A moving entity is only culled at an edge it is travelling out through, so one still on its way
 * in is left alone; swarms and formations spawn their trailing members further off screen than
 * their leader. An entity with no velocity is culled wherever it lies.
 */
class LifetimeSystem(
    private val worldWidth: Int,
    private val worldHeight: Int? = null,
) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var lifetimes: ComponentMapper<LifetimeComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>
    private lateinit var animations: ComponentMapper<AnimationComponent>
    private lateinit var playerControls: ComponentMapper<PlayerControlComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        lifetimes = world.mapper(LifetimeComponent::class)
        healths = world.mapper(HealthComponent::class)
        animations = world.mapper(AnimationComponent::class)
        playerControls = world.mapper(PlayerControlComponent::class)
        velocities = world.mapper(VelocityComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(transforms, lifetimes) { id ->
            val rect = transforms.require(id).rect
            val velocity = velocities[id]?.velocity
            val vx = velocity?.x ?: 0f
            val vy = velocity?.y ?: 0f

            // Both edges: scenery and enemies leave to the left, projectiles to the right.
            // Culling only the left edge would leak every shot that misses.
            val offLeft = rect.right < 0 && vx <= 0f
            val offRight = rect.left > worldWidth && vx >= 0f
            // A margin rather than the edge itself: a swarm or a diving enemy routinely dips a
            // little past the frame and comes back.
            val offVertically = worldHeight != null &&
                    ((rect.bottom < -VERTICAL_MARGIN && vy <= 0f) ||
                            (rect.top > worldHeight + VERTICAL_MARGIN && vy >= 0f))
            if (lifetimes.require(id).removeIfOutOfBounds && (offLeft || offRight || offVertically)) {
                world.removeEntity(id)
            }
        }

        // The dead go at once, except the player, whose run the screen ends in its own time.
        world.forEach(healths) { id ->
            if (!healths.require(id).alive) {
                if (!playerControls.has(id)) {
                    world.removeEntity(id)
                }
            }
        }

        // A one-shot animation is usually the entity's whole purpose (an explosion is only a
        // blast), so finishing it ends the entity. The player is the exception: its death animation
        // is one-shot too, and the screen still reads its health and position mid-fall.
        world.forEach(animations) { id ->
            val anim = animations.require(id)
            if (anim.isFinished && !anim.isLooping && !playerControls.has(id)) {
                world.removeEntity(id)
            }
        }
    }

    private companion object {
        /** How far past the top or bottom edge something has to be before it is culled. */
        const val VERTICAL_MARGIN = 48f
    }
}

/**
 * Draws a health bar under every entity carrying a [HealthBarComponent].
 *
 * The bar spans the entity's own width, so it reads as belonging to that sprite: the empty color
 * across the whole width, with the remaining health filled in from the left.
 *
 * Add it after [RenderSystem], or the sprites of the same frame are drawn over the bars.
 *
 * @param worldHeight framebuffer height, to keep the bar on screen when the entity is pressed
 *   against the bottom edge, as the player often is.
 */
class HealthBarSystem(private val worldHeight: Int) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>
    private lateinit var bars: ComponentMapper<HealthBarComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        healths = world.mapper(HealthComponent::class)
        bars = world.mapper(HealthBarComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        // Nothing to advance: a bar only reflects the health it is drawn from.
    }

    override fun draw(world: World, graphics: Graphics) {
        world.forEach(transforms, healths, bars) { id ->
            val bar = bars.require(id)
            val pinned = bar.pinnedTo
            val rect = pinned ?: transforms.require(id).rect
            val height = (pinned?.height ?: bar.height).toInt()
            val width = rect.width.toInt()
            if (width <= 0 || height <= 0) return@forEach

            val x = rect.left.toInt()
            val y = if (pinned != null) {
                pinned.top.toInt()
            } else {
                (rect.bottom + bar.offsetY).toInt().coerceAtMost(worldHeight - height)
            }

            graphics.drawRect(x, y, width, height, bar.emptyColor)
            val filled = (width * healths.require(id).fraction).roundToInt()
            if (filled > 0) graphics.drawRect(x, y, filled, height, bar.fullColor)
        }
    }
}

/**
 * Ages [FloatingTextComponent]s, draws them fading, and removes them once their time is up.
 * Movement is left to [MovementSystem], since a floating text is just a transform with a velocity.
 */
class FloatingTextSystem : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var texts: ComponentMapper<FloatingTextComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        texts = world.mapper(FloatingTextComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(texts) { id ->
            val text = texts.require(id)
            text.elapsed += deltaTime
            if (text.elapsed >= text.duration) world.removeEntity(id)
        }
    }

    override fun draw(world: World, graphics: Graphics) {
        world.forEach(transforms, texts) { id ->
            val text = texts.require(id)
            val rect = transforms.require(id).rect
            // Linear on purpose: a damage number is read in the first instant, and a fade that
            // lingered near full alpha would leave it cluttering the screen.
            val alpha = 1f - (text.elapsed / text.duration).coerceIn(0f, 1f)
            graphics.drawOutlinedString(
                text.text,
                rect.left.toInt(),
                rect.top.toInt(),
                text.fontSize,
                EngineColors.withAlpha(text.color, alpha),
                EngineColors.withAlpha(text.outlineColor, alpha),
            )
        }
    }
}

/**
 * Sheds trail segments from [TrailEmitterComponent]s, then ages, draws and removes them.
 *
 * What a segment looks like is left to [onEmit], as [WeaponSystem] leaves its projectiles to the
 * game, and how it moves to [MovementSystem]. Emitting on a cadence rather than per frame keeps the
 * wake the same length at any frame rate.
 *
 * Add it after [RenderSystem], or the background, a sprite like any other, would cover the trail.
 */
class TrailSystem(private val onEmit: (EntityId) -> Unit) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var trails: ComponentMapper<TrailComponent>
    private lateinit var emitters: ComponentMapper<TrailEmitterComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        trails = world.mapper(TrailComponent::class)
        emitters = world.mapper(TrailEmitterComponent::class)
        healths = world.mapper(HealthComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(transforms, emitters) { id ->
            // A dead entity stops leaving a wake, as it stops shooting.
            val health = healths[id]
            if (health != null && !health.alive) return@forEach

            val emitter = emitters.require(id)
            emitter.timeSinceLastSegment += deltaTime
            if (emitter.timeSinceLastSegment >= emitter.interval) {
                // Subtract rather than zero, so a long frame does not lose the remainder and
                // thin the wake out.
                emitter.timeSinceLastSegment -= emitter.interval
                onEmit(id)
            }
        }

        world.forEach(trails) { id ->
            val trail = trails.require(id)
            trail.elapsed += deltaTime
            if (trail.elapsed >= trail.duration) world.removeEntity(id)
        }
    }

    override fun draw(world: World, graphics: Graphics) {
        world.forEach(transforms, trails) { id ->
            val trail = trails.require(id)
            val rect = transforms.require(id).rect
            val progress = (trail.elapsed / trail.duration).coerceIn(0f, 1f)

            val scale = 1f - progress * (1f - trail.minScale)
            val width = (rect.width * scale).roundToInt()
            val height = (rect.height * scale).roundToInt()
            if (width <= 0 || height <= 0) return@forEach

            // About the center: a segment shrinking from one corner would drift off
            // the wake's line.
            graphics.drawRect(
                (rect.centerX - width / 2f).roundToInt(),
                (rect.centerY - height / 2f).roundToInt(),
                width,
                height,
                EngineColors.withAlpha(trail.color, 1f - progress),
            )

            // Half the segment's height, so the band runs down the middle of the wake.
            val coreHeight = height / 2
            if (trail.coreColor ushr 24 == 0 || coreHeight <= 0) return@forEach
            graphics.drawRect(
                (rect.centerX - width / 2f).roundToInt(),
                (rect.centerY - coreHeight / 2f).roundToInt(),
                width,
                coreHeight,
                EngineColors.scaleAlpha(trail.coreColor, 1f - progress),
            )
        }
    }
}

/**
 * Decays every [HitFlashComponent], so a flash fades instead of lingering on the entity.
 *
 * Update only: [RenderSystem] draws the flash, because it has to land between its own sprite and
 * the next in the draw order. Drawn by a later system, every flash would sit above every sprite,
 * and an enemy flashing behind another would glow through it.
 *
 * A spent flash is clamped at zero rather than removed: removing a component would change a
 * signature mid-iteration, and the component is a few bytes on an enemy about to die or leave.
 */
class HitFlashSystem : GameSystem() {
    private lateinit var flashes: ComponentMapper<HitFlashComponent>

    override fun onAttach(world: World) {
        flashes = world.mapper(HitFlashComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(flashes) { id ->
            val flash = flashes.require(id)
            if (flash.remaining > 0f) {
                flash.remaining = (flash.remaining - deltaTime).coerceAtLeast(0f)
            }
        }
    }
}

/**
 * Draws every [WoundComponent] entity from the row of its sheet its health calls for.
 *
 * Add it after whatever lands the hits and before [RenderSystem], so the blow that crosses a mark
 * shows on the frame it lands.
 *
 * The dead are left alone: the bat, for one, falls on a sheet of its own with no wounded rows.
 *
 * What else a wound does is left to [onRowChanged], called with the entity and its
 * new row whenever it changes, healed or hurt; the engine knows a creature is
 * wounded, not what that costs in the game.
 */
class WoundSystem(private val onRowChanged: (EntityId, Int) -> Unit = { _, _ -> }) : GameSystem() {
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var wounds: ComponentMapper<WoundComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>

    override fun onAttach(world: World) {
        sprites = world.mapper(SpriteComponent::class)
        wounds = world.mapper(WoundComponent::class)
        healths = world.mapper(HealthComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(sprites, wounds, healths) { id ->
            val health = healths.require(id)
            if (!health.alive) return@forEach
            val wound = wounds.require(id)
            val row = wound.rowFor(health.fraction)
            sprites.require(id).srcY = wound.rowHeight * row
            if (row != wound.row) {
                wound.row = row
                onRowChanged(id, row)
            }
        }
    }
}

/**
 * Drives everything carrying a [DeathThroesComponent]: gravity, tumble, and the
 * blasts thrown off on the way down.
 *
 * Add it before [MovementSystem], so the velocity it sets moves the entity this frame.
 *
 * What a blast looks like is left to [onPuff], as with [WeaponSystem] and [TrailSystem].
 */
class DeathSystem(private val onPuff: (EntityId) -> Unit) : GameSystem() {
    private lateinit var velocities: ComponentMapper<VelocityComponent>
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var throes: ComponentMapper<DeathThroesComponent>

    override fun onAttach(world: World) {
        velocities = world.mapper(VelocityComponent::class)
        sprites = world.mapper(SpriteComponent::class)
        throes = world.mapper(DeathThroesComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(velocities, throes) { id ->
            val dying = throes.require(id)
            dying.elapsed += deltaTime

            // Its velocity when it died, plus gravity. Accelerating from there rather than snapping
            // to a fixed drop makes the fall read as the same object carrying on.
            val velocity = velocities.require(id)
            velocity.velocity = velocity.velocity.copy(
                y = (velocity.velocity.y + dying.gravity * deltaTime)
                    .coerceAtMost(dying.terminalVelocity)
            )

            // A dying thing without a sprite still falls; it just has nothing to turn.
            sprites[id]?.let { it.rotationDegrees += dying.spinDegreesPerSecond * deltaTime }

            if (dying.puffInterval <= 0f) return@forEach
            dying.timeSincePuff += deltaTime
            if (dying.timeSincePuff >= dying.puffInterval) {
                // Subtracted rather than zeroed, so a long frame does not lose the remainder and
                // thin the trail of blasts out.
                dying.timeSincePuff -= dying.puffInterval
                onPuff(id)
            }
        }
    }
}
