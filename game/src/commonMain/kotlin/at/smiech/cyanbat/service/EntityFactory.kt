package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.BossPartComponent
import at.smiech.cyanbat.ecs.ColorwayComponent
import at.smiech.cyanbat.ecs.ContactWeapon
import at.smiech.cyanbat.ecs.ContactWeaponComponent
import at.smiech.cyanbat.ecs.EliteComponent
import at.smiech.cyanbat.ecs.ElitePalette
import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.OrbComponent
import at.smiech.cyanbat.ecs.OrbitSystem
import at.smiech.cyanbat.util.BAT_FRAME_WIDTH
import at.smiech.cyanbat.util.BAT_LIGHT_COLOR
import at.smiech.cyanbat.util.BAT_LIGHT_RADIUS
import at.smiech.cyanbat.util.BLAST_LIGHT_COLOR
import at.smiech.cyanbat.util.BLAST_LIGHT_MAX_RADIUS
import at.smiech.cyanbat.util.BLAST_LIGHT_RADIUS
import at.smiech.cyanbat.util.BLAST_LIGHT_STEP
import at.smiech.cyanbat.util.BURST_DRIFT
import at.smiech.cyanbat.util.CACO_IMP_LIGHT_COLOR
import at.smiech.cyanbat.util.CACO_IMP_LIGHT_RADIUS
import at.smiech.cyanbat.util.CACO_IMP_SMOULDER_INTENSITY
import at.smiech.cyanbat.util.CREATURE_SHINE
import at.smiech.cyanbat.util.CRITICAL_TEXT_DURATION_SECONDS
import at.smiech.cyanbat.util.CRITICAL_TEXT_FONT_SIZE
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.DAMAGE_TEXT_DURATION_SECONDS
import at.smiech.cyanbat.util.DAMAGE_TEXT_FONT_SIZE
import at.smiech.cyanbat.util.DAMAGE_TEXT_RISE_PER_TICK
import at.smiech.cyanbat.util.DESTRUCTIBLE_HIT_POINTS
import at.smiech.cyanbat.util.ELITE_AURA_INTENSITY
import at.smiech.cyanbat.util.ELITE_AURA_TIER
import at.smiech.cyanbat.util.ELITE_LIGHT_RADIUS
import at.smiech.cyanbat.util.ENEMY_IMPACT_LIGHT_RADIUS
import at.smiech.cyanbat.util.ENEMY_SHOT_LIGHT_RADIUS
import at.smiech.cyanbat.util.ENEMY_SHOT_VARIANT_OFFSET
import at.smiech.cyanbat.util.HEALTH_BAR_HEIGHT
import at.smiech.cyanbat.util.HEALTH_BAR_OFFSET_Y
import at.smiech.cyanbat.util.IMPACT_FRAME
import at.smiech.cyanbat.util.IMPACT_FRAME_COUNT
import at.smiech.cyanbat.util.IMPACT_FRAME_SECONDS
import at.smiech.cyanbat.util.IMPACT_LIGHT_PALENESS
import at.smiech.cyanbat.util.IMPACT_LIGHT_SECONDS
import at.smiech.cyanbat.util.MOTH_QUEEN_COLLISION_TOLERANCE
import at.smiech.cyanbat.util.MOTH_QUEEN_FRAME_COUNT
import at.smiech.cyanbat.util.MOTH_QUEEN_FRAME_SECONDS
import at.smiech.cyanbat.util.MOTH_QUEEN_FRAME_WIDTH
import at.smiech.cyanbat.util.MOTH_QUEEN_SHOT_VARIANT
import at.smiech.cyanbat.util.NAGA_FRAME
import at.smiech.cyanbat.util.NAGA_HEAD_FRAMES
import at.smiech.cyanbat.util.NAGA_HEAD_FRAME_SECONDS
import at.smiech.cyanbat.util.NAGA_PLATE_SHARE
import at.smiech.cyanbat.util.NAGA_SHOT_VARIANT
import at.smiech.cyanbat.util.NAGA_SPLASH_FRAME
import at.smiech.cyanbat.util.NAGA_SPLASH_FRAMES
import at.smiech.cyanbat.util.NAGA_SPLASH_FRAME_SECONDS
import at.smiech.cyanbat.util.ORB_COLLISION_TOLERANCE
import at.smiech.cyanbat.util.ORB_FRAME
import at.smiech.cyanbat.util.ORB_FRAME_COUNT
import at.smiech.cyanbat.util.ORB_FRAME_SECONDS
import at.smiech.cyanbat.util.PLAYER_IMPACT_LIGHT_RADIUS
import at.smiech.cyanbat.util.PLAYER_MAX_HIT_POINTS
import at.smiech.cyanbat.util.PLAYER_SHOT_LIGHT_RADIUS
import at.smiech.cyanbat.util.PLAYER_SHOT_VARIANT
import at.smiech.cyanbat.util.ROCK_SHINE
import at.smiech.cyanbat.util.SAND_WYRM_FRAME
import at.smiech.cyanbat.util.SAND_WYRM_HEAD_FRAMES
import at.smiech.cyanbat.util.SAND_WYRM_HEAD_FRAME_SECONDS
import at.smiech.cyanbat.util.SAND_WYRM_PLATE_SHARE
import at.smiech.cyanbat.util.SAND_WYRM_PLUME_FRAME
import at.smiech.cyanbat.util.SAND_WYRM_PLUME_FRAMES
import at.smiech.cyanbat.util.SAND_WYRM_PLUME_FRAME_SECONDS
import at.smiech.cyanbat.util.SAND_WYRM_SHOT_VARIANT
import at.smiech.cyanbat.util.SHIELD_REGROWTH_DELAY_SECONDS
import at.smiech.cyanbat.util.SHOT_BODY_COLORS
import at.smiech.cyanbat.util.SHOT_FRAME_COUNT
import at.smiech.cyanbat.util.SHOT_FRAME_SECONDS
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.cyanbat.util.SHOT_HIT_POINTS
import at.smiech.cyanbat.util.SHOT_LIGHT_INTENSITY
import at.smiech.cyanbat.util.SHOT_LIGHT_PALENESS
import at.smiech.cyanbat.util.SHOT_SPEED
import at.smiech.cyanbat.util.TRAIL_DRIFT_PER_TICK
import at.smiech.cyanbat.util.TRAIL_DURATION_SECONDS
import at.smiech.cyanbat.util.TRAIL_INTERVAL_SECONDS
import at.smiech.cyanbat.util.TRAIL_MIN_SCALE
import at.smiech.cyanbat.util.WAKE_COLOR
import at.smiech.cyanbat.util.WAKE_CORE_COLOR
import at.smiech.cyanbat.util.WAKE_MIN_SCALE
import at.smiech.cyanbat.util.WOUND_MARKS
import at.smiech.cyanbat.util.WOUND_ROWS
import at.smiech.engine.EngineColors
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.AnimationComponent
import at.smiech.engine.ecs.AuraComponent
import at.smiech.engine.ecs.BackgroundComponent
import at.smiech.engine.ecs.BounceComponent
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.CrossfadeComponent
import at.smiech.engine.ecs.DamageComponent
import at.smiech.engine.ecs.DitherComponent
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.FacesVelocityComponent
import at.smiech.engine.ecs.FloatingTextComponent
import at.smiech.engine.ecs.HealthBarComponent
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LifetimeComponent
import at.smiech.engine.ecs.LightComponent
import at.smiech.engine.ecs.OccluderComponent
import at.smiech.engine.ecs.PaceComponent
import at.smiech.engine.ecs.PierceComponent
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.ProjectileStyleComponent
import at.smiech.engine.ecs.ShieldComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TrailComponent
import at.smiech.engine.ecs.TrailEmitterComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.VelocityComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.ecs.WoundComponent
import at.smiech.engine.ecs.ZIndexComponent
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Builds every entity a run is made of.
 *
 * @param lit whether the stage is flown in the dark, where light sources carry a [LightComponent]
 *   and what blocks light an [OccluderComponent]. In daylight neither is added.
 */
class EntityFactory(val world: World, private val lit: Boolean = false) {

    /** The bat, at ([x], [y]), drawn from [pixmap]'s rows of unhurt and wounded wingbeats. */
    fun createBat(
        x: Float,
        y: Float,
        width: Float,
        pixmap: Pixmap,
        shotIntervalSeconds: Float,
    ): EntityId {
        val height = pixmap.height / WOUND_ROWS
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height.toFloat())))
        world.addComponent(id, VelocityComponent(Vector2.Zero))
        world.addComponent(
            id,
            SpriteComponent(pixmap, srcWidth = BAT_FRAME_WIDTH, srcHeight = height)
        )
        world.addComponent(
            id,
            AnimationComponent(BAT_FRAME_WIDTH, height, BAT_FRAME_COUNT, BAT_FRAME_SECONDS)
        )
        world.addComponent(id, WoundComponent(height, WOUND_MARKS))
        world.addComponent(id, CollisionComponent(5f, CollisionGroup.PLAYER))
        world.addComponent(id, HealthComponent(PLAYER_MAX_HIT_POINTS))
        // Only the bat and the boss carry health bars; a bar over every enemy would
        // clutter the screen.
        world.addComponent(id, HealthBarComponent(HEALTH_BAR_HEIGHT, HEALTH_BAR_OFFSET_Y))
        world.addComponent(id, PlayerControlComponent())
        // Whole until something keeps it from harm; see InvulnerabilitySystem.
        world.addComponent(id, DitherComponent())
        world.addComponent(id, WeaponComponent(shotIntervalSeconds))
        world.addComponent(id, TrailEmitterComponent(TRAIL_INTERVAL_SECONDS))
        // Dormant at level 1, where every run starts; GameScreen.syncAura wakes it up.
        world.addComponent(id, AuraComponent())
        // What the player sees the dark by. The bat casts no shadow: it is the light.
        if (lit) world.addComponent(id, LightComponent(BAT_LIGHT_COLOR, BAT_LIGHT_RADIUS))
        world.addComponent(id, LifetimeComponent(false))
        world.addComponent(id, ZIndexComponent(20))
        return id
    }

    /**
     * One enemy of [species], at the strength its wave calls for.
     *
     * [hitPoints], [damage] and [speedMultiplier] come from the wave, which is how a stage ramps.
     * They are fixed at spawn, so enemies on screen keep their strength when a wave turns over.
     *
     * @param height one picture's worth of [pixmap], which stacks each species unhurt, wounded
     *   and battered; see [WoundComponent].
     * @param laneY the path a group flies around, for the species that fly in one. A swarm's
     *   members all share it and each sits [offsetY] from it; a loner's lane is where it spawned.
     * @param phase a per-member offset into its pattern, so a swarm does not buzz in unison.
     * @param holdX where a hovering enemy stops, or a diving one commits.
     * @param shieldPoints a shield bubble to spawn behind, or zero for none.
     * @param shieldRegrowth the fraction of that shield that grows back per second once left alone;
     *   see [EnemySpecies.shieldRegrowth].
     * @param gun what it fires, or null for an enemy that only rams.
     * @param firstShotDelay how long an armed enemy waits before its first volley, on top of its
     *   interval, so a formation spawned on one tick does not fire as one.
     * @param elite the palette it wears as an elite, or null. Its extra health and faster gun are
     *   already in what it is handed; this adds the glow, the shot color and the payout marker.
     */
    fun createEnemy(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        pixmap: Pixmap,
        species: EnemySpecies,
        hitPoints: Int = DESTRUCTIBLE_HIT_POINTS,
        damage: Int = DAMAGE_PER_HIT,
        speedMultiplier: Float = 1f,
        laneY: Float = y,
        offsetY: Float = 0f,
        phase: Float = 0f,
        holdX: Float = 0f,
        shieldPoints: Int = 0,
        shieldRegrowth: Float = 0f,
        gun: EnemyGun? = null,
        firstShotDelay: Float = 0f,
        elite: ElitePalette? = null,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))

        val speedX = species.speedX * speedMultiplier
        world.addComponent(id, VelocityComponent(Vector2(speedX, 0f)))

        world.addComponent(
            id,
            SpriteComponent(
                pixmap,
                baseSrcX = srcXOf(species.strip),
                srcWidth = ENEMY_FRAME_WIDTH,
                srcHeight = height.toInt(),
            )
        )
        world.addComponent(
            id,
            AnimationComponent(
                ENEMY_FRAME_WIDTH,
                height.toInt(),
                ENEMY_FRAME_COUNT,
                ENEMY_FRAME_SECONDS
            )
        )
        world.addComponent(id, WoundComponent(height.toInt(), WOUND_MARKS))
        // Full pace until wounded; GameScreen slows it from there. Only ordinary enemies carry one,
        // so wounds never slow a boss.
        world.addComponent(id, PaceComponent())

        world.addComponent(
            id,
            EnemyBehaviorComponent(
                species.movement,
                initialY = laneY,
                holdX = holdX,
                baseSpeedX = speedX,
                offsetY = offsetY,
                phase = phase,
            )
        )
        // Its shots are drawn in its own color, or an elite's in the color of its glow, so whose
        // fire it is can be read.
        world.addComponent(id, ProjectileStyleComponent(elite?.shotVariant ?: species.shotVariant))
        if (elite != null) {
            world.addComponent(id, EliteComponent(elite))
            // A fixed glow, rather than one that grows like the bat's.
            world.addComponent(
                id,
                AuraComponent(
                    intensity = ELITE_AURA_INTENSITY,
                    tier = ELITE_AURA_TIER,
                    colors = elite.aura
                ),
            )
            // In the dark the glow is also a light, so it is seen coming from across the cave.
            if (lit) world.addComponent(id, LightComponent(elite.aura.rim, ELITE_LIGHT_RADIUS))
        }
        if (lit) world.addComponent(id, OccluderComponent(CREATURE_SHINE))
        if (gun != null) {
            world.addComponent(
                id,
                WeaponComponent(gun.interval, timeSinceLastShot = -firstShotDelay)
            )
            world.addComponent(id, GunComponent(gun.volleys))
        }
        if (shieldPoints > 0) {
            world.addComponent(
                id,
                ShieldComponent(
                    shieldPoints,
                    regenPerSecond = shieldPoints * shieldRegrowth,
                    regenDelay = SHIELD_REGROWTH_DELAY_SECONDS,
                ),
            )
        }
        // Drawn facing left like every hostile, except the one that comes from behind, and turned
        // from there to point along its arc.
        if (species.facesHeading) {
            val artwork =
                if (species.drawnFacingRight) RIGHT_FACING_ARTWORK_DEGREES else HOSTILE_ARTWORK_DEGREES
            world.addComponent(id, FacesVelocityComponent(artwork))
        }

        world.addComponent(id, CollisionComponent(species.collisionTolerance, CollisionGroup.ENEMY))
        world.addComponent(id, HealthComponent(hitPoints))
        world.addComponent(id, DamageComponent(damage))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(10))
        return id
    }

    /**
     * The cave's boss, the Caco Imp: the sheet's last enemy drawn [scale] times over, with a
     * fight's worth of health and a gun that [CacoImpBrain] rearms as the fight goes on.
     *
     * Every boss, built through [createBossEntity], differs from [createEnemy] deliberately: it is
     * never culled for leaving the frame, so it cannot drift out of the stage; it carries a health
     * bar, as only the bat otherwise does; it holds station rather than closing; and it carries no
     * [PaceComponent], so wounds never slow it.
     *
     * In the dark it is alight, so the fight can be read across the cave; its brain
     * dims, douses and relights it.
     *
     * @param bar where its health bar is pinned, since a bar hanging under it would show where it
     *   prowls in the dark.
     */
    fun createBoss(
        x: Float,
        y: Float,
        holdX: Float,
        pixmap: Pixmap,
        scale: Float,
        hitPoints: Int,
        damage: Int,
        gun: EnemyGun,
        bar: Rect,
    ): EntityId = createBossEntity(
        x, y, holdX, pixmap, hitPoints, damage, gun.interval,
        frameWidth = ENEMY_FRAME_WIDTH,
        frameHeight = pixmap.height / WOUND_ROWS,
        frameCount = ENEMY_FRAME_COUNT,
        frameSeconds = ENEMY_FRAME_SECONDS,
        baseSrcX = srcXOf(BOSS_ENEMY_TYPE),
        scale = scale,
        movement = EnemyMovementType.BOSS,
        shotVariant = BOSS_ENEMY_TYPE + ENEMY_SHOT_VARIANT_OFFSET,
        // Scaled with the sprite, so the box sits in from its edges in the same proportion.
        collisionTolerance = 5f * scale,
    ).also { id ->
        world.addComponent(id, GunComponent(gun.volleys))
        world.addComponent(id, HealthBarComponent(pinnedTo = bar))
        if (lit) {
            world.addComponent(
                id,
                LightComponent(
                    CACO_IMP_LIGHT_COLOR,
                    CACO_IMP_LIGHT_RADIUS,
                    CACO_IMP_SMOULDER_INTENSITY
                ),
            )
        }
    }

    /**
     * The jungle's boss, the Moth Queen, drawn at its own size from its own sheet. Built like every
     * boss (see [createBoss]), but flying a figure eight, with a gun [MothQueenBrain] rearms as the
     * fight goes on.
     */
    fun createMothQueen(
        x: Float,
        y: Float,
        holdX: Float,
        pixmap: Pixmap,
        hitPoints: Int,
        damage: Int,
        gun: EnemyGun,
    ): EntityId {
        val id = createBossEntity(
            x, y, holdX, pixmap, hitPoints, damage, gun.interval,
            frameWidth = MOTH_QUEEN_FRAME_WIDTH,
            frameHeight = pixmap.height / WOUND_ROWS,
            frameCount = MOTH_QUEEN_FRAME_COUNT,
            frameSeconds = MOTH_QUEEN_FRAME_SECONDS,
            baseSrcX = 0,
            scale = 1f,
            movement = EnemyMovementType.BOSS_FIGURE_EIGHT,
            shotVariant = MOTH_QUEEN_SHOT_VARIANT,
            // Wide, because most of her frame is wing and its corners are empty.
            collisionTolerance = MOTH_QUEEN_COLLISION_TOLERANCE,
        )
        world.addComponent(id, GunComponent(gun.volleys))
        // Down until she raises it, but present, so the brain only has to raise it.
        world.addComponent(id, ShieldComponent(0))
        return id
    }

    /**
     * The desert's boss, the Sand Wyrm: a head and nine armored plates, each its own entity so it
     * can be run into, shot and flashed anywhere along the body.
     *
     * The head carries the health, the pinned health bar, the gun and the leap it flies by. A shot
     * into a plate lands on the head at [SAND_WYRM_PLATE_SHARE], so the head is the place to aim.
     * [SandWyrmBrain] places the plates every tick along the head's path, so nothing else may move
     * or remove them and none is culled for leaving the frame. Every part is a [BossPartComponent].
     *
     * The parts are created tail first, so each is drawn over the one behind it. Only the head
     * carries a [WoundComponent]; the brain draws the plates from its row.
     *
     * @param bodyDamage what a plate deals on contact; less than the head's, see
     *   [at.smiech.cyanbat.util.SAND_WYRM_BODY_DAMAGE].
     * @param bar where the health bar is pinned.
     * @return the head, then the body from the neck back to the tail.
     */
    fun createSandWyrm(
        x: Float,
        y: Float,
        pixmap: Pixmap,
        hitPoints: Int,
        damage: Int,
        bodyDamage: Int,
        gun: EnemyGun,
        bar: Rect,
    ): List<EntityId> {
        val frame = SAND_WYRM_FRAME.toFloat()
        val body = SAND_WYRM_BODY.reversed().map { (sheetFrame, tolerance) ->
            val id = world.createEntity()
            world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, frame, frame)))
            world.addComponent(id, VelocityComponent(Vector2.Zero))
            // No wound of its own, as no health of its own: the brain draws it from the head's row.
            world.addComponent(
                id,
                SpriteComponent(
                    pixmap,
                    baseSrcX = sheetFrame * SAND_WYRM_FRAME,
                    srcWidth = SAND_WYRM_FRAME,
                    srcHeight = SAND_WYRM_FRAME,
                ),
            )
            world.addComponent(id, CollisionComponent(tolerance, CollisionGroup.ENEMY))
            world.addComponent(id, DamageComponent(bodyDamage))
            world.addComponent(id, BossPartComponent(share = SAND_WYRM_PLATE_SHARE))
            if (lit) world.addComponent(id, OccluderComponent(CREATURE_SHINE))
            world.addComponent(id, LifetimeComponent(false))
            world.addComponent(id, ZIndexComponent(12))
            id
        }.reversed()

        val head = world.createEntity()
        world.addComponent(head, TransformComponent(Rect.fromLTWH(x, y, frame, frame)))
        world.addComponent(head, VelocityComponent(Vector2.Zero))
        world.addComponent(
            head,
            SpriteComponent(pixmap, srcWidth = SAND_WYRM_FRAME, srcHeight = SAND_WYRM_FRAME),
        )
        world.addComponent(
            head,
            AnimationComponent(
                SAND_WYRM_FRAME,
                SAND_WYRM_FRAME,
                SAND_WYRM_HEAD_FRAMES,
                SAND_WYRM_HEAD_FRAME_SECONDS
            ),
        )
        world.addComponent(head, WoundComponent(SAND_WYRM_FRAME, WOUND_MARKS))
        world.addComponent(head, FacesVelocityComponent(HOSTILE_ARTWORK_DEGREES))
        // At rest until the brain throws it: a leap whose station is unreachable.
        world.addComponent(
            head,
            EnemyBehaviorComponent(EnemyMovementType.LEAP, y, holdX = -Float.MAX_VALUE)
        )
        world.addComponent(head, ProjectileStyleComponent(SAND_WYRM_SHOT_VARIANT))
        world.addComponent(head, CollisionComponent(SAND_WYRM_HEAD_TOLERANCE, CollisionGroup.ENEMY))
        world.addComponent(head, HealthComponent(hitPoints))
        world.addComponent(head, DamageComponent(damage))
        world.addComponent(head, HealthBarComponent(pinnedTo = bar))
        world.addComponent(head, BossPartComponent())
        world.addComponent(head, WeaponComponent(gun.interval))
        world.addComponent(head, GunComponent(gun.volleys))
        if (lit) world.addComponent(head, OccluderComponent(CREATURE_SHINE))
        world.addComponent(head, LifetimeComponent(false))
        world.addComponent(head, ZIndexComponent(13))
        return listOf(head) + body
    }

    /**
     * Sand thrown up out of the ground, standing on [bottom] and centered on [centerX]: the tell
     * before the Sand Wyrm breaches, and the breach itself.
     *
     * A one-shot effect like a blast, but it stays put rather than drifting with the scenery, since
     * the wyrm comes up where the sand boiled.
     */
    fun createSandPlume(
        centerX: Float,
        bottom: Float,
        pixmap: Pixmap,
        scale: Float = 1f
    ): EntityId {
        val size = SAND_WYRM_FRAME * scale
        val id = world.createEntity()
        world.addComponent(
            id,
            TransformComponent(Rect.fromLTWH(centerX - size / 2f, bottom - size, size, size))
        )
        world.addComponent(id, VelocityComponent(Vector2.Zero))
        world.addComponent(
            id,
            SpriteComponent(
                pixmap,
                baseSrcX = SAND_WYRM_PLUME_FRAME * SAND_WYRM_FRAME,
                srcWidth = SAND_WYRM_FRAME,
                srcHeight = SAND_WYRM_FRAME,
                scale = scale,
            ),
        )
        world.addComponent(
            id,
            AnimationComponent(
                SAND_WYRM_FRAME,
                SAND_WYRM_FRAME,
                SAND_WYRM_PLUME_FRAMES,
                SAND_WYRM_PLUME_FRAME_SECONDS,
                isLooping = false,
            ),
        )
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(50))
        return id
    }

    /**
     * The lagoon's boss, the Naga: a hooded head and twelve body parts, each its own entity, built
     * like the Sand Wyrm and moved by its own brain.
     *
     * The head carries the health, the pinned health bar, the gun, and a shield its hood raises
     * late in the fight. A shot into a part lands on the head at [NAGA_PLATE_SHARE]. [NagaBrain]
     * places every part every tick, so nothing else may move or remove them and none is culled for
     * leaving the frame. Every part is a [BossPartComponent].
     *
     * The parts are created tail first, so each is drawn over the one behind it. Only the head
     * carries a [WoundComponent]; the brain draws the parts from its row.
     *
     * @param bodyDamage what a part of the body deals on contact; less than the head's.
     * @param bar where the health bar is pinned.
     * @return the head, then the body from the neck back to the tail.
     */
    fun createNaga(
        x: Float,
        y: Float,
        pixmap: Pixmap,
        hitPoints: Int,
        damage: Int,
        bodyDamage: Int,
        gun: EnemyGun,
        bar: Rect,
    ): List<EntityId> {
        val frame = NAGA_FRAME.toFloat()
        val body = NAGA_BODY.reversed().map { (sheetFrame, tolerance) ->
            val id = world.createEntity()
            world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, frame, frame)))
            world.addComponent(id, VelocityComponent(Vector2.Zero))
            world.addComponent(
                id,
                SpriteComponent(
                    pixmap,
                    baseSrcX = sheetFrame * NAGA_FRAME,
                    srcWidth = NAGA_FRAME,
                    srcHeight = NAGA_FRAME
                ),
            )
            world.addComponent(id, CollisionComponent(tolerance, CollisionGroup.ENEMY))
            world.addComponent(id, DamageComponent(bodyDamage))
            world.addComponent(id, BossPartComponent(share = NAGA_PLATE_SHARE))
            if (lit) world.addComponent(id, OccluderComponent(CREATURE_SHINE))
            world.addComponent(id, LifetimeComponent(false))
            world.addComponent(id, ZIndexComponent(12))
            id
        }.reversed()

        val head = world.createEntity()
        world.addComponent(head, TransformComponent(Rect.fromLTWH(x, y, frame, frame)))
        world.addComponent(head, VelocityComponent(Vector2.Zero))
        world.addComponent(
            head,
            SpriteComponent(pixmap, srcWidth = NAGA_FRAME, srcHeight = NAGA_FRAME)
        )
        world.addComponent(
            head,
            AnimationComponent(NAGA_FRAME, NAGA_FRAME, NAGA_HEAD_FRAMES, NAGA_HEAD_FRAME_SECONDS),
        )
        world.addComponent(head, WoundComponent(NAGA_FRAME, WOUND_MARKS))
        world.addComponent(head, ProjectileStyleComponent(NAGA_SHOT_VARIANT))
        world.addComponent(head, CollisionComponent(NAGA_HEAD_TOLERANCE, CollisionGroup.ENEMY))
        world.addComponent(head, HealthComponent(hitPoints))
        world.addComponent(head, DamageComponent(damage))
        world.addComponent(head, HealthBarComponent(pinnedTo = bar))
        world.addComponent(head, BossPartComponent())
        world.addComponent(head, WeaponComponent(gun.interval))
        world.addComponent(head, GunComponent(gun.volleys))
        // Down until its hood raises it, but present, so the brain only has to raise it.
        world.addComponent(head, ShieldComponent(0))
        if (lit) world.addComponent(head, OccluderComponent(CREATURE_SHINE))
        world.addComponent(head, LifetimeComponent(false))
        world.addComponent(head, ZIndexComponent(13))
        return listOf(head) + body
    }

    /**
     * Water thrown up out of the sea, standing on [bottom] and centered on [centerX]: the boiling
     * before the Naga rises, and the burst as it does. Stays put, like the Sand Wyrm's sand.
     */
    fun createSplash(centerX: Float, bottom: Float, pixmap: Pixmap, scale: Float = 1f): EntityId {
        val size = NAGA_FRAME * scale
        val id = world.createEntity()
        world.addComponent(
            id,
            TransformComponent(Rect.fromLTWH(centerX - size / 2f, bottom - size, size, size))
        )
        world.addComponent(id, VelocityComponent(Vector2.Zero))
        world.addComponent(
            id,
            SpriteComponent(
                pixmap,
                baseSrcX = NAGA_SPLASH_FRAME * NAGA_FRAME,
                srcWidth = NAGA_FRAME,
                srcHeight = NAGA_FRAME,
                scale = scale,
            ),
        )
        world.addComponent(
            id,
            AnimationComponent(
                NAGA_FRAME,
                NAGA_FRAME,
                NAGA_SPLASH_FRAMES,
                NAGA_SPLASH_FRAME_SECONDS,
                isLooping = false
            ),
        )
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(50))
        return id
    }

    /** What every boss shares; see [createBoss]. */
    private fun createBossEntity(
        x: Float,
        y: Float,
        holdX: Float,
        pixmap: Pixmap,
        hitPoints: Int,
        damage: Int,
        shotIntervalSeconds: Float,
        frameWidth: Int,
        frameHeight: Int,
        frameCount: Int,
        frameSeconds: Float,
        baseSrcX: Int,
        scale: Float,
        movement: EnemyMovementType,
        shotVariant: Int,
        collisionTolerance: Float,
    ): EntityId {
        val width = frameWidth * scale
        val height = frameHeight * scale

        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2.Zero))
        world.addComponent(
            id,
            SpriteComponent(
                pixmap,
                baseSrcX = baseSrcX,
                srcWidth = frameWidth,
                srcHeight = frameHeight,
                scale = scale,
            )
        )
        world.addComponent(
            id,
            AnimationComponent(frameWidth, frameHeight, frameCount, frameSeconds)
        )
        // At the same marks a boss with phases changes phase; see WOUND_MARKS.
        world.addComponent(id, WoundComponent(frameHeight, WOUND_MARKS))
        world.addComponent(id, EnemyBehaviorComponent(movement, y, holdX = holdX))
        world.addComponent(id, ProjectileStyleComponent(shotVariant))
        world.addComponent(id, CollisionComponent(collisionTolerance, CollisionGroup.ENEMY))
        world.addComponent(id, HealthComponent(hitPoints))
        world.addComponent(id, DamageComponent(damage))
        world.addComponent(id, HealthBarComponent(HEALTH_BAR_HEIGHT * scale, HEALTH_BAR_OFFSET_Y))
        world.addComponent(id, WeaponComponent(shotIntervalSeconds))
        if (lit) world.addComponent(id, OccluderComponent(CREATURE_SHINE))
        world.addComponent(id, LifetimeComponent(false))
        world.addComponent(id, ZIndexComponent(12))
        return id
    }

    /**
     * One tile of a scrolling background strip, with its left edge at [x], sized from [pixmap] so
     * the box the world moves and culls matches the picture drawn.
     */
    fun createBackground(x: Float, pixmap: Pixmap): EntityId {
        val id = world.createEntity()
        world.addComponent(
            id,
            TransformComponent(
                Rect.fromLTWH(
                    x,
                    0f,
                    pixmap.width.toFloat(),
                    pixmap.height.toFloat()
                )
            )
        )
        world.addComponent(id, VelocityComponent(Vector2(-2f, 0f)))
        world.addComponent(id, SpriteComponent(pixmap))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, BackgroundComponent())
        world.addComponent(id, ZIndexComponent(-100))
        return id
    }

    /**
     * An obstacle at ([x], [y]), scrolling in with the scenery; destructible.
     *
     * @param keyframed whether [pixmap] holds the obstacle once per time of day, a row of [height]
     *   each, for a stage whose light changes; [at.smiech.cyanbat.ecs.SkySystem] picks the rows.
     */
    fun createObstacle(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        pixmap: Pixmap,
        keyframed: Boolean = false,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2(-1f, 0f)))
        world.addComponent(id, SpriteComponent(pixmap, srcHeight = height.toInt()))
        if (keyframed) world.addComponent(id, CrossfadeComponent())
        if (lit) world.addComponent(id, OccluderComponent(ROCK_SHINE))
        world.addComponent(id, CollisionComponent(5f, CollisionGroup.OBSTACLE))
        world.addComponent(id, HealthComponent(DESTRUCTIBLE_HIT_POINTS))
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(5))
        return id
    }

    /**
     * A shot, the bat's or an enemy's, flying away from its shooter.
     *
     * @param angleDegrees how far off straight the shot flies, positive downward. A
     *   fanned shot keeps the full [speed] along its heading, so the outer shots of
     *   a spread keep pace with the middle.
     * @param speed along its heading, in framebuffer pixels per tick. The bat's are [SHOT_SPEED];
     *   enemy fire is slower, so it can be seen coming.
     * @param variant the colorway of `shot.png` it is drawn in.
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
        speed: Float = SHOT_SPEED,
    ): EntityId {
        val radians = angleDegrees * PI_OVER_180
        val forward = if (isPlayer) speed else -speed

        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(
            id,
            VelocityComponent(Vector2(forward * cos(radians), speed * sin(radians)))
        )
        // The artwork is a pointed bolt, so every shot, the enemy's included, turns
        // to point where it is going.
        world.addComponent(id, FacesVelocityComponent())
        // Added only when earned, so the collision handler can tell a piercing shot
        // by its component.
        if (pierce > 0) world.addComponent(id, PierceComponent(pierce))
        if (bounce > 0) world.addComponent(id, BounceComponent(bounce))
        world.addComponent(
            id,
            SpriteComponent(
                pixmap,
                baseSrcX = variant * SHOT_FRAME_WIDTH * SHOT_FRAME_COUNT,
                srcWidth = SHOT_FRAME_WIDTH,
            )
        )
        // The bolt burns as it flies. A volley's shots start on the same frame, so a spread or ring
        // throbs as one.
        world.addComponent(
            id,
            AnimationComponent(
                SHOT_FRAME_WIDTH,
                pixmap.height,
                SHOT_FRAME_COUNT,
                SHOT_FRAME_SECONDS
            ),
        )
        world.addComponent(id, ColorwayComponent(variant))
        world.addComponent(
            id,
            CollisionComponent(
                2f,
                if (isPlayer) CollisionGroup.PLAYER_PROJECTILE else CollisionGroup.ENEMY_PROJECTILE
            )
        )
        world.addComponent(id, HealthComponent(SHOT_HIT_POINTS))
        world.addComponent(id, DamageComponent(damage, isCritical = critical))
        if (lit) {
            // A bolt glows the color of its body, so its light shows whose it is.
            val color = EngineColors.lerp(
                SHOT_BODY_COLORS[variant],
                EngineColors.WHITE,
                SHOT_LIGHT_PALENESS
            )
            val radius = if (isPlayer) PLAYER_SHOT_LIGHT_RADIUS else ENEMY_SHOT_LIGHT_RADIUS
            world.addComponent(id, LightComponent(color, radius, SHOT_LIGHT_INTENSITY))
        }
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(15))
        return id
    }

    /**
     * The hit a shot leaves where it struck, centered on ([centerX], [centerY]): a spark in the
     * shot's colorway, played once, drifting with the scenery like a blast.
     *
     * In the dark it is also a light, fading over [IMPACT_LIGHT_SECONDS], wider and paler than the
     * shot's own, so what was struck and its surroundings light up.
     *
     * @param variant the shot's colorway; see [ColorwayComponent].
     * @param isPlayer whether it was one of the bat's shots, whose hits light further.
     */
    fun createImpact(
        centerX: Float,
        centerY: Float,
        pixmap: Pixmap,
        variant: Int,
        isPlayer: Boolean
    ): EntityId =
        createBurst(
            centerX,
            centerY,
            pixmap,
            IMPACT_FRAME,
            IMPACT_FRAME_COUNT,
            IMPACT_FRAME_SECONDS,
            scale = 1f,
            baseSrcX = variant * IMPACT_FRAME * IMPACT_FRAME_COUNT,
        ).also { id ->
            if (lit) {
                val color = EngineColors.lerp(
                    SHOT_BODY_COLORS[variant],
                    EngineColors.WHITE,
                    IMPACT_LIGHT_PALENESS
                )
                val radius = if (isPlayer) PLAYER_IMPACT_LIGHT_RADIUS else ENEMY_IMPACT_LIGHT_RADIUS
                world.addComponent(
                    id,
                    LightComponent(color, radius, fadeSeconds = IMPACT_LIGHT_SECONDS)
                )
            }
        }

    /**
     * A flash of light alone, centered on ([centerX], [centerY]) in [color], reaching [radius]: it
     * fades over [seconds] and is then removed, drifting with the scenery meanwhile. The frost beam
     * leaves one on everything it freezes.
     */
    fun createFlash(
        centerX: Float,
        centerY: Float,
        color: Int,
        radius: Int,
        seconds: Float
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(centerX, centerY, 0f, 0f)))
        world.addComponent(id, VelocityComponent(Vector2(BURST_DRIFT, 0f)))
        world.addComponent(
            id,
            LightComponent(color, radius, fadeSeconds = seconds, removeWhenFaded = true)
        )
        return id
    }

    /**
     * One segment of the bat's wake, shed just behind it by [at.smiech.engine.ecs.TrailSystem].
     *
     * It drifts with the scenery rather than the bat, so the wake marks where the bat has been.
     *
     * @param seconds how long it lasts, and so how far behind the bat the wake reaches.
     * @param charged whether Charged Trail has made the wake a weapon: it then shocks enemies it
     *   touches as a [ContactWeapon.WAKE], with a bright core to show it, and keeps more of its
     *   size as it fades so its harmful extent stays visible.
     */
    fun createTrail(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        seconds: Float = TRAIL_DURATION_SECONDS,
        charged: Boolean = false,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, width, height)))
        world.addComponent(id, VelocityComponent(Vector2(TRAIL_DRIFT_PER_TICK, 0f)))
        if (charged) {
            world.addComponent(
                id,
                TrailComponent(WAKE_COLOR, seconds, WAKE_MIN_SCALE, coreColor = WAKE_CORE_COLOR)
            )
            world.addComponent(id, CollisionComponent(0f, CollisionGroup.PLAYER_CONTACT))
            world.addComponent(id, ContactWeaponComponent(ContactWeapon.WAKE))
        } else {
            world.addComponent(id, TrailComponent(EngineColors.CYAN, seconds, TRAIL_MIN_SCALE))
        }
        world.addComponent(id, LifetimeComponent(true))
        return id
    }

    /**
     * One of the orbs circling the bat, centered on ([centerX], [centerY]) until [OrbitSystem] carries
     * it round.
     *
     * Never culled for leaving the frame, since the bat can fly along an edge with
     * half its ring past it. It has no health, and its collision group meets only
     * enemies, which it hurts as a [ContactWeapon.ORB]. Drawn under the bat, with
     * no light of its own: it is always inside the bat's.
     *
     * @param offset its place on the ring; see [OrbComponent] and [OrbitSystem.shareOf].
     */
    fun createOrb(centerX: Float, centerY: Float, pixmap: Pixmap, offset: Float): EntityId {
        val size = ORB_FRAME.toFloat()
        val id = world.createEntity()
        world.addComponent(
            id,
            TransformComponent(Rect.fromLTWH(centerX - size / 2f, centerY - size / 2f, size, size))
        )
        world.addComponent(id, SpriteComponent(pixmap, srcWidth = ORB_FRAME, srcHeight = ORB_FRAME))
        world.addComponent(
            id,
            AnimationComponent(ORB_FRAME, ORB_FRAME, ORB_FRAME_COUNT, ORB_FRAME_SECONDS)
        )
        world.addComponent(id, OrbComponent(offset))
        world.addComponent(id, ContactWeaponComponent(ContactWeapon.ORB))
        world.addComponent(
            id,
            CollisionComponent(ORB_COLLISION_TOLERANCE, CollisionGroup.PLAYER_CONTACT)
        )
        world.addComponent(id, ZIndexComponent(19))
        return id
    }

    /**
     * A damage number that rises from ([x], [y]) and fades out, moved by
     * [at.smiech.engine.ecs.MovementSystem] and removed by
     * [at.smiech.engine.ecs.FloatingTextSystem].
     *
     * @param color overrides the white or red, for a number that is not damage to health, such as
     *   what a shield absorbed, drawn in the shield's color.
     */
    fun createDamageText(
        x: Float,
        y: Float,
        damage: Int,
        critical: Boolean = false,
        color: Int? = null,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 0f, 0f)))
        world.addComponent(id, VelocityComponent(Vector2(0f, -DAMAGE_TEXT_RISE_PER_TICK)))
        world.addComponent(
            id,
            FloatingTextComponent(
                text = damage.toString(),
                // A crit is bigger, red and stays up longer: size alone gets lost among the white
                // numbers.
                fontSize = if (critical) CRITICAL_TEXT_FONT_SIZE else DAMAGE_TEXT_FONT_SIZE,
                color = color ?: if (critical) EngineColors.RED else EngineColors.WHITE,
                duration = if (critical) CRITICAL_TEXT_DURATION_SECONDS else DAMAGE_TEXT_DURATION_SECONDS,
            )
        )
        return id
    }

    /**
     * A blast centered on ([centerX], [centerY]), [scale]d to match what died, so a boss does not
     * go out in the same puff as an escort.
     */
    fun createExplosion(
        centerX: Float,
        centerY: Float,
        pixmap: Pixmap,
        scale: Float = 1f,
    ): EntityId = createBurst(
        centerX, centerY, pixmap, EXPLOSION_FRAME_WIDTH,
        EXPLOSION_FRAME_COUNT, EXPLOSION_FRAME_SECONDS, scale,
    ).also { id ->
        // Fire lights the dark around it, and goes out with the fireball.
        if (lit) {
            world.addComponent(
                id,
                LightComponent(
                    BLAST_LIGHT_COLOR,
                    blastLightRadius(scale),
                    fadeSeconds = EXPLOSION_FRAME_COUNT * EXPLOSION_FRAME_SECONDS,
                ),
            )
        }
    }

    /**
     * Rock coming apart, for an obstacle: chunks and dust rather than fire. Its own sheet rather
     * than a tinted explosion, because a break moves differently: pieces thrown outward that fall.
     */
    fun createShatter(
        centerX: Float,
        centerY: Float,
        pixmap: Pixmap,
        scale: Float = 1f,
    ): EntityId = createBurst(
        centerX, centerY, pixmap, SHATTER_FRAME_WIDTH,
        SHATTER_FRAME_COUNT, SHATTER_FRAME_SECONDS, scale,
    )

    /**
     * A one-shot effect where something died or a shot struck, centered on the given point since
     * the caller knows what died, not the effect's frame size. It drifts with the scenery, so it
     * stays where the thing was in the world, and is removed once it has played.
     *
     * @param baseSrcX where its frames start on [pixmap], for a sheet that holds it in several
     *   colorways.
     */
    private fun createBurst(
        centerX: Float,
        centerY: Float,
        pixmap: Pixmap,
        frameWidth: Int,
        frameCount: Int,
        frameSeconds: Float,
        scale: Float,
        baseSrcX: Int = 0,
    ): EntityId {
        val width = frameWidth * scale
        val height = pixmap.height * scale

        val id = world.createEntity()
        world.addComponent(
            id,
            TransformComponent(
                Rect.fromLTWH(
                    centerX - width / 2f,
                    centerY - height / 2f,
                    width,
                    height
                )
            )
        )
        world.addComponent(id, VelocityComponent(Vector2(BURST_DRIFT, 0f)))
        world.addComponent(
            id,
            SpriteComponent(pixmap, baseSrcX = baseSrcX, srcWidth = frameWidth, scale = scale)
        )
        world.addComponent(
            id,
            AnimationComponent(
                frameWidth,
                pixmap.height,
                frameCount,
                frameSeconds,
                isLooping = false,
            )
        )
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(50))
        return id
    }

    private companion object {
        /**
         * The bat's sheet: six frames of one wingbeat left to right, the row repeated wounded and
         * battered below; see [WoundComponent]. A full beat takes about 0.4 s.
         */
        const val BAT_FRAME_COUNT = 6
        const val BAT_FRAME_SECONDS = 0.07f

        /**
         * The enemy sheets: four frames of wingbeat per type, laid out type by type, the row
         * repeated wounded and battered below, with the same stride on every sheet. A beat takes
         * about 0.4 s, matching the bat's.
         */
        const val ENEMY_FRAME_WIDTH = 32
        const val ENEMY_FRAME_COUNT = 4
        const val ENEMY_FRAME_SECONDS = 0.1f

        /**
         * The blast: eight frames of one fireball, square so it can expand evenly. Brief, since a
         * blast that outlives what it killed looks like a decal.
         */
        const val EXPLOSION_FRAME_WIDTH = 32
        const val EXPLOSION_FRAME_COUNT = 8
        const val EXPLOSION_FRAME_SECONDS = 0.055f

        /**
         * The obstacle break: seven frames of rock and dust, a little wider than the fireball since
         * the dust reaches further, and a touch slower, since debris has to be seen falling.
         */
        const val SHATTER_FRAME_WIDTH = 40
        const val SHATTER_FRAME_COUNT = 7
        const val SHATTER_FRAME_SECONDS = 0.07f

        /** Where enemy [type]'s strip starts on its sheet, which is laid out on an exact stride. */
        fun srcXOf(type: Int): Int = type.coerceAtLeast(0) * ENEMY_FRAME_WIDTH * ENEMY_FRAME_COUNT

        /** The boss wears the third enemy's colors, the same ones the final wave escorts it in. */
        const val BOSS_ENEMY_TYPE = 2

        /**
         * A blast's light radius at [scale], rounded to [BLAST_LIGHT_STEP] and capped at
         * [BLAST_LIGHT_MAX_RADIUS], since every light radius is rendered once and cached.
         */
        fun blastLightRadius(scale: Float): Int =
            ((BLAST_LIGHT_RADIUS * scale / BLAST_LIGHT_STEP).roundToInt() * BLAST_LIGHT_STEP)
                .coerceIn(BLAST_LIGHT_STEP, BLAST_LIGHT_MAX_RADIUS)

        /** Degrees to radians, for the spread on a fanned shot. */
        const val PI_OVER_180 = 0.017453292f

        /** Which way every hostile's artwork points: left, toward the bat. */
        const val HOSTILE_ARTWORK_DEGREES = 180f

        /** Which way the artwork of something that comes from behind points: right, the way it goes. */
        const val RIGHT_FACING_ARTWORK_DEGREES = 0f

        /**
         * The Sand Wyrm's body from the neck back, as the frame of its sheet each part is drawn
         * from and how far inside that frame its hit box sits: three big plates, three middling,
         * two small, and the tail. The tolerance grows as the plates shrink inside their frames.
         */
        val SAND_WYRM_BODY = listOf(
            2 to 6f, 2 to 6f, 2 to 6f,
            3 to 9f, 3 to 9f, 3 to 9f,
            4 to 12f, 4 to 12f,
            5 to 14f,
        )

        /** Its head fills most of its frame: jaws, crest and all. */
        const val SAND_WYRM_HEAD_TOLERANCE = 8f

        /**
         * The Naga's body from the neck back, as the frame of its sheet each part is drawn from and how
         * far inside that frame its hit box sits: two of its neck, four great coils, three lesser, two
         * small, and the tail. The tolerance grows as the parts shrink inside their frames.
         */
        val NAGA_BODY = listOf(
            2 to 10f, 2 to 10f,
            3 to 11f, 3 to 11f, 3 to 11f, 3 to 11f,
            4 to 15f, 4 to 15f, 4 to 15f,
            5 to 19f, 5 to 19f,
            6 to 21f,
        )

        /** Its hood is wide and its corners are air: the box sits well in from the frame. */
        const val NAGA_HEAD_TOLERANCE = 12f
    }
}
