package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.BossPartComponent
import at.smiech.cyanbat.ecs.EliteComponent
import at.smiech.cyanbat.ecs.ElitePalette
import at.smiech.cyanbat.ecs.GunComponent
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
import at.smiech.cyanbat.util.ENEMY_SHOT_LIGHT_RADIUS
import at.smiech.cyanbat.util.ENEMY_SHOT_VARIANT_OFFSET
import at.smiech.cyanbat.util.HEALTH_BAR_HEIGHT
import at.smiech.cyanbat.util.HEALTH_BAR_OFFSET_Y
import at.smiech.cyanbat.util.IMPACT_LIGHT_RADIUS
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
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.cyanbat.util.SHOT_HIT_POINTS
import at.smiech.cyanbat.util.SHOT_LIGHT_INTENSITY
import at.smiech.cyanbat.util.SHOT_LIGHT_PALENESS
import at.smiech.cyanbat.util.SHOT_SPEED
import at.smiech.cyanbat.util.TRAIL_DRIFT_PER_TICK
import at.smiech.cyanbat.util.TRAIL_DURATION_SECONDS
import at.smiech.cyanbat.util.TRAIL_INTERVAL_SECONDS
import at.smiech.cyanbat.util.TRAIL_MIN_SCALE
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
 * @param lit whether the stage is flown in the dark, where what gives off light carries a
 *   [LightComponent] and what stands in its way an [OccluderComponent]. In daylight neither is added,
 *   so nothing carries bookkeeping no system will read.
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
        world.addComponent(id, SpriteComponent(pixmap, srcWidth = BAT_FRAME_WIDTH, srcHeight = height))
        world.addComponent(
            id,
            AnimationComponent(BAT_FRAME_WIDTH, height, BAT_FRAME_COUNT, BAT_FRAME_SECONDS)
        )
        world.addComponent(id, WoundComponent(height, WOUND_MARKS))
        world.addComponent(id, CollisionComponent(5f, CollisionGroup.PLAYER))
        world.addComponent(id, HealthComponent(PLAYER_MAX_HIT_POINTS))
        // Bars are for the two things a fight is decided between - the bat and the stage's boss.
        // A bar over every passing enemy would bury the game behind them.
        world.addComponent(id, HealthBarComponent(HEALTH_BAR_HEIGHT, HEALTH_BAR_OFFSET_Y))
        world.addComponent(id, PlayerControlComponent())
        world.addComponent(id, WeaponComponent(shotIntervalSeconds))
        world.addComponent(id, TrailEmitterComponent(TRAIL_INTERVAL_SECONDS))
        // Dormant at level 1, which is where every run starts: an aura the player has not earned
        // yet draws nothing at all. GameScreen.syncAura is what wakes it up.
        world.addComponent(id, AuraComponent())
        // What the player sees the dark by. The bat throws no shadow of its own: it is the light.
        if (lit) world.addComponent(id, LightComponent(BAT_LIGHT_COLOR, BAT_LIGHT_RADIUS))
        world.addComponent(id, LifetimeComponent(false))
        world.addComponent(id, ZIndexComponent(20))
        return id
    }

    /**
     * One enemy of [species], at whatever strength the wave that ordered it calls for.
     *
     * [hitPoints], [damage] and [speedMultiplier] are arguments rather than constants because
     * that is the whole of how a stage ramps: the same species, sent in tougher, angrier and
     * faster as the minutes go by. They are fixed at spawn, so enemies already on screen keep the
     * strength they arrived with when a wave turns over.
     *
     * @param height one picture's worth of [pixmap], which stacks each species unhurt, wounded
     *   and battered; see [WoundComponent].
     * @param laneY the path a group flies around, for the species that fly in one. A swarm's
     *   members all share it and each sits [offsetY] from it; a loner's lane is where it spawned.
     * @param phase a per-member offset into its pattern, so a swarm does not buzz in unison.
     * @param holdX where a hovering enemy stops, or a diving one commits.
     * @param shieldPoints a shield bubble to spawn behind, or zero for none.
     * @param shieldRegrowth how much of that shield grows back a second once it has been left alone,
     *   as a fraction of it; see [EnemySpecies.shieldRegrowth].
     * @param gun what it fires, or null for an enemy that only rams.
     * @param firstShotDelay how long an armed enemy waits before its first volley, on top of its
     *   interval - so a formation spawned on one tick does not fire as one.
     * @param elite the palette it wears as an elite, or null for an ordinary enemy. What makes an
     *   elite tougher and quicker on the trigger is already in the health and gun it is handed; this
     *   is the glow, the color of its shots, and the mark the run pays out on.
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
        // At full pace until it is wounded; GameScreen slows it from there. Only an ordinary enemy
        // carries one, so nothing a wound does can reach a boss.
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
        // So that anything which is given a gun fires in its own color rather than the player's - and
        // an elite in the color of its glow rather than of its kind, which is how its fire is told
        // from the rest of the wave's.
        world.addComponent(id, ProjectileStyleComponent(elite?.shotVariant ?: species.shotVariant))
        if (elite != null) {
            world.addComponent(id, EliteComponent(elite))
            // A fixed glow rather than one that grows: an elite arrives as what it is.
            world.addComponent(
                id,
                AuraComponent(intensity = ELITE_AURA_INTENSITY, tier = ELITE_AURA_TIER, colors = elite.aura),
            )
            // And in the dark the glow is a light, so it is seen coming from across the cave.
            if (lit) world.addComponent(id, LightComponent(elite.aura.rim, ELITE_LIGHT_RADIUS))
        }
        if (lit) world.addComponent(id, OccluderComponent(CREATURE_SHINE))
        if (gun != null) {
            world.addComponent(id, WeaponComponent(gun.interval, timeSinceLastShot = -firstShotDelay))
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
        // Drawn facing left, as every hostile is - but for the one that comes from behind - and turned
        // from there to point along its arc.
        if (species.facesHeading) {
            val artwork = if (species.drawnFacingRight) RIGHT_FACING_ARTWORK_DEGREES else HOSTILE_ARTWORK_DEGREES
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
     * The cave's boss, the Caco Imp: the sheet's last enemy drawn [scale] times over, with a health
     * pool worth a fight and a gun of its own, which [CacoImpBrain] rearms as the fight goes on.
     *
     * It differs from [createEnemy] in four ways that matter, and each is deliberate - and so does
     * every boss, all of which are built by [createBossEntity]. It is never culled for leaving the
     * frame, because it enters from the edge and a boss that could drift out of the stage is a boss
     * the player can lose rather than beat. It carries a health bar, the only thing besides the bat
     * that does, because a fight this long is unreadable without one. It holds station instead of
     * closing, which is what [EnemyMovementType.BOSS] is for. And it carries no [PaceComponent]: its
     * wounds show, but they never slow it or thin out its fire.
     *
     * In the dark it is alight: the fight is fought across the cave, and a boss sunk in the dark at
     * its far end is one the player cannot read. Its brain turns the light down, out and up again.
     *
     * @param bar where its health bar is pinned: it puts its light out to prowl the dark, and a bar
     *   hanging under it would show where it had got to.
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
        // Tolerance scaled with the sprite, so the boss's box sits in from its edges by the same
        // proportion an ordinary enemy's does.
        collisionTolerance = 5f * scale,
    ).also { id ->
        world.addComponent(id, GunComponent(gun.volleys))
        world.addComponent(id, HealthBarComponent(pinnedTo = bar))
        if (lit) {
            world.addComponent(
                id,
                LightComponent(CACO_IMP_LIGHT_COLOR, CACO_IMP_LIGHT_RADIUS, CACO_IMP_SMOULDER_INTENSITY),
            )
        }
    }

    /**
     * The jungle's boss: drawn at its own size from its own sheet, rather than an enemy magnified.
     *
     * Everything [createBoss] says about why a boss is built the way it is holds here too. What is
     * different is the flight - a figure eight rather than a weave - and the gun, which starts on
     * the Moth Queen's opening volleys; [MothQueenBrain] rearms it as the fight goes on.
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
            // Wide, because most of her frame is wing and the corners of it are empty air.
            collisionTolerance = MOTH_QUEEN_COLLISION_TOLERANCE,
        )
        world.addComponent(id, GunComponent(gun.volleys))
        // Down until she raises it, but present, so the brain only ever has to raise it.
        world.addComponent(id, ShieldComponent(0))
        return id
    }

    /**
     * The desert's boss: a head and a train of nine armored parts behind it, each one an entity of
     * its own so it can be run into, shot and lit up wherever it is along the body.
     *
     * The head is the boss: it carries the health, the pinned health bar, the gun and the leap it
     * flies by. The plates carry no health: a shot into one lands on the head, though only
     * [SAND_WYRM_PLATE_SHARE] of it, so the head is the place to aim. They are placed every tick by
     * [SandWyrmBrain] along the path the head has flown - which is why nothing but the brain may
     * move or remove them, and why they are never culled for leaving the frame: the whole wyrm
     * spends half the fight under it. Every part, head included, is a [BossPartComponent], which
     * is what keeps the bat from wearing it down by being hit by it.
     *
     * The parts are created tail first, so each is drawn over the one behind it and the body
     * overlaps toward the head the way plates do. Only the head carries a [WoundComponent], for the
     * same reason only the head carries health; [SandWyrmBrain] draws the plates from its row.
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
            // No wound of its own, because no health of its own: the brain draws every plate from
            // the head's row, so the whole body is as battered as the health it shares.
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
            AnimationComponent(SAND_WYRM_FRAME, SAND_WYRM_FRAME, SAND_WYRM_HEAD_FRAMES, SAND_WYRM_HEAD_FRAME_SECONDS),
        )
        world.addComponent(head, WoundComponent(SAND_WYRM_FRAME, WOUND_MARKS))
        world.addComponent(head, FacesVelocityComponent(HOSTILE_ARTWORK_DEGREES))
        // At rest until the brain throws it: a leap whose station can never be reached.
        world.addComponent(head, EnemyBehaviorComponent(EnemyMovementType.LEAP, y, holdX = -Float.MAX_VALUE))
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
     * A one-shot effect like a blast, but it stays where it was thrown up rather than drifting with
     * the scenery: the wyrm comes up where the sand boiled, and the two have to line up.
     */
    fun createSandPlume(centerX: Float, bottom: Float, pixmap: Pixmap, scale: Float = 1f): EntityId {
        val size = SAND_WYRM_FRAME * scale
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(centerX - size / 2f, bottom - size, size, size)))
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
                SAND_WYRM_FRAME, SAND_WYRM_FRAME, SAND_WYRM_PLUME_FRAMES, SAND_WYRM_PLUME_FRAME_SECONDS,
                isLooping = false,
            ),
        )
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(50))
        return id
    }

    /**
     * The lagoon's boss: a hooded head and a body of twelve parts behind it, each an entity of its
     * own so it can be run into, shot and lit up wherever it is along the body - the Sand Wyrm's build,
     * bigger, and moved by its own brain.
     *
     * The head is the boss: it carries the health, the pinned health bar, the gun, and a shield its
     * hood raises late in the fight. The parts carry no health: a shot into one lands on the head,
     * though only [NAGA_PLATE_SHARE] of it. Neither carries a movement of the engine's: [NagaBrain]
     * places every part every tick - rearing up out of the water, striking, swimming - which is why
     * nothing else may move or remove them, and why none is culled for leaving the frame: it spends
     * a good part of its fight under the water, below it. Every part, head included, is a
     * [BossPartComponent], so the bat is not wearing it down by being hit by it.
     *
     * The parts are created tail first, so each is drawn over the one behind it and the body
     * overlaps toward the head. Only the head carries a [WoundComponent]; the brain draws the parts
     * from its row.
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
                SpriteComponent(pixmap, baseSrcX = sheetFrame * NAGA_FRAME, srcWidth = NAGA_FRAME, srcHeight = NAGA_FRAME),
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
        world.addComponent(head, SpriteComponent(pixmap, srcWidth = NAGA_FRAME, srcHeight = NAGA_FRAME))
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
        // Down until its hood raises it, but present, so the brain only ever has to raise it.
        world.addComponent(head, ShieldComponent(0))
        if (lit) world.addComponent(head, OccluderComponent(CREATURE_SHINE))
        world.addComponent(head, LifetimeComponent(false))
        world.addComponent(head, ZIndexComponent(13))
        return listOf(head) + body
    }

    /**
     * Water thrown up out of the sea, standing on [bottom] and centered on [centerX]: the boiling
     * before the Naga rises, and the burst as it does. Stays where it was thrown, like the Sand Wyrm's
     * sand, since the Naga comes up where the water boiled.
     */
    fun createSplash(centerX: Float, bottom: Float, pixmap: Pixmap, scale: Float = 1f): EntityId {
        val size = NAGA_FRAME * scale
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(centerX - size / 2f, bottom - size, size, size)))
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
            AnimationComponent(NAGA_FRAME, NAGA_FRAME, NAGA_SPLASH_FRAMES, NAGA_SPLASH_FRAME_SECONDS, isLooping = false),
        )
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(50))
        return id
    }

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
        // At the marks the fight changes phase at, for a boss that has phases; see WOUND_MARKS.
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
     * @param keyframed whether [pixmap] holds the obstacle once per time of day, a row of [height]
     *   apiece, for a stage whose light changes; see [at.smiech.cyanbat.ecs.SkySystem], which
     *   picks the rows.
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
     * @param angleDegrees how far off straight the shot flies, positive downwards. A fanned shot
     *   keeps the full [speed] along its own heading rather than along x, so the outer shots
     *   of a spread do not lag behind the middle one.
     * @param speed along its heading, in framebuffer pixels per tick. The bat's are [SHOT_SPEED];
     *   enemy fire is slower, so it can be seen coming.
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
            // A bolt glows the color of its body, so a shot lights its way across the dark and the
            // player sees whose it is by the light it throws as well as by the bolt.
            val color = EngineColors.lerp(SHOT_BODY_COLORS[variant], EngineColors.WHITE, SHOT_LIGHT_PALENESS)
            val radius = if (isPlayer) PLAYER_SHOT_LIGHT_RADIUS else ENEMY_SHOT_LIGHT_RADIUS
            world.addComponent(id, LightComponent(color, radius, SHOT_LIGHT_INTENSITY))
        }
        world.addComponent(id, LifetimeComponent(true))
        world.addComponent(id, ZIndexComponent(15))
        return id
    }

    /**
     * A flash of light where a shot was spent, in the shot's own [color]: it lights up whatever was
     * struck at the moment it is struck. Nothing but the light, which fades in
     * [IMPACT_LIGHT_SECONDS] and takes the entity with it. It drifts with the scenery, so it stays
     * where the blow landed.
     */
    fun createFlash(centerX: Float, centerY: Float, color: Int): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(centerX, centerY, 0f, 0f)))
        world.addComponent(id, VelocityComponent(Vector2(BURST_DRIFT, 0f)))
        world.addComponent(
            id,
            LightComponent(color, IMPACT_LIGHT_RADIUS, fadeSeconds = IMPACT_LIGHT_SECONDS, removeWhenFaded = true),
        )
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
     *
     * @param color overrides the white or red, for a number that is not damage to health - what
     *   a shield soaked up is drawn in the shield's own color.
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
                // A crit is read rather than glanced at, so it is bigger, red, and stays up
                // longer. Three changes rather than one because a number that is only larger
                // still gets lost in a screen of white numbers going up at the same time.
                fontSize = if (critical) CRITICAL_TEXT_FONT_SIZE else DAMAGE_TEXT_FONT_SIZE,
                color = color ?: if (critical) EngineColors.RED else EngineColors.WHITE,
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
     * Rock coming apart, for an obstacle: chunks and dust rather than fire.
     *
     * Its own sheet rather than the explosion tinted grey, because the two are different events
     * and the difference is in the motion, not the palette. A fireball expands from a hot core and
     * hollows out; a break throws angular pieces outward and lets them fall.
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
     * The shared shape of a one-shot effect left where something died.
     *
     * Centered rather than placed by its corner, because the caller knows what died and not how
     * big a frame of the effect happens to be. It drifts with the scenery so it stays where the
     * thing was in the world rather than on the screen, and it is reaped by [LifetimeComponent]
     * and the animation cull once it has played.
     */
    private fun createBurst(
        centerX: Float,
        centerY: Float,
        pixmap: Pixmap,
        frameWidth: Int,
        frameCount: Int,
        frameSeconds: Float,
        scale: Float,
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
        world.addComponent(id, SpriteComponent(pixmap, srcWidth = frameWidth, scale = scale))
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
         * The bat's sheet: six frames of one wing-beat, laid out left to right, and that row again
         * wounded and battered below it; see [WoundComponent].
         *
         * Six rather than the two it had, because two frames of a flap is a sprite blinking
         * between poses; a beat needs a downstroke and a fold to read as one. The interval is set
         * so the whole cycle still takes about the 0.4s the old pair did - the bat beats its wings
         * at the same rate, it just has the frames to show it now.
         */
        const val BAT_FRAME_COUNT = 6
        const val BAT_FRAME_SECONDS = 0.07f

        /**
         * The enemy sheets: three types in the cave's and five in the jungle's and the desert's,
         * four frames of wingbeat each, laid out type by type - and the whole row again wounded and
         * battered below it, so the stride along a row is the same on every one of them.
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
         * The obstacle break: seven frames of rock and dust, a little wider than the fireball
         * because the dust reaches further than a blast of the same nominal size.
         *
         * Held a touch slower than the explosion too. Fire is over the instant it has burned, but
         * debris has to be seen falling or it reads as a flicker.
         */
        const val SHATTER_FRAME_WIDTH = 40
        const val SHATTER_FRAME_COUNT = 7
        const val SHATTER_FRAME_SECONDS = 0.07f

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

        /**
         * A blast's light at [scale], rounded to [BLAST_LIGHT_STEP] and held to [BLAST_LIGHT_MAX_RADIUS]:
         * every radius of light is drawn once and kept, and the bosses go up in blasts of every size.
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
