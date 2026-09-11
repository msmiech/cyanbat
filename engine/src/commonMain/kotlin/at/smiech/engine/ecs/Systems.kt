package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.drawOutlinedString
import at.smiech.engine.math.Vector2
import kotlin.math.roundToInt
import kotlin.math.sin

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

/**
 * System that handles enemy movement patterns.
 */
class EnemyBehaviorSystem : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>
    private lateinit var behaviors: ComponentMapper<EnemyBehaviorComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        velocities = world.mapper(VelocityComponent::class)
        behaviors = world.mapper(EnemyBehaviorComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(transforms, velocities, behaviors) { id ->
            val transform = transforms.require(id)
            val velocity = velocities.require(id)
            val behavior = behaviors.require(id)

            behavior.elapsedTime += deltaTime

            when (behavior.type) {
                EnemyMovementType.SCOUT -> {
                    velocity.velocity = velocity.velocity.copy(y = sin(behavior.elapsedTime * 2f) * 0.2f)
                }
                EnemyMovementType.SINE -> {
                    val amplitude = 80f
                    val frequency = 3f
                    val targetY = behavior.initialY + sin(behavior.elapsedTime * frequency) * amplitude
                    val dy = (targetY - transform.rect.top) * 0.1f
                    velocity.velocity = velocity.velocity.copy(y = dy)
                }
                EnemyMovementType.ZIGZAG -> {
                    if (behavior.elapsedTime > behavior.nextDirectionChange) {
                        behavior.verticalDirection *= -1f
                        behavior.nextDirectionChange = behavior.elapsedTime + 0.8f // simplified
                    }
                    velocity.velocity = velocity.velocity.copy(y = behavior.verticalDirection * 2.5f)
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
            }
        }
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

            // The unscaled call for everything that is drawn at its own size, so the common case
            // stays on the path both backends are tuned for.
            if (sprite.scale == 1f) {
                graphics.drawPixmap(
                    sprite.pixmap,
                    transform.rect.left.toInt(),
                    transform.rect.top.toInt(),
                    sprite.srcX,
                    sprite.srcY,
                    sprite.srcWidth,
                    sprite.srcHeight
                )
            } else {
                graphics.drawPixmap(
                    sprite.pixmap,
                    transform.rect.left.toInt(),
                    transform.rect.top.toInt(),
                    sprite.srcX,
                    sprite.srcY,
                    sprite.srcWidth,
                    sprite.srcHeight,
                    (sprite.srcWidth * sprite.scale).roundToInt(),
                    (sprite.srcHeight * sprite.scale).roundToInt(),
                )
            }
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
                    reflect(id, transform, velocity.copy(y = -velocity.y), dy = worldHeight - rect.bottom)

                rect.right > worldWidth && velocity.x > 0f ->
                    reflect(id, transform, velocity.copy(x = -velocity.x), dx = worldWidth - rect.right)

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
 */
class LifetimeSystem(private val worldWidth: Int) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var lifetimes: ComponentMapper<LifetimeComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>
    private lateinit var animations: ComponentMapper<AnimationComponent>
    private lateinit var playerControls: ComponentMapper<PlayerControlComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        lifetimes = world.mapper(LifetimeComponent::class)
        healths = world.mapper(HealthComponent::class)
        animations = world.mapper(AnimationComponent::class)
        playerControls = world.mapper(PlayerControlComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(transforms, lifetimes) { id ->
            val rect = transforms.require(id).rect

            // Both edges: scenery and enemies leave to the left, projectiles to the right.
            // Culling only the left edge would leak every shot that misses.
            val offLeft = rect.right < 0
            val offRight = rect.left > worldWidth
            if (lifetimes.require(id).removeIfOutOfBounds && (offLeft || offRight)) {
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

        // Handle finished animations
        world.forEach(animations) { id ->
            val anim = animations.require(id)
            if (anim.isFinished && !anim.isLooping) {
                world.removeEntity(id)
            }
        }
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
 * What a segment looks like is left to [onEmit] - size and colour are a game's business, the same
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

            // About the centre: a segment that shrank from one corner would crawl away from the
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
