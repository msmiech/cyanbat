package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.SHOT_SPEED
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.AnimationComponent
import at.smiech.engine.ecs.BackgroundComponent
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LifetimeComponent
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.VelocityComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.ecs.ZIndexComponent
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2

class EntityFactory(private val world: World) {

    fun createBat(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        pixmap: Pixmap,
        shotIntervalSeconds: Float,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2.Zero))
        world.addComponent(id, SpriteComponent(pixmap, srcWidth = 45)) // DEFAULT_WIDTH
        world.addComponent(id, AnimationComponent(45, height.toInt(), 2, 0.2f))
        world.addComponent(id, CollisionComponent(5f, CollisionGroup.PLAYER))
        world.addComponent(id, HealthComponent(3))
        world.addComponent(id, PlayerControlComponent())
        world.addComponent(id, WeaponComponent(shotIntervalSeconds))
        world.addComponent(id, LifetimeComponent(false))
        world.addComponent(id, ZIndexComponent(20))
        return id
    }

    fun createEnemy(x: Float, y: Float, width: Float, height: Float, pixmap: Pixmap, type: Int): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        
        val speedX = when (type) {
            0 -> -2.5f
            1 -> -1.5f
            2 -> -1.2f
            else -> -1.0f
        }
        world.addComponent(id, VelocityComponent(Vector2(speedX, 0f)))
        
        val srcX = when (type) {
            0 -> 0
            1 -> 67
            2 -> 137
            else -> 0
        }
        world.addComponent(id, SpriteComponent(pixmap, baseSrcX = srcX, srcWidth = 32))
        world.addComponent(id, AnimationComponent(32, height.toInt(), 2, 0.2f))
        
        val movementType = when (type) {
            0 -> EnemyMovementType.SCOUT
            1 -> EnemyMovementType.SINE
            2 -> EnemyMovementType.ZIGZAG
            else -> EnemyMovementType.SCOUT
        }
        world.addComponent(id, EnemyBehaviorComponent(movementType, y))
        
        world.addComponent(id, CollisionComponent(5f, CollisionGroup.ENEMY))
        world.addComponent(id, HealthComponent(1))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(10))
        return id
    }

    fun createBackground(x: Float, y: Float, width: Float, height: Float, pixmap: Pixmap): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2(-2f, 0f)))
        world.addComponent(id, SpriteComponent(pixmap))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, BackgroundComponent())
        world.addComponent(id, ZIndexComponent(-100))
        return id
    }
    
    fun createObstacle(x: Float, y: Float, width: Float, height: Float, pixmap: Pixmap): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2(-1f, 0f)))
        world.addComponent(id, SpriteComponent(pixmap))
        world.addComponent(id, CollisionComponent(5f, CollisionGroup.OBSTACLE))
        world.addComponent(id, HealthComponent(1))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(5))
        return id
    }

    fun createShot(x: Float, y: Float, width: Float, height: Float, pixmap: Pixmap, isPlayer: Boolean): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2(if (isPlayer) SHOT_SPEED else -SHOT_SPEED, 0f)))
        world.addComponent(id, SpriteComponent(pixmap))
        world.addComponent(id, CollisionComponent(2f, if (isPlayer) CollisionGroup.PLAYER_PROJECTILE else CollisionGroup.ENEMY_PROJECTILE))
        world.addComponent(id, HealthComponent(1))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(15))
        return id
    }

    fun createExplosion(x: Float, y: Float, width: Float, height: Float, pixmap: Pixmap): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2(-1f, 0f)))
        world.addComponent(id, SpriteComponent(pixmap, srcWidth = 25))
        world.addComponent(id, AnimationComponent(25, height.toInt(), 5, 0.3f, isLooping = false))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(50))
        return id
    }
}
