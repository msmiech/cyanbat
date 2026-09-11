package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.DAMAGE_TEXT_DURATION_SECONDS
import at.smiech.cyanbat.util.DAMAGE_TEXT_FONT_SIZE
import at.smiech.cyanbat.util.DAMAGE_TEXT_RISE_PER_TICK
import at.smiech.cyanbat.util.DESTRUCTIBLE_HIT_POINTS
import at.smiech.cyanbat.util.HEALTH_BAR_HEIGHT
import at.smiech.cyanbat.util.HEALTH_BAR_OFFSET_Y
import at.smiech.cyanbat.util.PLAYER_MAX_HIT_POINTS
import at.smiech.cyanbat.util.SHOT_HIT_POINTS
import at.smiech.cyanbat.util.SHOT_SPEED
import at.smiech.cyanbat.util.TRAIL_DRIFT_PER_TICK
import at.smiech.cyanbat.util.TRAIL_DURATION_SECONDS
import at.smiech.cyanbat.util.TRAIL_INTERVAL_SECONDS
import at.smiech.cyanbat.util.TRAIL_MIN_SCALE
import at.smiech.engine.EngineColors
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.AnimationComponent
import at.smiech.engine.ecs.BackgroundComponent
import at.smiech.engine.ecs.BounceComponent
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.DamageComponent
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.FloatingTextComponent
import at.smiech.engine.ecs.HealthBarComponent
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LifetimeComponent
import at.smiech.engine.ecs.PierceComponent
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
import kotlin.math.cos
import kotlin.math.sin

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
        // Bars are for the two things a fight is decided between - the bat and the level's boss.
        // A bar over every passing enemy would bury the game behind them.
        world.addComponent(id, HealthBarComponent(HEALTH_BAR_HEIGHT, HEALTH_BAR_OFFSET_Y))
        world.addComponent(id, PlayerControlComponent())
        world.addComponent(id, WeaponComponent(shotIntervalSeconds))
        world.addComponent(id, TrailEmitterComponent(TRAIL_INTERVAL_SECONDS))
        world.addComponent(id, LifetimeComponent(false))
        world.addComponent(id, ZIndexComponent(20))
        return id
    }

    /**
     * One of the three enemies from the sheet, at whatever strength the wave that ordered it
     * calls for.
     *
     * [hitPoints], [damage] and [speedMultiplier] are arguments rather than constants because
     * that is the whole of how a level ramps: the same three sprites, sent in tougher, angrier
     * and faster as the minutes go by. They are fixed at spawn, so enemies already on screen keep
     * the strength they arrived with when a wave turns over.
     */
    fun createEnemy(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        pixmap: Pixmap,
        type: Int,
        hitPoints: Int = DESTRUCTIBLE_HIT_POINTS,
        damage: Int = DAMAGE_PER_HIT,
        speedMultiplier: Float = 1f,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))

        val speedX = when (type) {
            0 -> -2.5f
            1 -> -1.5f
            2 -> -1.2f
            else -> -1.0f
        }
        world.addComponent(id, VelocityComponent(Vector2(speedX * speedMultiplier, 0f)))

        world.addComponent(id, SpriteComponent(pixmap, baseSrcX = srcXOf(type), srcWidth = ENEMY_FRAME_WIDTH))
        world.addComponent(id, AnimationComponent(ENEMY_FRAME_WIDTH, height.toInt(), 2, 0.2f))

        val movementType = when (type) {
            0 -> EnemyMovementType.SCOUT
            1 -> EnemyMovementType.SINE
            2 -> EnemyMovementType.ZIGZAG
            else -> EnemyMovementType.SCOUT
        }
        world.addComponent(id, EnemyBehaviorComponent(movementType, y))

        world.addComponent(id, CollisionComponent(5f, CollisionGroup.ENEMY))
        world.addComponent(id, HealthComponent(hitPoints))
        world.addComponent(id, DamageComponent(damage))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(10))
        return id
    }

    /**
     * The level's boss: the sheet's last enemy drawn [scale] times over, with a health pool worth
     * a fight and a gun of its own.
     *
     * It differs from [createEnemy] in three ways that matter, and each is deliberate. It is never
     * culled for leaving the frame, because it enters from the edge and a boss that could drift
     * out of the level is a boss the player can lose rather than beat. It carries a health bar,
     * the only thing besides the bat that does, because a fight this long is unreadable without
     * one. And it holds station instead of closing, which is what [EnemyMovementType.BOSS] is for.
     */
    fun createBoss(
        x: Float,
        y: Float,
        holdX: Float,
        pixmap: Pixmap,
        scale: Float,
        hitPoints: Int,
        damage: Int,
        shotIntervalSeconds: Float,
    ): EntityId {
        val width = ENEMY_FRAME_WIDTH * scale
        val height = pixmap.height * scale

        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2.Zero))
        world.addComponent(
            id,
            SpriteComponent(pixmap, baseSrcX = srcXOf(BOSS_ENEMY_TYPE), srcWidth = ENEMY_FRAME_WIDTH, scale = scale)
        )
        world.addComponent(id, AnimationComponent(ENEMY_FRAME_WIDTH, pixmap.height, 2, 0.2f))
        world.addComponent(id, EnemyBehaviorComponent(EnemyMovementType.BOSS, y, holdX = holdX))
        // Tolerance scaled with the sprite, so the boss's box sits in from its edges by the same
        // proportion an ordinary enemy's does.
        world.addComponent(id, CollisionComponent(5f * scale, CollisionGroup.ENEMY))
        world.addComponent(id, HealthComponent(hitPoints))
        world.addComponent(id, DamageComponent(damage))
        world.addComponent(id, HealthBarComponent(HEALTH_BAR_HEIGHT * scale, HEALTH_BAR_OFFSET_Y))
        world.addComponent(id, WeaponComponent(shotIntervalSeconds))
        world.addComponent(id, LifetimeComponent(false))
        world.addComponent(id, ZIndexComponent(12))
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

    /**
     * @param angleDegrees how far off straight the shot flies, positive downwards. A fanned shot
     *   keeps the full [SHOT_SPEED] along its own heading rather than along x, so the outer shots
     *   of a spread do not lag behind the middle one.
     */
    fun createShot(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        pixmap: Pixmap,
        isPlayer: Boolean,
        damage: Int = DAMAGE_PER_HIT,
        angleDegrees: Float = 0f,
        pierce: Int = 0,
        bounce: Int = 0,
    ): EntityId {
        val radians = angleDegrees * PI_OVER_180
        val forward = if (isPlayer) SHOT_SPEED else -SHOT_SPEED

        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(
            id,
            VelocityComponent(Vector2(forward * cos(radians), SHOT_SPEED * sin(radians)))
        )
        // Added only when the run has earned them, so an ordinary shot carries no bookkeeping it
        // will never use - and so the collision handler can tell a piercing shot by its component.
        if (pierce > 0) world.addComponent(id, PierceComponent(pierce))
        if (bounce > 0) world.addComponent(id, BounceComponent(bounce))
        world.addComponent(id, SpriteComponent(pixmap))
        world.addComponent(id, CollisionComponent(2f, if (isPlayer) CollisionGroup.PLAYER_PROJECTILE else CollisionGroup.ENEMY_PROJECTILE))
        world.addComponent(id, HealthComponent(SHOT_HIT_POINTS))
        world.addComponent(id, DamageComponent(damage))
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

    /**
     * A blast centred on ([centerX], [centerY]).
     *
     * Centred rather than placed by its corner because the caller knows what died, not how big an
     * explosion frame happens to be - and [scale] changes that size. Blowing the blast up to match
     * is what keeps a boss from going out in the same puff as one of its escorts.
     */
    fun createExplosion(
        centerX: Float,
        centerY: Float,
        pixmap: Pixmap,
        scale: Float = 1f,
    ): EntityId {
        val width = EXPLOSION_FRAME_WIDTH * scale
        val height = pixmap.height * scale

        val id = world.createEntity()
        world.addComponent(
            id,
            TransformComponent(Rect.fromLTWH(centerX - width / 2f, centerY - height / 2f, width, height))
        )
        world.addComponent(id, VelocityComponent(Vector2(-1f, 0f)))
        world.addComponent(id, SpriteComponent(pixmap, srcWidth = EXPLOSION_FRAME_WIDTH, scale = scale))
        world.addComponent(id, AnimationComponent(EXPLOSION_FRAME_WIDTH, pixmap.height, 5, 0.3f, isLooping = false))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(50))
        return id
    }

    private companion object {
        /** Width of one enemy frame in the shared sheet; the strips are addressed by [srcXOf]. */
        const val ENEMY_FRAME_WIDTH = 32
        const val EXPLOSION_FRAME_WIDTH = 25

        /** The sheet strip each enemy type animates from. */
        fun srcXOf(type: Int): Int = when (type) {
            0 -> 0
            1 -> 67
            2 -> 137
            else -> 0
        }

        /** The boss wears the third enemy's colours, the same ones the final wave escorts it in. */
        const val BOSS_ENEMY_TYPE = 2

        /** Degrees to radians, for the spread on a fanned shot. */
        const val PI_OVER_180 = 0.017453292f
    }
}
