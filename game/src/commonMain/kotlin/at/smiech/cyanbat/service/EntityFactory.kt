package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.DAMAGE_TEXT_DURATION_SECONDS
import at.smiech.cyanbat.util.DAMAGE_TEXT_FONT_SIZE
import at.smiech.cyanbat.util.DAMAGE_TEXT_RISE_PER_TICK
import at.smiech.cyanbat.util.DESTRUCTIBLE_HIT_POINTS
import at.smiech.cyanbat.util.HEALTH_BAR_HEIGHT
import at.smiech.cyanbat.util.HEALTH_BAR_OFFSET_Y
import at.smiech.cyanbat.util.PLAYER_MAX_HIT_POINTS
import at.smiech.cyanbat.util.SHOT_SPEED
import at.smiech.cyanbat.util.TRAIL_DRIFT_PER_TICK
import at.smiech.cyanbat.util.TRAIL_DURATION_SECONDS
import at.smiech.cyanbat.util.TRAIL_INTERVAL_SECONDS
import at.smiech.cyanbat.util.TRAIL_MIN_SCALE
import at.smiech.engine.EngineColors
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.AnimationComponent
import at.smiech.engine.ecs.BackgroundComponent
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.FloatingTextComponent
import at.smiech.engine.ecs.HealthBarComponent
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LifetimeComponent
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TrailComponent
import at.smiech.engine.ecs.TrailEmitterComponent
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
        world.addComponent(id, HealthComponent(PLAYER_MAX_HIT_POINTS))
        // The bat is the only entity that gets a bar: the player needs to see how much of their own
        // health is left, and a bar over every passing enemy would bury the game behind them.
        world.addComponent(id, HealthBarComponent(HEALTH_BAR_HEIGHT, HEALTH_BAR_OFFSET_Y))
        world.addComponent(id, PlayerControlComponent())
        world.addComponent(id, WeaponComponent(shotIntervalSeconds))
        world.addComponent(id, TrailEmitterComponent(TRAIL_INTERVAL_SECONDS))
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
        world.addComponent(id, HealthComponent(DESTRUCTIBLE_HIT_POINTS))
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
        world.addComponent(id, HealthComponent(DESTRUCTIBLE_HIT_POINTS))
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
        world.addComponent(id, HealthComponent(DESTRUCTIBLE_HIT_POINTS))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(15))
        return id
    }

    /**
     * One segment of the bat's wake, shed just off its rear by [at.smiech.engine.ecs.TrailSystem].
     *
     * It drifts with the scenery rather than with the bat, so the wake marks where the bat has
     * been instead of following it around, and it is culled at the left edge like anything else
     * that leaves the frame.
     */
    fun createTrail(x: Float, y: Float, width: Float, height: Float): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2(TRAIL_DRIFT_PER_TICK, 0f)))
        world.addComponent(
            id,
            TrailComponent(EngineColors.CYAN, TRAIL_DURATION_SECONDS, TRAIL_MIN_SCALE)
        )
        world.addComponent(id, LifetimeComponent(true))
        return id
    }

    /**
     * A damage number that rises from ([x], [y]) and fades out. Drawn over the run rather than in
     * it, so it is never lost behind the enemy it belongs to.
     *
     * Carries no collision or health of its own, so nothing in the run can touch it: it drifts on
     * the shared [at.smiech.engine.ecs.MovementSystem] and is reaped by
     * [at.smiech.engine.ecs.FloatingTextSystem] when its time is up.
     */
    fun createDamageText(x: Float, y: Float, damage: Int): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 0f, 0f)))
        world.addComponent(id, VelocityComponent(Vector2(0f, -DAMAGE_TEXT_RISE_PER_TICK)))
        world.addComponent(
            id,
            FloatingTextComponent(
                text = damage.toString(),
                fontSize = DAMAGE_TEXT_FONT_SIZE,
                duration = DAMAGE_TEXT_DURATION_SECONDS,
            )
        )
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
