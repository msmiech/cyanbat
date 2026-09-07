package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Input
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

            graphics.drawPixmap(
                sprite.pixmap,
                transform.rect.left.toInt(),
                transform.rect.top.toInt(),
                sprite.srcX,
                sprite.srcY,
                sprite.srcWidth,
                sprite.srcHeight
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
