package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Input
import kotlin.math.sin

/**
 * System that moves entities based on their velocity.
 */
class MovementSystem : GameSystem() {
    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.query(TransformComponent::class, VelocityComponent::class).forEach { id ->
            val transform = world.getComponent(id, TransformComponent::class)!!
            val velocity = world.getComponent(id, VelocityComponent::class)!!
            
            transform.rect = transform.rect.offset(velocity.velocity.x, velocity.velocity.y)
        }
    }
}

/**
 * System that handles enemy movement patterns.
 */
class EnemyBehaviorSystem : GameSystem() {
    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.query(TransformComponent::class, VelocityComponent::class, EnemyBehaviorComponent::class).forEach { id ->
            val transform = world.getComponent(id, TransformComponent::class)!!
            val velocity = world.getComponent(id, VelocityComponent::class)!!
            val behavior = world.getComponent(id, EnemyBehaviorComponent::class)!!
            
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
    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.query(SpriteComponent::class, AnimationComponent::class).forEach { id ->
            val sprite = world.getComponent(id, SpriteComponent::class)!!
            val anim = world.getComponent(id, AnimationComponent::class)!!
            
            if (anim.isFinished) return@forEach

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
            
            sprite.srcX = anim.currentFrame * anim.frameWidth
        }
    }
}

/**
 * System that renders entities with a SpriteComponent.
 */
class RenderSystem : GameSystem() {
    override fun update(world: World, deltaTime: Float, input: Input?) {
        // Render system doesn't usually update logic
    }

    override fun draw(world: World, graphics: Graphics) {
        val renderables = world.query(TransformComponent::class, SpriteComponent::class)
            .sortedBy { id -> world.getComponent(id, ZIndexComponent::class)?.zIndex ?: 0 }
            
        renderables.forEach { id ->
            val transform = world.getComponent(id, TransformComponent::class)!!
            val sprite = world.getComponent(id, SpriteComponent::class)!!
            
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
 * System that removes entities when they are out of bounds or marked for removal.
 */
class LifetimeSystem(private val worldWidth: Int) : GameSystem() {
    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.query(TransformComponent::class, LifetimeComponent::class).forEach { id ->
            val transform = world.getComponent(id, TransformComponent::class)!!
            val lifetime = world.getComponent(id, LifetimeComponent::class)!!
            
            if (lifetime.removeIfOutOfBounds && transform.rect.right < 0) {
                world.removeEntity(id)
            }
        }
        
        // Handle HealthComponent removal
        world.query(HealthComponent::class).forEach { id ->
            val health = world.getComponent(id, HealthComponent::class)!!
            if (!health.alive) {
                // If it's a player, we might not want to remove it immediately
                // but for enemies we do.
                if (!world.hasComponent(id, PlayerControlComponent::class)) {
                    world.removeEntity(id)
                }
            }
        }

        // Handle finished animations
        world.query(AnimationComponent::class).forEach { id ->
            val anim = world.getComponent(id, AnimationComponent::class)!!
            if (anim.isFinished && !anim.isLooping) {
                world.removeEntity(id)
            }
        }
    }
}
