package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.drawOutlinedString
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * System that moves entities based on their velocity.
 */
class MovementSystem : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        velocities = world.mapper(VelocityComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(transforms, velocities) { id ->
            val transform = transforms.require(id)
            val velocity = velocities.require(id).velocity

            transform.rect = transform.rect.offset(velocity.x, velocity.y)
        }
    }
}

/** How a [EnemyMovementType.BOSS] enters and then holds its ground, in framebuffer pixels per tick. */
private const val BOSS_APPROACH_SPEED = -1.1f

/**
 * Its weave once it is there: slow and wide enough that the player has to follow it, but never so
 * fast that it cannot be tracked with a thumb.
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
 * A hover: how long it hangs on station, how quickly its lane drifts toward the player's - slow,
 * so it tracks a player who sits still and loses one who keeps moving - and how it bobs there.
 */
private const val HOVER_SECONDS = 5f
private const val HOVER_LANE_DRIFT = 0.35f
private const val HOVER_BOB = 10f
private const val HOVER_LEAVE_FACTOR = 1.3f

/**
 * A dive: how long the tell lasts - the moment it checks its swing and backs off a little, which
 * is the player's warning - and how fast it comes once committed. Its closing speed never drops
 * below [DIVE_MIN_CLOSING], so a player who has slipped behind it cannot make it fly backwards.
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

/** Stages of the patterns that have them; see [EnemyBehaviorComponent.state]. */
private const val STAGE_APPROACH = 0
private const val STAGE_HOLD = 1
private const val STAGE_COMMITTED = 2

/**
 * System that handles enemy movement patterns.
 *
 * Some patterns aim at the player, so it looks the player up once per update: the first living
 * entity carrying a [PlayerControlComponent]. With none - a test world, or a bat already dead -
 * those patterns fly on as if nobody were there.
 */
class EnemyBehaviorSystem : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>
    private lateinit var behaviors: ComponentMapper<EnemyBehaviorComponent>
    private lateinit var players: ComponentMapper<PlayerControlComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>

    /** The player's center this update, or NaN when there is no living player to aim at. */
    private var targetX = Float.NaN
    private var targetY = Float.NaN

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        velocities = world.mapper(VelocityComponent::class)
        behaviors = world.mapper(EnemyBehaviorComponent::class)
        players = world.mapper(PlayerControlComponent::class)
        healths = world.mapper(HealthComponent::class)
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

            behavior.elapsedTime += deltaTime * behavior.tempo
            behavior.stateTime += deltaTime

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
                        behavior.nextDirectionChange = behavior.elapsedTime + 0.8f // simplified
                    }
                    velocity.velocity =
                        velocity.velocity.copy(y = behavior.verticalDirection * 2.5f)
                }

                EnemyMovementType.BOSS -> {
                    // Closes from the edge it entered on, then stops dead at its station. Its own
                    // x velocity is overwritten here rather than decayed, so the entrance reads as
                    // one deliberate move instead of a drift.
                    val closing = transform.rect.left > behavior.holdX
                    val targetY = behavior.initialY +
                            sin(behavior.elapsedTime * BOSS_WEAVE_FREQUENCY) * BOSS_WEAVE_AMPLITUDE
                    velocity.velocity = Vector2(
                        x = if (closing) BOSS_APPROACH_SPEED else 0f,
                        // Chased rather than set outright, so the boss eases into the turns at the
                        // top and bottom of its weave instead of snapping between them.
                        y = (targetY - transform.rect.top) * BOSS_WEAVE_TRACKING,
                    )
                }

                EnemyMovementType.SWARM -> swarm(transform, velocity, behavior)
                EnemyMovementType.SURGE -> surge(velocity, behavior)
                EnemyMovementType.HOVER -> hover(transform, velocity, behavior)
                EnemyMovementType.DIVE -> dive(transform, velocity, behavior)
                EnemyMovementType.FORMATION -> formation(transform, velocity, behavior)
                EnemyMovementType.BOSS_FIGURE_EIGHT -> figureEight(transform, velocity, behavior)
            }
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
                .coerceIn(-HOVER_LANE_DRIFT, HOVER_LANE_DRIFT)
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
        if (behavior.state == STAGE_APPROACH && rect.left <= behavior.holdX) enter(behavior, STAGE_HOLD)

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

            // Committed: the heading was set once, on the way in, and is left alone. A dive that
            // kept steering would be a homing missile, which nothing can dodge.
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
                val weaveY = behavior.initialY + sin(behavior.elapsedTime * BOSS_WEAVE_FREQUENCY) * BOSS_WEAVE_AMPLITUDE
                velocity.velocity = Vector2(BOSS_APPROACH_SPEED, (weaveY - rect.top) * BOSS_WEAVE_TRACKING)
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
 * Add it after [RenderSystem]: a bubble is drawn around a sprite, and one drawn first would be
 * painted over by the enemy it is meant to be enclosing.
 *
 * The bubble is sized off the entity's box rather than set per entity, so the same component reads
 * right on a wasp and on a boss.
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

            // Nothing dead recharges, and nothing without a recharge rate ever comes back.
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
                // Faint inside, so the enemy stays readable through it; the rim carries the
                // strength, fading as the bubble wears down; and a hit flares both.
                graphics.drawOval(
                    left, top, size, size,
                    EngineColors.withAlpha(shield.color, FILL_ALPHA + FLASH_FILL_ALPHA * shield.flash),
                )
                graphics.drawOvalOutline(
                    left, top, size, size,
                    EngineColors.withAlpha(
                        shield.color,
                        (RIM_MIN_ALPHA + RIM_RANGE_ALPHA * shield.fraction + shield.flash).coerceAtMost(1f),
                    ),
                )
                // A glint on the upper left, the side the game's light comes from.
                val glint = (radius * 0.5f).roundToInt()
                graphics.drawOvalOutline(
                    left + glint / 2, top + glint / 2, glint, glint,
                    EngineColors.withAlpha(EngineColors.WHITE, GLINT_ALPHA),
                )
            } else if (shield.popTime > 0f) {
                // Going out as a ring that grows and fades, so a broken bubble is an event.
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
 * Run it after everything that can change a velocity - the movement, the bounce, the steering - so
 * the angle a frame is drawn at is the direction that frame is actually travelling. A shot coming
 * off a wall then turns on the same frame it reverses, rather than a frame late.
 *
 * The artwork is assumed to point right at zero degrees, which is the axis the game's sprites are
 * drawn along.
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
            // Something at a standstill has no direction to face, so it keeps the last one it had
            // rather than snapping to zero - which for a bullet shape would be a visible flick.
            if (velocity.x == 0f && velocity.y == 0f) return@forEach

            sprites.require(id).rotationDegrees = atan2(velocity.y, velocity.x) * DEGREES_PER_RADIAN
        }
    }

    private companion object {
        const val DEGREES_PER_RADIAN = 57.29578f
    }
}

/**
 * System that handles animations by updating Sprite source rectangles.
 */
class AnimationSystem : GameSystem() {
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var animations: ComponentMapper<AnimationComponent>

    override fun onAttach(world: World) {
        sprites = world.mapper(SpriteComponent::class)
        animations = world.mapper(AnimationComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(sprites, animations) { id ->
            val anim = animations.require(id)

            if (!anim.isFinished) {
                anim.currentTime += deltaTime
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
 * System that renders entities with a SpriteComponent.
 */
class RenderSystem : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var zIndices: ComponentMapper<ZIndexComponent>
    private lateinit var flashes: ComponentMapper<HitFlashComponent>

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
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        // Render system doesn't usually update logic
    }

    override fun draw(world: World, graphics: Graphics) {
        var count = 0
        world.forEach(transforms, sprites) { id ->
            if (count == ids.size) {
                ids = ids.copyOf(count * 2)
                order = order.copyOf(count * 2)
            }
            val zIndex = zIndices[id]?.zIndex ?: 0
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

            // Three paths, narrowest first. Everything but the projectiles is drawn upright at its
            // own size, and that case must not pay for a canvas transform it does not use.
            when {
                sprite.rotationDegrees != 0f -> graphics.drawPixmap(
                    sprite.pixmap, left, top,
                    sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                    dstWidth, dstHeight, sprite.rotationDegrees,
                )

                sprite.scale != 1f -> graphics.drawPixmap(
                    sprite.pixmap, left, top,
                    sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                    dstWidth, dstHeight,
                )

                else -> graphics.drawPixmap(
                    sprite.pixmap, left, top,
                    sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                )
            }

            // Straight over the frame just drawn, while this sprite is still the top of the
            // picture. That is the whole reason the flash is drawn here rather than in a system of
            // its own - see HitFlashSystem.
            //
            // Drawn upright even when the sprite above it was turned: the only thing in the game
            // that rotates is a projectile, and a projectile is spent by what it hits rather than
            // hurt by it, so nothing that flashes also has an angle. Give something rotating a
            // flash and this is the line that will need a rotated blit behind it.
            val flash = flashes[id] ?: continue
            val strength = flash.strength
            if (strength <= 0f) continue
            graphics.drawPixmapSilhouette(
                sprite.pixmap, left, top,
                sprite.srcX, sprite.srcY, sprite.srcWidth, sprite.srcHeight,
                dstWidth, dstHeight,
                EngineColors.scaleAlpha(flash.color, strength),
            )
        }
    }
}

/**
 * Fires weapons whose cadence has come round.
 *
 * Spawning is delegated to [onFire] because what a projectile looks like is a game concern, not
 * an engine one - the same split CollisionSystem uses for its handler.
 */
class WeaponSystem(private val onFire: (EntityId) -> Unit) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var weapons: ComponentMapper<WeaponComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        weapons = world.mapper(WeaponComponent::class)
        healths = world.mapper(HealthComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(transforms, weapons) { id ->
            // A dead entity keeps its weapon but stops using it.
            val health = healths[id]
            if (health == null || health.alive) {
                val weapon = weapons.require(id)
                weapon.timeSinceLastShot += deltaTime
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
 * Placed between [MovementSystem] and [LifetimeSystem], and it has to be: movement is what carries
 * an entity into an edge, and culling is what would remove it there. Run it earlier and it
 * reflects things that have not reached the edge yet; run it later and there is nothing left to
 * reflect.
 *
 * Only the leading edge counts. An entity is reflected off a wall it is actually travelling into,
 * never off one it is already moving away from - without that, something that ends a frame still
 * overlapping an edge would flip back and forth and burn every bounce it has in a few frames.
 *
 * @param worldWidth/worldHeight the framebuffer, which is what the edges are.
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
            // at once would send the entity back the way it came for the price of two bounces.
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
     * Turns the entity around and nudges it back inside.
     *
     * The nudge is what stops the same wall being hit again on the next frame, which would spend
     * every bounce in a row and leave the entity stuck to the edge.
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
 * System that removes entities when they are out of bounds or marked for removal.
 *
 * @param worldHeight when given, things that leave through the top or the bottom are culled too,
 *   once they are [VERTICAL_MARGIN] clear of the edge. For a long time nothing could: everything
 *   travelled left or right. Aimed and radial enemy fire does not, and a shot that left through the
 *   ceiling would otherwise fly on above the frame for the rest of the run.
 *
 * Something moving is only culled at an edge it is travelling *out* through. An entity still on its
 * way in from beyond the frame is left alone, which is what lets a swarm or a formation spawn with
 * its trailing members further off screen than its leader - without that, everything behind the
 * leader was deleted on the tick it arrived. Something with no velocity is culled wherever it
 * lies, as it always was.
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

        // Handle HealthComponent removal
        world.forEach(healths) { id ->
            if (!healths.require(id).alive) {
                // If it's a player, we might not want to remove it immediately
                // but for enemies we do.
                if (!playerControls.has(id)) {
                    world.removeEntity(id)
                }
            }
        }

        // Handle finished animations. A one-shot animation is normally the entire reason its
        // entity exists - an explosion is a blast and nothing else - so finishing it is the same
        // as being over.
        //
        // The player is the exception, and has to be: its death animation is one-shot too, and
        // culling the bat the instant that animation ended would delete the entity the screen
        // still reads its health and position from, mid-fall. Who ends the player's run is the
        // screen's decision, exactly as it already is for the health cull above.
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
 * Draws a health bar under every entity carrying a [HealthBarComponent], and nothing under the
 * ones that do not.
 *
 * The bar spans the entity's own width, so it reads as belonging to that sprite: black underneath
 * for the whole width, then the remaining health filled in from the left edge. A full bar is
 * therefore solid [HealthBarComponent.fullColor] and an empty one solid black.
 *
 * Add it after [RenderSystem], or the sprites of the same frame will be drawn over the bars.
 *
 * @param worldHeight framebuffer height, used to keep the bar on screen when the entity is pressed
 *   right against the bottom edge - which the player, who is clamped to the frame, routinely is.
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
        // Nothing to advance: a bar only ever reflects the health it is drawn from.
    }

    override fun draw(world: World, graphics: Graphics) {
        world.forEach(transforms, healths, bars) { id ->
            val bar = bars.require(id)
            val rect = transforms.require(id).rect
            val height = bar.height.toInt()
            val width = rect.width.toInt()
            if (width <= 0 || height <= 0) return@forEach

            val x = rect.left.toInt()
            val y = (rect.bottom + bar.offsetY).toInt().coerceAtMost(worldHeight - height)

            graphics.drawRect(x, y, width, height, bar.emptyColor)
            val filled = (width * healths.require(id).fraction).roundToInt()
            if (filled > 0) graphics.drawRect(x, y, filled, height, bar.fullColor)
        }
    }
}

/**
 * Ages [FloatingTextComponent]s, draws them fading, and reaps them once their time is up.
 *
 * Movement is left to [MovementSystem] - a floating text is just a transform with a velocity, so
 * there is nothing here worth duplicating.
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
            // Linear, and deliberately: a damage number is read in the first instant, so a fade
            // that lingers near full alpha would just leave the number sitting on the screen.
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
 * Sheds trail segments off [TrailEmitterComponent]s, then ages, draws and reaps them.
 *
 * What a segment looks like is left to [onEmit] - size and color are a game's business, the same
 * split [WeaponSystem] uses - and how it moves is left to [MovementSystem], so a segment is just a
 * transform with a velocity. Emitting on a cadence rather than per frame is what keeps the wake the
 * same length whatever the frame rate.
 *
 * Add it after [RenderSystem]: a trail drawn before the sprites would be painted over by the
 * background, which is a sprite like any other.
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
            // A dead entity stops leaving a wake, the way a dead one stops shooting.
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

            // About the center: a segment that shrank from one corner would crawl away from the
            // line the rest of the wake sits on.
            graphics.drawRect(
                (rect.centerX - width / 2f).roundToInt(),
                (rect.centerY - height / 2f).roundToInt(),
                width,
                height,
                EngineColors.withAlpha(trail.color, 1f - progress),
            )
        }
    }
}

/**
 * Burns down every [HitFlashComponent], so a flash fades instead of hanging on the entity.
 *
 * Update only: the drawing is [RenderSystem]'s, and deliberately, because a flash has to land
 * between its own sprite and the next one in the draw order. A system of its own drawing after the
 * sprites would put every flash above every sprite, so an enemy lighting up behind another would
 * glow through the one in front of it. [RenderSystem] is the only thing that knows where in the
 * order a given sprite sat.
 *
 * A spent flash is clamped at zero rather than taken off the entity. Removing a component would
 * change the signature a query is in the middle of iterating, and the component is a few bytes on
 * an enemy that is about to die or leave the frame anyway - where a mid-iteration structural
 * change is a class of bug that only shows up on the frame two things happen at once.
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
 * Drives everything carrying a [DeathThroesComponent]: gravity, tumble, and the blasts thrown off
 * on the way down.
 *
 * Add it before [MovementSystem], so the velocity it sets is the velocity that frame is moved by
 * rather than the next one's.
 *
 * What a blast looks like is left to [onPuff], the same split [WeaponSystem] and [TrailSystem]
 * use: an engine knows that a dying thing sheds debris, not what this game's debris is made of.
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

            // Whatever it was doing when it died, plus gravity from there. Accelerating from the
            // last velocity rather than snapping to a fixed drop is what makes the fall read as
            // the same object carrying on, rather than as a new one being dropped in its place.
            val velocity = velocities.require(id)
            velocity.velocity = velocity.velocity.copy(
                y = (velocity.velocity.y + dying.gravity * deltaTime)
                    .coerceAtMost(dying.terminalVelocity)
            )

            // Not every dying thing has a sprite to turn - a dying thing with no sprite is still
            // allowed to fall.
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
