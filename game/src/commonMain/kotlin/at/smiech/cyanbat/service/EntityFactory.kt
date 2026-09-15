package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BAT_FRAME_WIDTH
import at.smiech.cyanbat.util.CRITICAL_TEXT_DURATION_SECONDS
import at.smiech.cyanbat.util.CRITICAL_TEXT_FONT_SIZE
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.DAMAGE_TEXT_DURATION_SECONDS
import at.smiech.cyanbat.util.DAMAGE_TEXT_FONT_SIZE
import at.smiech.cyanbat.util.DAMAGE_TEXT_RISE_PER_TICK
import at.smiech.cyanbat.util.DESTRUCTIBLE_HIT_POINTS
import at.smiech.cyanbat.util.HEALTH_BAR_HEIGHT
import at.smiech.cyanbat.util.HEALTH_BAR_OFFSET_Y
import at.smiech.cyanbat.util.ENEMY_SHOT_VARIANT_OFFSET
import at.smiech.cyanbat.util.PLAYER_MAX_HIT_POINTS
import at.smiech.cyanbat.util.PLAYER_SHOT_VARIANT
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.cyanbat.util.SHOT_HIT_POINTS
import at.smiech.cyanbat.util.SHOT_SPEED
import at.smiech.cyanbat.util.TRAIL_DRIFT_PER_TICK
import at.smiech.cyanbat.util.TRAIL_DURATION_SECONDS
import at.smiech.cyanbat.util.TRAIL_INTERVAL_SECONDS
import at.smiech.cyanbat.util.TRAIL_MIN_SCALE
import at.smiech.engine.EngineColors
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.AnimationComponent
import at.smiech.engine.ecs.AuraComponent
import at.smiech.engine.ecs.BackgroundComponent
import at.smiech.engine.ecs.BounceComponent
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.DamageComponent
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.FacesVelocityComponent
import at.smiech.engine.ecs.FloatingTextComponent
import at.smiech.engine.ecs.HealthBarComponent
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LifetimeComponent
import at.smiech.engine.ecs.PierceComponent
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.ProjectileStyleComponent
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
        world.addComponent(id, SpriteComponent(pixmap, srcWidth = BAT_FRAME_WIDTH))
        world.addComponent(
            id,
            AnimationComponent(BAT_FRAME_WIDTH, height.toInt(), BAT_FRAME_COUNT, BAT_FRAME_SECONDS)
        )
        world.addComponent(id, CollisionComponent(5f, CollisionGroup.PLAYER))
        world.addComponent(id, HealthComponent(PLAYER_MAX_HIT_POINTS))
        // Bars are for the two things a fight is decided between - the bat and the level's boss.
        // A bar over every passing enemy would bury the game behind them.
        world.addComponent(id, HealthBarComponent(HEALTH_BAR_HEIGHT, HEALTH_BAR_OFFSET_Y))
        world.addComponent(id, PlayerControlComponent())
        world.addComponent(id, WeaponComponent(shotIntervalSeconds))
        world.addComponent(id, TrailEmitterComponent(TRAIL_INTERVAL_SECONDS))
        // Dormant at level 1, which is where every run starts: an aura the player has not earned
        // yet draws nothing at all. GameScreen.syncAura is what wakes it up.
        world.addComponent(id, AuraComponent())
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
        world.addComponent(
            id,
            AnimationComponent(ENEMY_FRAME_WIDTH, height.toInt(), ENEMY_FRAME_COUNT, ENEMY_FRAME_SECONDS)
        )

        val movementType = when (type) {
            0 -> EnemyMovementType.SCOUT
            1 -> EnemyMovementType.SINE
            2 -> EnemyMovementType.ZIGZAG
            else -> EnemyMovementType.SCOUT
        }
        world.addComponent(id, EnemyBehaviorComponent(movementType, y))
        // So that anything which is given a gun later fires in its own color rather than the
        // player's; ordinary enemies carry no weapon today, and this costs them one component.
        world.addComponent(id, ProjectileStyleComponent(type + ENEMY_SHOT_VARIANT_OFFSET))

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
        world.addComponent(
            id,
            AnimationComponent(ENEMY_FRAME_WIDTH, pixmap.height, ENEMY_FRAME_COUNT, ENEMY_FRAME_SECONDS)
        )
        world.addComponent(id, EnemyBehaviorComponent(EnemyMovementType.BOSS, y, holdX = holdX))
        world.addComponent(
            id,
            ProjectileStyleComponent(BOSS_ENEMY_TYPE + ENEMY_SHOT_VARIANT_OFFSET)
        )
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

    /**
     * One tile of the scrolling cave, with its left edge at [x].
     *
     * The size is taken from the pixmap rather than passed in, and that is a fix rather than a
     * tidy-up. It used to be handed the framebuffer's size while `RenderSystem` drew the sprite at
     * the pixmap's - 480 against 838 - so the box the world moved and culled was not the picture
     * anybody saw. Tiles were spaced 480 apart while being drawn 838 wide, which put a hard
     * vertical seam through the cave every 240 ticks, sweeping across the screen as the join
     * between one copy's column 0 and the next one's column 480.
     */
    fun createBackground(x: Float, pixmap: Pixmap): EntityId {
        val id = world.createEntity()
        world.addComponent(
            id,
            TransformComponent(Rect.fromLTWH(x, 0f, pixmap.width.toFloat(), pixmap.height.toFloat()))
        )
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
        critical: Boolean = false,
        variant: Int = PLAYER_SHOT_VARIANT,
    ): EntityId {
        val radians = angleDegrees * PI_OVER_180
        val forward = if (isPlayer) SHOT_SPEED else -SHOT_SPEED

        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(
            id,
            VelocityComponent(Vector2(forward * cos(radians), SHOT_SPEED * sin(radians)))
        )
        // The artwork is a bullet with a point on it, so where it is going is the only thing its
        // shape means. Every shot gets this, the enemy's included: theirs travels left, and drawing
        // it pointing right was always wrong - it just had nothing to be compared against until
        // the bat's shots started coming back off walls.
        world.addComponent(id, FacesVelocityComponent())
        // Added only when the run has earned them, so an ordinary shot carries no bookkeeping it
        // will never use - and so the collision handler can tell a piercing shot by its component.
        if (pierce > 0) world.addComponent(id, PierceComponent(pierce))
        if (bounce > 0) world.addComponent(id, BounceComponent(bounce))
        world.addComponent(
            id,
            SpriteComponent(
                pixmap,
                baseSrcX = variant * SHOT_FRAME_WIDTH,
                srcWidth = SHOT_FRAME_WIDTH,
            )
        )
        world.addComponent(id, CollisionComponent(2f, if (isPlayer) CollisionGroup.PLAYER_PROJECTILE else CollisionGroup.ENEMY_PROJECTILE))
        world.addComponent(id, HealthComponent(SHOT_HIT_POINTS))
        world.addComponent(id, DamageComponent(damage, isCritical = critical))
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
    fun createDamageText(x: Float, y: Float, damage: Int, critical: Boolean = false): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 0f, 0f)))
        world.addComponent(id, VelocityComponent(Vector2(0f, -DAMAGE_TEXT_RISE_PER_TICK)))
        world.addComponent(
            id,
            FloatingTextComponent(
                text = damage.toString(),
                // A crit is read rather than glanced at, so it is bigger, red, and stays up
                // longer. Three changes rather than one because a number that is only larger
                // still gets lost in a screen of white numbers going up at the same time.
                fontSize = if (critical) CRITICAL_TEXT_FONT_SIZE else DAMAGE_TEXT_FONT_SIZE,
                color = if (critical) EngineColors.RED else EngineColors.WHITE,
                duration = if (critical) CRITICAL_TEXT_DURATION_SECONDS else DAMAGE_TEXT_DURATION_SECONDS,
            )
        )
        return id
    }

    /**
     * A blast centered on ([centerX], [centerY]).
     *
     * Centered rather than placed by its corner because the caller knows what died, not how big an
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
        world.addComponent(
            id,
            AnimationComponent(
                EXPLOSION_FRAME_WIDTH,
                pixmap.height,
                EXPLOSION_FRAME_COUNT,
                EXPLOSION_FRAME_SECONDS,
                isLooping = false,
            )
        )
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(50))
        return id
    }

    private companion object {
        /**
         * The bat's sheet: six frames of one wing-beat, laid out left to right.
         *
         * Six rather than the two it had, because two frames of a flap is a sprite blinking
         * between poses; a beat needs a downstroke and a fold to read as one. The interval is set
         * so the whole cycle still takes about the 0.4s the old pair did - the bat beats its wings
         * at the same rate, it just has the frames to show it now.
         */
        const val BAT_FRAME_COUNT = 6
        const val BAT_FRAME_SECONDS = 0.07f

        /**
         * The enemy sheet: three types, four frames of wingbeat each, laid out type by type.
         *
         * Four rather than the two it had, for the same reason the bat got six - and at an
         * interval that puts the cycle at the same ~0.4s, so the hostiles and the player beat
         * their wings at one rate instead of the enemies looking slowed down beside him.
         */
        const val ENEMY_FRAME_WIDTH = 32
        const val ENEMY_FRAME_COUNT = 4
        const val ENEMY_FRAME_SECONDS = 0.1f

        /**
         * The blast: eight frames of one fireball, square so it can expand in every direction.
         *
         * The sheet it replaced was five unrelated pictures at offsets measured off some larger
         * sheet, walked on a 25 pixel stride that landed on two empty slots - so a death played as
         * blob, nothing, star, nothing, rocks. The interval is a fifth of what it was, because the
         * old five frames took a second and a half: a blast that outlives the thing it killed reads
         * as a decal, not as an explosion.
         */
        const val EXPLOSION_FRAME_WIDTH = 32
        const val EXPLOSION_FRAME_COUNT = 8
        const val EXPLOSION_FRAME_SECONDS = 0.055f

        /**
         * The sheet strip each enemy type animates from.
         *
         * Computed rather than tabulated. The old sheet had its strips at 0, 67 and 137 - offsets
         * that were measured off the artwork rather than chosen - so the table and the image could
         * disagree and nothing would say so. The regenerated sheet is laid out on an exact stride,
         * which makes this arithmetic and the two impossible to drift apart.
         */
        fun srcXOf(type: Int): Int = type.coerceAtLeast(0) * ENEMY_FRAME_WIDTH * ENEMY_FRAME_COUNT

        /** The boss wears the third enemy's colors, the same ones the final wave escorts it in. */
        const val BOSS_ENEMY_TYPE = 2

        /** Degrees to radians, for the spread on a fanned shot. */
        const val PI_OVER_180 = 0.017453292f
    }
}
