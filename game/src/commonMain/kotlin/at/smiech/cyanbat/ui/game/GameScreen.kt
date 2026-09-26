package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.ScoreTracker
import at.smiech.cyanbat.ecs.BackgroundScrollingSystem
import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.Volley
import at.smiech.cyanbat.progress.PlayerLoadout
import at.smiech.cyanbat.progress.PlayerProgress
import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.service.EnemyGenerator
import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.service.StageProgression
import at.smiech.cyanbat.service.ObstacleGenerator
import at.smiech.cyanbat.util.AURA_SURGE_VOLUME
import at.smiech.cyanbat.util.BANNER_CHAR_WIDTH
import at.smiech.cyanbat.util.BANNER_FONT_SIZE
import at.smiech.cyanbat.util.BAT_DEATH_FRAME_COUNT
import at.smiech.cyanbat.util.BAT_DEATH_FRAME_SECONDS
import at.smiech.cyanbat.util.BAT_FRAME_WIDTH
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.DEATH_GRAVITY
import at.smiech.cyanbat.util.DEATH_PUFF_INTERVAL_SECONDS
import at.smiech.cyanbat.util.DEATH_PUFF_SCALE
import at.smiech.cyanbat.util.DEATH_SPIN_DEGREES_PER_SECOND
import at.smiech.cyanbat.util.DEATH_TERMINAL_VELOCITY
import at.smiech.cyanbat.util.HIT_FLASH_COLOR
import at.smiech.cyanbat.util.HIT_FLASH_SECONDS
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
import at.smiech.cyanbat.util.STAGE_COMPLETE_ARMING_SECONDS
import at.smiech.cyanbat.util.PAUSE_DIM
import at.smiech.cyanbat.util.PLAYER_SHOT_VARIANT
import at.smiech.cyanbat.util.POWER_UP_ARMING_SECONDS
import at.smiech.cyanbat.util.POWER_UP_CARD_GAP
import at.smiech.cyanbat.util.POWER_UP_CARD_HEIGHT
import at.smiech.cyanbat.util.POWER_UP_CARD_TOP
import at.smiech.cyanbat.util.POWER_UP_CARD_WIDTH
import at.smiech.cyanbat.util.RESUME_ARMING_SECONDS
import at.smiech.cyanbat.util.REVIVE_HEALTH_FRACTION
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.cyanbat.util.SHOT_VOLUME
import at.smiech.cyanbat.util.SPREAD_ANGLE_DEGREES
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.TRAIL_SEGMENT_HEIGHT_FRACTION
import at.smiech.cyanbat.util.TRAIL_SEGMENT_WIDTH_FRACTION
import at.smiech.cyanbat.util.WAVE_BANNER_SECONDS
import at.smiech.cyanbat.util.XP_BAR_HEIGHT
import at.smiech.cyanbat.util.XP_PER_BOSS
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.Graphics
import at.smiech.engine.Screen
import at.smiech.engine.drawOutlinedString
import at.smiech.engine.ecs.AnimationComponent
import at.smiech.engine.ecs.AnimationSystem
import at.smiech.engine.ecs.AuraComponent
import at.smiech.engine.ecs.AuraSystem
import at.smiech.engine.ecs.BounceSystem
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.CollisionSystem
import at.smiech.engine.ecs.DamageComponent
import at.smiech.engine.ecs.DeathSystem
import at.smiech.engine.ecs.DeathThroesComponent
import at.smiech.engine.ecs.EnemyBehaviorSystem
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.FacingSystem
import at.smiech.engine.ecs.FloatingTextSystem
import at.smiech.engine.ecs.HealthBarSystem
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.HitFlashComponent
import at.smiech.engine.ecs.HitFlashSystem
import at.smiech.engine.ecs.LifetimeSystem
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.PierceComponent
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.PlayerInputSystem
import at.smiech.engine.ecs.ProjectileStyleComponent
import at.smiech.engine.ecs.RenderSystem
import at.smiech.engine.ecs.ShieldComponent
import at.smiech.engine.ecs.ShieldSystem
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TrailSystem
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.WeaponSystem
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * One run through one stage.
 *
 * A run is a stage: moving on to the next one builds a fresh screen, which is what resets the score,
 * the bat's experience and its power-ups. Each stage is meant to be beaten from a standing start,
 * however it was reached - through the one before it or straight from the stage select.
 *
 * @param stageId which stage to fly, 1-based. An id with no stage behind it flies the first.
 */
class GameScreen(
    override val game: Game,
    private val env: CyanBatEnvironment,
    stageId: Int = 1,
) : Screen {
    var currentStage = env.assets.stage(stageId)

    private val world = World()
    private val factory = EntityFactory(world)

    /**
     * Canceled in [dispose], so nothing started here outlives the screen. On the main dispatcher,
     * which is the thread the game loop runs on, so what a coroutine here writes needs no locking.
     */
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val batId: EntityId

    private val scoring = ScoreTracker()
    var highscore: Int = 0
    var tick = TICK_INITIAL
    private var tickTime = 0f

    /** The difficulty curve of the stage being played; see [StageProgression]. */
    private val progression = StageProgression.forStage(currentStage.id)

    var enmGen = EnemyGenerator(
        xSpawnPosition = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        currentStage.enemySheet,
        progression = progression,
        onWaveChanged = { wave -> announce("WAVE ${wave.index + 1}") },
        onBossSpawned = { announce(progression.design.bossName) },
        bossPixmap = currentStage.bossSheet,
        onBossPhaseChanged = { phase -> announce(bossPhaseBanner(phase)) },
    )
    var obsGen = ObstacleGenerator(
        worldWidth = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        currentStage
    )

    private lateinit var g: Graphics
    private var stageNameDisplayTime = 3.0f

    /** The wave or boss announcement currently on screen, and what is left of its time. */
    private var bannerText: String? = null
    private var bannerTime = 0f

    /**
     * Set when the stage's boss goes down. The run is over, but won rather than lost, so the bat
     * stays where it is and the player reads their score off a screen they earned.
     */
    private var stageComplete = false

    /** Time before the victory overlay will accept a tap as "done"; see [handleStageCompleteControls]. */
    private var stageCompleteArmingTime = 0f

    /** Experience earned this run, and the levels it has bought. */
    private val progress = PlayerProgress()

    /** What the run's power-ups have made of the bat; see [PlayerLoadout]. */
    private val loadout = PlayerLoadout()

    /** Rolls the bat's critical hits. Its own, so nothing else in the run shifts the sequence. */
    private val random = Random.Default

    /**
     * Level ups owed but not yet spent.
     *
     * A count rather than a flag: one kill late in a run can cross two thresholds, and the player
     * is owed a pick for each. They are handed out one dialog at a time.
     */
    private var pendingLevelUps = 0

    /** The power-ups currently on offer, or empty when the run is not waiting on a choice. */
    private var offer: List<PowerUp> = emptyList()

    /** Time before the level up dialog will accept a tap; see [handlePowerUpChoice]. */
    private var offerArmingTime = 0f

    /**
     * Taps on whichever overlay is up - level up, pause, stage complete. One is enough because
     * only one of them reads the events on any frame (pause, over the level up dialog, reads them
     * first), and each [TapDetector.reset]s it as it opens.
     */
    private val overlayTaps = TapDetector()

    /** Fractional health owed by Regeneration, carried between ticks; see [regenerate]. */
    private var regenCarry = 0f

    /** Set by the player, and by the host backgrounding the app. Cleared only by the player. */
    private var paused = false

    /**
     * Time before a tap counts as "resume", in seconds.
     *
     * Android's back *gesture* is a swipe from the edge, so the pointer events that pause the
     * game are followed by the finger lifting. Without this the game would unpause on the tail of
     * the very gesture that paused it.
     */
    private var resumeArmingTime = 0f

    init {
        game.graphics?.let { g = it }

        // Setup Systems
        world.addSystem(PlayerInputSystem(game.frameBufferWidth, game.frameBufferHeight))
        // Before the movement it feeds: the gravity it adds this tick is the gravity this tick
        // moves by, rather than arriving one frame late.
        world.addSystem(DeathSystem { dyingId -> shedDeathPuff(dyingId) })
        world.addSystem(MovementSystem())
        // Straight after the movement that carries a shot into an edge, and well before the
        // culling that would remove it there.
        world.addSystem(BounceSystem(game.frameBufferWidth, game.frameBufferHeight))
        world.addSystem(WeaponSystem { shooterId -> fireShot(shooterId) })
        world.addSystem(BackgroundScrollingSystem(game.frameBufferWidth, factory))
        world.addSystem(EnemyBehaviorSystem())
        // After everything that can set a velocity - the steering, the movement, the bounce, the
        // enemy patterns - so a shot is drawn pointing the way it is travelling on this frame
        // rather than the way it was travelling on the last one.
        world.addSystem(FacingSystem())
        world.addSystem(AnimationSystem())
        // Before the collisions that arm a flash, so a hit landing this tick gets a full frame lit
        // rather than being aged down on the very tick it happened.
        world.addSystem(HitFlashSystem())
        world.addSystem(CollisionSystem { id1, id2 -> handleCollision(id1, id2) })
        // With the height too: enemy fire is aimed and fanned now, and leaves through the top and
        // bottom as well as the sides.
        world.addSystem(LifetimeSystem(game.frameBufferWidth, game.frameBufferHeight))
        // Before the sprites, so the halo is light coming off the bat rather than a wash over it.
        // This is also the pass that advances the aura's clock; see [AuraSystem.Layer].
        world.addSystem(AuraSystem(AuraSystem.Layer.HALO))
        world.addSystem(RenderSystem())
        // Straight after the sprites, so a bubble encloses the enemy it protects rather than being
        // painted over by it; and before the health bars, which must never be lost behind one.
        world.addSystem(ShieldSystem())
        // After the sprites: these four draw on top of the run rather than into it. The wake goes
        // first of them, so the bar and the damage numbers stay legible over it. The arcs go over
        // the wake and under the bar, which is the one thing that must never be lost behind an
        // effect - a player who cannot read their own health cannot play the fight.
        world.addSystem(TrailSystem { emitterId -> shedTrail(emitterId) })
        world.addSystem(AuraSystem(AuraSystem.Layer.ARCS))
        world.addSystem(HealthBarSystem(game.frameBufferHeight))
        world.addSystem(FloatingTextSystem())

        // Add the primary background
        factory.createBackground(0f, currentStage.background)

        batId = factory.createBat(
            x = (game.frameBufferWidth / 3).toFloat(),
            y = (game.frameBufferHeight / 2).toFloat(),
            width = 45f,
            height = env.assets.graphics.bat.height.toFloat(),
            pixmap = env.assets.graphics.bat,
            // From the loadout rather than the constant, so the bat is built from the same stats
            // the power-ups go on to change and the two can never start out disagreeing.
            shotIntervalSeconds = loadout.shotIntervalSeconds,
        )

        startStageMusic()
        initStats()
    }

    /**
     * Spawns a shot at the shooter's leading edge, centered on it vertically.
     *
     * Which edge leads depends on who is firing: the bat shoots to the right and the boss back to
     * the left, so each shot leaves from the side it travels toward rather than through the
     * sprite that fired it. A shot carries its shooter's damage, which is how the boss hits harder
     * at range than anything else in the stage does on contact.
     */
    private fun fireShot(shooterId: EntityId) {
        val transform = world.getComponent(shooterId, TransformComponent::class) ?: return
        val isPlayer = world.hasComponent(shooterId, PlayerControlComponent::class)
        // An enemy holds its fire until it is on screen. A volley fired from past the right edge
        // would arrive out of nowhere, and nothing the player could see would have warned them.
        if (!isPlayer && !isOnScreen(transform.rect)) return
        val gun = if (isPlayer) null else world.getComponent(shooterId, GunComponent::class)
        if (gun != null) {
            fireVolley(shooterId, transform.rect, gun)
            return
        }
        val shot = env.assets.graphics.shot
        // The sheet's own width is every colorway laid side by side, so a shot is positioned and
        // sized by one frame of it rather than by the pixmap.
        val x = if (isPlayer) transform.rect.right else transform.rect.left - SHOT_FRAME_WIDTH
        val y = transform.rect.centerY - shot.height / 2f
        // Read off the shooter, so a bolt is the color of whatever fired it. The bat has no such
        // component and falls through to its own cyan.
        val variant = world.getComponent(shooterId, ProjectileStyleComponent::class)?.variant
            ?: PLAYER_SHOT_VARIANT
        // The bat's damage is a run stat the power-ups raise; everything else deals what it was
        // spawned with.
        val damage = if (isPlayer) loadout.shotDamage else damageOf(shooterId)

        for (angle in spreadAngles(if (isPlayer) loadout.extraShots else 0)) {
            // Rolled per projectile rather than per volley, so a wider fan really is more chances
            // at one. The bat only: a critical is a reward, and one landing on the player from
            // off screen would just be a death they cannot account for.
            val critical = isPlayer && random.nextFloat() < loadout.criticalChance

            factory.createShot(
                x = x,
                y = y,
                width = SHOT_FRAME_WIDTH.toFloat(),
                height = shot.height.toFloat(),
                pixmap = shot,
                isPlayer = isPlayer,
                damage = if (critical) loadout.criticalDamage else damage,
                critical = critical,
                variant = variant,
                angleDegrees = angle,
                // Piercing and ricochet are the bat's alone. An enemy shot that came back off a
                // wall would be a hazard the player has no way to read or answer.
                pierce = if (isPlayer) loadout.shotPierce else 0,
                bounce = if (isPlayer) loadout.shotBounce else 0,
            )
        }

        // Once per volley, not once per shot: a spread is one pull of the trigger, and playing it
        // per projectile would make a five-way fan five times as loud as a single shot.
        //
        // The bat only. The boss fires too, but its shots are already announced by being on
        // screen and coming at the player, and a second gun in the mix at this cadence would
        // bury the one the player is actually operating.
        if (isPlayer && env.audioSettings.soundsEnabled) {
            env.assets.audio.shotSound.play(SHOT_VOLUME)
        }
    }

    private fun isOnScreen(rect: Rect): Boolean =
        rect.left >= 0f && rect.right <= game.frameBufferWidth

    /**
     * One pull of an enemy's trigger: whatever [gun]'s next [Volley] is, in the shooter's color
     * and at its damage.
     *
     * Everything but a straight bolt leaves from the shooter's center, because a fan or a ring
     * spreads out from a point and a straight bolt from the edge it is travelling toward.
     */
    private fun fireVolley(shooterId: EntityId, rect: Rect, gun: GunComponent) {
        val volley = gun.pull()
        val shot = env.assets.graphics.shot
        val variant = world.getComponent(shooterId, ProjectileStyleComponent::class)?.variant
            ?: PLAYER_SHOT_VARIANT
        val damage = (damageOf(shooterId) * volley.damageFactor).roundToInt().coerceAtLeast(1)

        val straight = volley.pattern == ShotPattern.STRAIGHT
        val x = if (straight) rect.left - SHOT_FRAME_WIDTH else rect.centerX - SHOT_FRAME_WIDTH / 2f
        val y = rect.centerY - shot.height / 2f

        for (angle in volleyAngles(volley, gun, rect)) {
            factory.createShot(
                x = x,
                y = y,
                width = SHOT_FRAME_WIDTH.toFloat(),
                height = shot.height.toFloat(),
                pixmap = shot,
                isPlayer = false,
                damage = damage,
                variant = variant,
                angleDegrees = angle,
                speed = volley.speed,
            )
        }
    }

    /**
     * The headings of one enemy volley, in the same terms [EntityFactory.createShot] takes: degrees
     * off straight ahead - which for an enemy is to the left - positive downwards.
     */
    private fun volleyAngles(volley: Volley, gun: GunComponent, rect: Rect): List<Float> =
        when (volley.pattern) {
            ShotPattern.STRAIGHT -> listOf(0f)
            ShotPattern.AIMED -> listOf(aimAt(rect))
            ShotPattern.AIMED_FAN -> {
                val aim = aimAt(rect)
                val middle = (volley.count - 1) / 2f
                List(volley.count) { aim + (it - middle) * volley.spreadDegrees }
            }

            ShotPattern.RADIAL -> {
                val step = 360f / volley.count
                val start = gun.spin
                // Half a step round each time, so the lanes one ring leaves open are the ones the
                // next ring closes.
                gun.spin = (gun.spin + step / 2f) % 360f
                List(volley.count) { start + it * step }
            }
        }

    /**
     * The heading from [rect]'s center to the bat's, or straight ahead with no bat to aim at.
     *
     * Aimed where the bat *is*, not where it is going: leading the target would make a moving
     * player unable to dodge by moving, which is the one thing a player can always do.
     */
    private fun aimAt(rect: Rect): Float {
        val health = world.getComponent(batId, HealthComponent::class)
        if (health?.alive != true) return 0f
        val bat = world.getComponent(batId, TransformComponent::class)?.rect ?: return 0f
        val dx = bat.centerX - rect.centerX
        val dy = bat.centerY - rect.centerY
        // Measured from the left, which is the way enemy fire faces: atan2 of (down, left).
        return atan2(dy, -dx) * DEGREES_PER_RADIAN
    }

    /**
     * The headings of one volley: the straight shot, then [extraShots] fanned alternately below
     * and above it, widening a step at a time.
     *
     * The straight shot is always there, so a spread widens the bat's fire rather than replacing
     * it. Alternating sides rather than filling one first is what keeps the fan balanced: an even
     * number of extras is symmetrical, and an odd one leans by a single shot instead of stacking
     * every extra below the line.
     */
    private fun spreadAngles(extraShots: Int): List<Float> =
        (0..extraShots).map { index ->
            val step = (index + 1) / 2
            val sign = if (index % 2 == 1) 1f else -1f
            if (index == 0) 0f else sign * step * SPREAD_ANGLE_DEGREES
        }

    /**
     * Sheds one segment of the bat's wake, just off the back of it.
     *
     * Centered on the lower half of the sprite rather than sitting under it: the bat's tail is the
     * lower band of the frame, and a wake off its belly would read as coming from the health bar instead.
     */
    private fun shedTrail(emitterId: EntityId) {
        val rect = world.getComponent(emitterId, TransformComponent::class)?.rect ?: return
        val width = rect.width * TRAIL_SEGMENT_WIDTH_FRACTION
        val height = rect.height * TRAIL_SEGMENT_HEIGHT_FRACTION
        factory.createTrail(
            x = rect.left - width,
            y = rect.centerY + height / 2f,
            width = width,
            height = height,
        )
    }

    private fun handleCollision(id1: EntityId, id2: EntityId) {
        val group1 = collisionGroupOf(id1)
        val group2 = collisionGroupOf(id2)
        // Read up front: killing the boss clears it from the generator, and this pass still has to
        // know which of the two it was looking at.
        val bossId = enmGen.bossId

        // Resolved before anything is hurt: a piercing shot that has already gone through this
        // enemy is not colliding with it any more, however many frames the two spend overlapping.
        // Without this the pair would re-hit every frame, spending the pierce and killing the
        // enemy several times over.
        if (hasAlreadyPierced(id1, id2) || hasAlreadyPierced(id2, id1)) return

        // Both read before either side takes its hit: an entity that dies here still lands the
        // blow it arrived with, and reading afterwards would give the survivor a free pass.
        val damage1 = damageOf(id1)
        val damage2 = damageOf(id2)
        val died1 = damage(id1, damage2, dealtBy = id2)
        val died2 = damage(id2, damage1, dealtBy = id1)

        // A kill scores when the enemy actually dies rather than on every shot that lands: past
        // the opening wave they take more than one.
        if (isEnemyShotDown(group1, group2)) {
            val enemyDied = if (group1 == CollisionGroup.ENEMY) died1 else died2
            if (enemyDied) {
                scoring.registerEnemyDestroyed()
                // The boss is banked by completeStage, which knows it was the boss. Everything
                // else is worth what its wave is worth.
                val enemyId = if (group1 == CollisionGroup.ENEMY) id1 else id2
                if (enemyId != bossId) {
                    awardExperience(PlayerProgress.experienceForKill(enmGen.currentWave.index))
                }
            }
        }
    }

    /**
     * Banks [amount] of experience and queues a power-up pick for every level it bought.
     *
     * Queued rather than shown, because this runs from inside a world update: the dialog goes up
     * on the next frame, once the tick that earned it has finished resolving.
     */
    private fun awardExperience(amount: Int) {
        pendingLevelUps += progress.award((amount * loadout.experienceMultiplier).roundToInt())
        syncAura()
    }

    /**
     * Puts the bat's aura where its level says it should be.
     *
     * Called on every award rather than only on a level up, because the two dials it sets move on
     * different schedules and only one of them is a level: the glow is read straight off
     * [PlayerProgress.level] and the tier off the same number divided down. Setting both in one
     * place is what keeps them from ever disagreeing.
     *
     * Crossing a tier is the moment the effect is built around - more sparks, another arc - so it
     * gets a flare and a sound. Compared rather than counted, so nothing is owed if two tiers are
     * crossed at once by a single late kill.
     */
    private fun syncAura() {
        val aura = world.getComponent(batId, AuraComponent::class) ?: return
        aura.intensity = progress.auraIntensity

        val tier = progress.auraTier
        if (tier <= aura.tier) return
        aura.tier = tier
        aura.surge = 1f
        if (env.audioSettings.soundsEnabled) {
            env.assets.audio.auraSurgeSound.play(AURA_SURGE_VOLUME)
        }
    }

    /**
     * True when this pair is one of the player's shots meeting an enemy, in either order - the
     * collision system reports pairs by entity id, not by role.
     *
     * Obstacles deliberately do not count: they are scenery a shot happens to clear, not a kill.
     */
    private fun isEnemyShotDown(group1: CollisionGroup?, group2: CollisionGroup?): Boolean =
        setOf(group1, group2) == setOf(CollisionGroup.PLAYER_PROJECTILE, CollisionGroup.ENEMY)

    private fun collisionGroupOf(id: EntityId): CollisionGroup? =
        world.getComponent(id, CollisionComponent::class)?.group

    /**
     * What [id] takes off whatever it runs into.
     *
     * Anything spawned by a wave carries its own [DamageComponent]; the fallback is for the
     * entities whose damage never varies - the bat itself, and the obstacles bolted to the cave.
     */
    private fun damageOf(id: EntityId): Int =
        world.getComponent(id, DamageComponent::class)?.amount ?: DAMAGE_PER_HIT

    /**
     * True when [id] is carrying a critical blow.
     *
     * Anything without a [DamageComponent] - the bat itself, an obstacle - is never critical, so
     * ramming an enemy stays an ordinary hit however hard the run has made the bat.
     */
    private fun isCritical(id: EntityId): Boolean =
        world.getComponent(id, DamageComponent::class)?.isCritical == true

    /**
     * True when [shotId] is a piercing shot that has already passed through [targetId], and false
     * for everything else - including the first frame of a pierce, which it records on the way.
     */
    private fun hasAlreadyPierced(shotId: EntityId, targetId: EntityId): Boolean {
        val pierce = world.getComponent(shotId, PierceComponent::class) ?: return false
        if (collisionGroupOf(targetId) != CollisionGroup.ENEMY) return false
        return pierce.meet(targetId)
    }

    /**
     * Hurts [id] for [amount], shows what it cost over an enemy that took the hit, and blows up
     * whatever the hit destroyed.
     *
     * Only enemies get a number. An obstacle is scenery being cleared rather than a target, and
     * what the bat itself has lost is already there to read on its health bar.
     *
     * @param dealtBy the entity on the other side of the collision, which is what decides whether
     *   a piercing shot spends a pierce here or is spent itself.
     * @return true if this hit is what killed it.
     */
    private fun damage(id: EntityId, amount: Int, dealtBy: EntityId): Boolean {
        // A shot with pierce left goes through rather than being stopped. Only enemies count:
        // scenery is what a shot is stopped by however sharp it has been made.
        val pierce = world.getComponent(id, PierceComponent::class)
        if (pierce != null && collisionGroupOf(dealtBy) == CollisionGroup.ENEMY && pierce.spend()) {
            return false
        }

        if (absorbedByShield(id, amount)) return false

        val dealt = applyDamage(id, amount)
        if (dealt > 0 && collisionGroupOf(id) == CollisionGroup.ENEMY) {
            // Read off whatever landed the blow, not off the amount: a crit is a property of the
            // shot, and comparing the number against some threshold would call a heavily upgraded
            // ordinary shot critical.
            showDamageText(id, dealt, critical = isCritical(dealtBy))
            lightUp(id)
        }

        val died = dealt > 0 && world.getComponent(id, HealthComponent::class)?.alive == false
        // What is left behind depends on what died; see [burst], which is also what decides that
        // most things leave nothing at all.
        if (died) burst(id)
        return died
    }

    /**
     * Lets [id]'s shield take a hit of [amount], if it has one up, and says so over the enemy in
     * the shield's own color - so a player can see their shot was spent on the bubble, not wasted.
     *
     * @return true when the bubble took the hit, and nothing gets through to the enemy inside.
     */
    private fun absorbedByShield(id: EntityId, amount: Int): Boolean {
        if (amount <= 0 || collisionGroupOf(id) != CollisionGroup.ENEMY) return false
        if (world.getComponent(id, HealthComponent::class)?.alive != true) return false
        val shield = world.getComponent(id, ShieldComponent::class) ?: return false
        if (!shield.absorb(amount)) return false

        val rect = world.getComponent(id, TransformComponent::class)?.rect ?: return true
        factory.createDamageText(rect.left, rect.centerY, amount, color = shield.color)
        return true
    }

    /** A blast the size of whatever just died, so the boss goes out bigger than its escort. */
    private fun burst(id: EntityId) {
        val rect = world.getComponent(id, TransformComponent::class)?.rect ?: return
        val graphics = env.assets.graphics

        // What died decides what is left behind, and the two are deliberately different events.
        // An enemy burns; a spire of limestone breaks. Sharing one effect between them said the
        // obstacle had been detonated, in a cave where nothing is flammable.
        //
        // Everything else is left alone. A blast on every shot that lands would bury a tough enemy
        // behind its own hit effects, and the bat's death has an animation of its own.
        val (pixmap, spawn) = when (collisionGroupOf(id)) {
            CollisionGroup.ENEMY -> graphics.explosion to factory::createExplosion
            CollisionGroup.OBSTACLE -> graphics.shatter to factory::createShatter
            else -> return
        }

        // Never smaller than the artwork was drawn: an ordinary enemy keeps the blast it always
        // had, and only something bigger than one scales the effect up.
        spawn(rect.centerX, rect.centerY, pixmap, (rect.height / pixmap.height).coerceAtLeast(1f))
    }

    /**
     * Takes [amount] off [targetId]'s health and kills it at zero, returning what actually landed.
     *
     * Nothing lands on something with no health to lose, on something already dead, or on a player
     * still inside the cooldown that follows their last hit.
     */
    private fun applyDamage(targetId: EntityId, amount: Int): Int {
        val health = world.getComponent(targetId, HealthComponent::class) ?: return 0
        if (!health.alive) return 0

        var incoming = amount

        // The bat is the only entity with a cooldown: without one a single obstacle would strip the
        // whole bar over the frames the two sprites spend overlapping. How long that cooldown runs
        // and how much of the hit gets through are both run stats the power-ups raise.
        val control = world.getComponent(targetId, PlayerControlComponent::class)
        if (control != null) {
            if (control.hitCooldown > 0f) return 0
            control.hitCooldown = loadout.hitCooldownSeconds
            scoring.registerPlayerHit()

            // The flat cut comes off first and the armor scales what survives it, so the two
            // stack the way a player would expect rather than one swallowing the other. Rounded up
            // and floored at one: no amount of either can make a hit free, which would leave a run
            // the player cannot lose.
            incoming = ((incoming - loadout.flatDamageReduction) * loadout.damageTaken)
                .roundToInt()
                .coerceAtLeast(1)

            // Vibrate on hit
            env.haptics.vibrate(HIT_VIBRATION_MILLIS)
        }

        // Capped at what is left, so an overkill reports the damage the target could actually take.
        val dealt = minOf(incoming, health.hitPoints)
        health.hitPoints -= dealt
        if (health.hitPoints <= 0) {
            // The damage still landed and is still reported: a revive is the bat surviving a blow
            // that would have killed it, not the blow never happening.
            if (control != null && revive(health)) return dealt

            health.hitPoints = 0
            health.alive = false
            if (control != null) {
                beginDeath(targetId)
                endRun()
            }
            if (targetId == enmGen.bossId) completeStage()
        }
        return dealt
    }

    /**
     * Spends a Second Life, if the run has one, putting the bat back on its feet at half a bar.
     *
     * Half rather than full because a free death should keep a run alive, not undo the damage that
     * ended it - and the mercy window is reset alongside, or the same enemy would take the new
     * health off before the player's hand had moved.
     */
    private fun revive(health: HealthComponent): Boolean {
        if (!loadout.useRevive()) return false

        health.hitPoints =
            (health.maxHitPoints * REVIVE_HEALTH_FRACTION).roundToInt().coerceAtLeast(1)
        world.getComponent(batId, PlayerControlComponent::class)?.hitCooldown =
            loadout.hitCooldownSeconds
        announce("SECOND LIFE")
        return true
    }

    /**
     * The stage's boss is down, so the stage is over.
     *
     * The run stops here rather than rolling on into a sixth minute of enemies: a boss that could
     * be beaten and then followed by more of the same would not be a boss. What is left on screen
     * is left alone - anything still in flight flies out on its own - and the player reads their
     * total off the overlay and taps out when they are ready.
     */
    private fun completeStage() {
        if (stageComplete) return
        stageComplete = true
        stageCompleteArmingTime = STAGE_COMPLETE_ARMING_SECONDS
        overlayTaps.reset()
        enmGen.clearBoss()
        scoring.awardStageCleared()
        // Banked even though the run ends here: the total is what the victory screen reports, and
        // a boss worth nothing would read as a boss that did not count.
        awardExperience(XP_PER_BOSS)
        saveHighscore()
        // Unlocked the moment it is earned, not when the player taps through: a player who quits
        // from the victory screen has still beaten the stage.
        nextStageId?.let { env.stageUnlocks.unlockAsync(it) }

        currentStage.music.apply {
            stop()
            isLooping = false
        }
    }

    /** The stage after this one, or null when this is the last. */
    private val nextStageId: Int?
        get() = (currentStage.id + 1).takeIf { env.assets.hasStageAfter(currentStage.id) }

    /** What a boss with phases announces on entering phase [phase]. */
    private fun bossPhaseBanner(phase: Int): String = if (phase >= 3) "QUEEN ENRAGED" else "SWARM CALLED"

    /** Puts [text] up over the run for [WAVE_BANNER_SECONDS], replacing whatever was there. */
    private fun announce(text: String) {
        bannerText = text
        bannerTime = WAVE_BANNER_SECONDS
    }

    /**
     * Puts the bat into its death throes: the limp sheet, played once, and a tumbling fall.
     *
     * The sprite and the animation are *replaced* rather than a second entity being spawned in the
     * bat's place. Everything already watching this entity - the screen's own health and position
     * checks, the aura, the wake - keeps watching the same one, and each of those systems already
     * knows to stop when its owner is dead. A stand-in would have meant teaching all of them about
     * a second bat.
     *
     * The one-shot animation is why [at.smiech.engine.ecs.LifetimeSystem] has to leave the player
     * alone when an animation finishes: without that, the bat would be deleted the instant its
     * death animation ended, halfway through the fall the player is meant to watch.
     */
    private fun beginDeath(batId: EntityId) {
        val sprite = world.getComponent(batId, SpriteComponent::class) ?: return
        val death = env.assets.graphics.batDeath

        world.addComponent(
            batId,
            SpriteComponent(death, srcWidth = BAT_FRAME_WIDTH, srcHeight = death.height),
        )
        world.addComponent(
            batId,
            AnimationComponent(
                BAT_FRAME_WIDTH,
                death.height,
                BAT_DEATH_FRAME_COUNT,
                BAT_DEATH_FRAME_SECONDS,
                isLooping = false,
            ),
        )
        world.addComponent(
            batId,
            DeathThroesComponent(
                gravity = DEATH_GRAVITY,
                terminalVelocity = DEATH_TERMINAL_VELOCITY,
                // Whichever way it was already turning stays the way it turns, so the tumble
                // carries on from the hit rather than starting over.
                spinDegreesPerSecond = DEATH_SPIN_DEGREES_PER_SECOND,
                puffInterval = DEATH_PUFF_INTERVAL_SECONDS,
            ),
        )
        // Kept off the previous sprite's rotation, which is zero for the bat but would not be for
        // anything that had been turned before it died.
        world.getComponent(batId, SpriteComponent::class)?.rotationDegrees = sprite.rotationDegrees
    }

    /** One of the pieces coming off the bat on its way down; see [DeathThroesComponent]. */
    private fun shedDeathPuff(dyingId: EntityId) {
        val rect = world.getComponent(dyingId, TransformComponent::class)?.rect ?: return
        factory.createExplosion(
            // Scattered over the sprite rather than centered on it, so the pieces come off the
            // whole animal instead of pulsing out of one point.
            centerX = rect.centerX + (random.nextFloat() - 0.5f) * rect.width,
            centerY = rect.centerY + (random.nextFloat() - 0.5f) * rect.height,
            pixmap = env.assets.graphics.explosion,
            scale = DEATH_PUFF_SCALE,
        )
    }

    /** Banks the score and hands playback over to the game over track. */
    private fun endRun() {
        saveHighscore()

        if (env.audioSettings.soundsEnabled) {
            env.assets.audio.deathSound.play(100f)
        }
        currentStage.music.apply {
            stop()
            isLooping = false
        }
        if (env.audioSettings.musicEnabled) {
            env.assets.audio.gameOverMusic.play()
        }
    }

    /**
     * Lights [enemyId] up for [HIT_FLASH_SECONDS], so a hit that lands is visible on the thing it
     * landed on rather than only in the number floating off it.
     *
     * Re-armed rather than added twice when the enemy is already lit: a second hit landing mid
     * flash restarts it, which is what makes sustained fire read as a burst of separate impacts
     * instead of one continuous glow.
     *
     * Fires on any damage an enemy takes, not only on damage from a shot. The alternative was to
     * light up only for projectiles, which would leave the bat ramming an enemy showing a damage
     * number and no flash - the two are one piece of feedback, and having them disagree about
     * whether a hit happened would read as a bug.
     */
    private fun lightUp(enemyId: EntityId) {
        val existing = world.getComponent(enemyId, HitFlashComponent::class)
        if (existing != null) {
            existing.remaining = existing.duration
            return
        }
        world.addComponent(enemyId, HitFlashComponent(HIT_FLASH_SECONDS, HIT_FLASH_COLOR))
    }

    /** Puts the number at the enemy's leading edge, which is the side the bat's shots arrive from. */
    private fun showDamageText(enemyId: EntityId, damage: Int, critical: Boolean) {
        val rect = world.getComponent(enemyId, TransformComponent::class)?.rect ?: return
        factory.createDamageText(rect.left, rect.centerY, damage, critical)
    }

    private fun initStats() {
        scoring.reset()
        readHighscore()
    }

    // A one-shot read: nothing outside this screen writes the highscore during a run, so there is
    // nothing to keep observing. Stays on the main thread, which is the only thread that touches
    // `highscore`.
    private fun readHighscore() = screenScope.launch {
        // Merged rather than assigned, in case this run has already beaten the stored value.
        highscore = maxOf(highscore, env.highscores.read())
    }

    override fun update(deltaTime: Float) {
        // Ahead of the pause controls, which would otherwise read the player's way out of a won
        // stage as a request to pause it.
        if (stageComplete) {
            handleStageCompleteControls(deltaTime)
            return
        }
        if (handlePauseControls(deltaTime)) return

        // A level up owed is a level up shown, before anything else moves: the pick is meant to
        // change the fight the player is in, not the one after it.
        if (offer.isEmpty() && pendingLevelUps > 0) openLevelUpOffer()
        if (offer.isNotEmpty()) {
            handlePowerUpChoice(deltaTime)
            return
        }

        if (stageNameDisplayTime > 0) {
            stageNameDisplayTime -= deltaTime
        }
        if (bannerTime > 0) {
            bannerTime -= deltaTime
        }

        tickTime += deltaTime
        while (tickTime > tick) {
            tickTime -= tick
            world.update(tick, game.input)
            // Only while the bat is still flying. The score is banked the moment it dies, but the
            // world ticks on through the fall that follows, and a point for each of those ticks
            // would be shown on the HUD and never saved.
            if (world.getComponent(batId, HealthComponent::class)?.alive == true) {
                scoring.awardSurvivalTick()
            }
            regenerate(tick)

            // The stage clock is the fixed tick, not the wall clock: a paused game is a paused
            // stage, and a slow frame costs the player no ground on the wave they are in.
            enmGen.update(tick)
            // Held back for the boss. The duel is fought in an open cave, because a boss pinning
            // the player against scenery they cannot outrun is a death with nothing to read in it.
            if (!enmGen.bossSpawned) obsGen.update(tick)
        }

        val health = world.getComponent(batId, HealthComponent::class)!!
        if (!health.alive) {
            val transform = world.getComponent(batId, TransformComponent::class)!!
            if (transform.rect.top > game.frameBufferHeight) {
                game.setScreen(GameOverScreen(game, env))
            }
        }
    }

    /** Draws the next owed level up, freezing the run until the player has taken something. */
    private fun openLevelUpOffer() {
        pendingLevelUps--
        offer = PowerUp.offer(loadout)
        offerArmingTime = POWER_UP_ARMING_SECONDS
        overlayTaps.reset()

        // A dialog with nothing to choose between would trap the run. Two of the power-ups scale
        // without a ceiling so this cannot happen, but a future one that forgets to say so would
        // otherwise lock the game rather than fail loudly.
        if (offer.isEmpty()) return

        if (currentStage.music.isPlaying) currentStage.music.pause()
    }

    /**
     * Reads a pick off the level up dialog: a tap on a card, or its number key.
     *
     * Armed on a delay for the same reason the pause overlay is - the player was steering with a
     * finger down when the level up landed, and the lift that follows is not a choice. There is no
     * way to dismiss this without picking: the pick is the reward, and a dialog that could be
     * waved away would just be a tax on the player who did not read it in time.
     */
    private fun handlePowerUpChoice(deltaTime: Float) {
        offerArmingTime -= deltaTime

        val input = game.input
        val controls = input?.controls
        // Consumed whether or not they can act yet, so the buffer does not hand the whole backlog
        // to the run the moment the dialog closes.
        // Only taps begun on the dialog. The finger that was steering when it opened is still
        // down, and its lift used to pick whatever card it happened to be over.
        val touches = overlayTaps.taps(input?.touchEvents.orEmpty())
        // CONFIRM picks the leftmost card: a game pad has no number keys, and its A button is the
        // only thing on it a player will reach for first.
        val confirmed = controls?.consumePress(GameButton.CONFIRM) == true
        val pressed = GameButton.CHOICES.map { controls?.consumePress(it) == true }

        if (offerArmingTime > 0f) return

        val byKey = pressed.indexOfFirst { it }.takeIf { it >= 0 }
            ?: 0.takeIf { confirmed }
        val chosen = byKey ?: touches.firstNotNullOfOrNull { cardIndexAt(it.x, it.y) }

        if (chosen != null && chosen in offer.indices) choosePowerUp(offer[chosen])
    }

    /** Which card covers ([x], [y]) in framebuffer pixels, or null for a tap that missed. */
    private fun cardIndexAt(x: Int, y: Int): Int? {
        if (y < POWER_UP_CARD_TOP || y > POWER_UP_CARD_TOP + POWER_UP_CARD_HEIGHT) return null
        return offer.indices.firstOrNull { index ->
            val left = cardLeft(index)
            x >= left && x <= left + POWER_UP_CARD_WIDTH
        }
    }

    /** The left edge of card [index], with the row of them centered on the framebuffer. */
    private fun cardLeft(index: Int): Int {
        val stride = POWER_UP_CARD_WIDTH + POWER_UP_CARD_GAP
        val rowWidth = offer.size * stride - POWER_UP_CARD_GAP
        return (game.frameBufferWidth - rowWidth) / 2 + index * stride
    }

    /** Takes the pick, makes the bat match, and hands the run back. */
    private fun choosePowerUp(powerUp: PowerUp) {
        powerUp.applyTo(loadout)
        applyLoadout()
        offer = emptyList()
        announce(powerUp.title)

        // Only if the bat is still flying: a level up banked by the shot that also killed the
        // player has no run left to go back to.
        val health = world.getComponent(batId, HealthComponent::class)
        if (health?.alive == true) startStageMusic()
    }

    /**
     * Pushes the run's earned stats onto the bat's components.
     *
     * One place rather than each power-up reaching into the world for itself: a power-up says what
     * the bat is now, and this is what makes it so.
     */
    private fun applyLoadout() {
        world.getComponent(batId, WeaponComponent::class)?.interval = loadout.shotIntervalSeconds
        scoring.bonusMultiplier = loadout.scoreMultiplier

        val health = world.getComponent(batId, HealthComponent::class) ?: return
        health.maxHitPoints = loadout.maxHitPoints
        // Capped at the bar rather than added blindly, so healing a nearly full bat is worth
        // whatever room is actually left in it.
        val heal = loadout.takePendingHeal()
        if (heal > 0) health.hitPoints = (health.hitPoints + heal).coerceAtMost(health.maxHitPoints)
    }

    /**
     * Puts back whatever Regeneration is owed this tick.
     *
     * Fractional health is carried rather than rounded, for the same reason the score bonus carries
     * its remainder: two health a second is well under a point per tick, and rounding each tick
     * would heal nothing at all. Only a living bat regenerates, and only up to its own bar.
     */
    private fun regenerate(deltaTime: Float) {
        if (loadout.healthRegenPerSecond <= 0f) return
        val health = world.getComponent(batId, HealthComponent::class) ?: return
        if (!health.alive || health.hitPoints >= health.maxHitPoints) {
            // Dropped rather than banked: health owed while the bar is already full would
            // otherwise pour out in one lump the instant the bat took its next hit.
            regenCarry = 0f
            return
        }

        regenCarry += loadout.healthRegenPerSecond * deltaTime
        val whole = regenCarry.toInt()
        if (whole <= 0) return
        regenCarry -= whole
        health.hitPoints = (health.hitPoints + whole).coerceAtMost(health.maxHitPoints)
    }

    /**
     * The stage is won. A tap or Confirm flies on to the next stage, where there is one; Back
     * leaves for the menu, and so does a tap on the last stage.
     *
     * Armed on a delay for the same reason the pause overlay is: the player was steering with a
     * finger down as the boss died, and the lift that follows is not them asking to leave.
     */
    private fun handleStageCompleteControls(deltaTime: Float) {
        stageCompleteArmingTime -= deltaTime

        val input = game.input
        // Read whether or not it can act on them, so the buffer does not hoard events.
        val tapped = overlayTaps.taps(input?.touchEvents.orEmpty()).isNotEmpty()
        val controls = input?.controls
        val confirmed = controls?.consumePress(GameButton.CONFIRM) == true
        val backed = controls?.consumePress(GameButton.BACK) == true

        if (stageCompleteArmingTime > 0f) return
        val next = nextStageId
        when {
            backed -> env.onExitToMenu()
            (tapped || confirmed) && next != null -> startStage(next)
            tapped || confirmed -> env.onExitToMenu()
        }
    }

    /**
     * Flies on to stage [id], on a fresh screen - which is the reset: a new run's score, a bat at
     * level 1, and no power-ups. The highscore was already banked when the boss went down.
     */
    private fun startStage(id: Int) {
        game.setScreen(GameScreen(game, env, id))
    }

    /**
     * Reads the pause controls and, while paused, holds the run still.
     *
     * @return true when the caller should skip this update entirely.
     */
    private fun handlePauseControls(deltaTime: Float): Boolean {
        val input = game.input
        val controls = input?.controls

        // Back pauses a running game and leaves a paused one. That second meaning is what makes
        // the Android back button safe to intercept: it still gets the player out, in two presses
        // rather than one, without a button the touch UI does not have.
        if (controls?.consumePress(GameButton.BACK) == true) {
            if (paused) {
                saveHighscore()
                env.onExitToMenu()
                return true
            }
            setPaused(true)
        }
        if (controls?.consumePress(GameButton.PAUSE) == true) {
            setPaused(!paused)
        }

        if (!paused) return false

        resumeArmingTime -= deltaTime
        // Read even when it cannot resume: the buffer is drained by reading it, and a pause spent
        // hoarding events would dump them all on the bat at once on the way back in.
        val tapped = overlayTaps.taps(input?.touchEvents.orEmpty()).isNotEmpty()
        if (tapped && resumeArmingTime <= 0f) setPaused(false)
        return true
    }

    private fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        if (value) {
            resumeArmingTime = RESUME_ARMING_SECONDS
            overlayTaps.reset()
            if (currentStage.music.isPlaying) currentStage.music.pause()
        } else {
            // Only the stage theme: once the bat is dead the game over track owns playback, and
            // resuming would put two tracks on top of each other.
            val health = world.getComponent(batId, HealthComponent::class)
            if (health?.alive == true) startStageMusic()
        }
    }

    private fun drawPauseOverlay() {
        g.apply {
            drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
            // No text measurement in the Graphics API, so these x offsets are eyeballed against
            // the 480px framebuffer rather than centered properly.
            drawString("PAUSED", 186, 140, 30, EngineColors.CYAN)
            drawString("Tap or click to resume", 170, 175, 15, EngineColors.WHITE)
            drawString("Back or Q to quit", 185, 197, 15, EngineColors.WHITE)
        }
    }

    fun saveHighscore() {
        if (scoring.score > highscore) {
            highscore = scoring.score
        }

        // Deliberately not on screenScope: this runs as the bat dies, moments before the screen is
        // swapped out and disposed, and the write has to survive that.
        env.highscores.saveAsync(highscore)
    }

    /**
     * The banner a new wave or the boss arrives on, held for [WAVE_BANNER_SECONDS].
     *
     * Outlined rather than plain, because it lands over whatever the run happens to be drawing,
     * and centered by eye against the 480px framebuffer like the rest of the overlays here - the
     * Graphics API has no way to measure a string.
     */
    private fun drawBanner(text: String) {
        g.drawOutlinedString(
            text,
            game.frameBufferWidth / 2 - text.length * BANNER_CHAR_WIDTH / 2,
            game.frameBufferHeight / 3,
            BANNER_FONT_SIZE,
            EngineColors.YELLOW,
        )
    }

    /**
     * What the player gets for clearing the stage: the run's total, and the way out.
     *
     * Drawn over the stage rather than on a screen of its own, so the last thing they see is the
     * cave they beat with the wreckage of the boss still clearing off it.
     */
    private fun drawStageCompleteOverlay() {
        g.apply {
            drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
            drawString("STAGE COMPLETE", 120, 130, 30, EngineColors.YELLOW)
            drawString(currentStage.name, 150, 160, 15, EngineColors.CYAN)
            drawString("Score: ${scoring.score}", 175, 185, 20, EngineColors.WHITE)
            if (nextStageId != null) {
                drawString("Tap or press Enter for stage ${nextStageId}", 135, 215, 15, EngineColors.WHITE)
                drawString("Back or Q for the menu", 170, 237, 15, EngineColors.WHITE)
                drawString("Score, level and power-ups start over", 118, 262, 13, EngineColors.CYAN)
            } else {
                drawString("Tap or press Enter to continue", 140, 215, 15, EngineColors.WHITE)
            }
        }
    }

    /**
     * The level up dialog: what the bat just reached, and the three things it can become.
     *
     * Cards are drawn rather than composed, because this screen owns a 480x320 framebuffer and has
     * no text measurement to lay anything out with - every offset here is eyeballed against that
     * frame, as the other overlays are. A card is its own tap target, and carries no number: what
     * it does is the whole of what the player needs to read. The number keys still pick by
     * position for anyone on a keyboard, which is a shortcut rather than the advertised way in.
     */
    private fun drawPowerUpOffer() {
        g.apply {
            drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
            drawString("LEVEL ${progress.level}", 190, 70, 30, EngineColors.YELLOW)
            drawString("Choose an upgrade", 168, 100, 15, EngineColors.WHITE)

            offer.forEachIndexed { index, powerUp ->
                drawPowerUpCard(index, powerUp)
            }
        }
    }

    private fun drawPowerUpCard(index: Int, powerUp: PowerUp) {
        val left = cardLeft(index)
        g.apply {
            // A filled panel behind the text, then a cyan lip along the top, so a card reads as a
            // thing to press rather than as words floating over the run.
            drawRect(left, POWER_UP_CARD_TOP, POWER_UP_CARD_WIDTH, POWER_UP_CARD_HEIGHT, CARD_FILL)
            drawRect(left, POWER_UP_CARD_TOP, POWER_UP_CARD_WIDTH, 2, EngineColors.CYAN)

            drawString(powerUp.title, left + 8, POWER_UP_CARD_TOP + 26, 14, EngineColors.CYAN)
            // Wrapped by hand for the same reason the layout is eyeballed: nothing here can
            // measure a string, so the description is broken on whole words at a fixed width.
            wrapped(powerUp.description, CARD_TEXT_CHARS).forEachIndexed { line, text ->
                drawString(
                    text,
                    left + 8,
                    POWER_UP_CARD_TOP + 48 + line * 14,
                    11,
                    EngineColors.WHITE
                )
            }
        }
    }

    /** Greedy word wrap at [chars] per line, which is all the card layout needs. */
    private fun wrapped(text: String, chars: Int): List<String> {
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > chars) {
                lines += line.toString()
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    /**
     * The experience bar, across the very top edge.
     *
     * Up there because it is the one strip of the frame nothing else uses - the HUD text starts
     * below it and the bat never reaches it - and because a bar the player reads out of the corner
     * of their eye is the point: it says how close the next choice is without asking to be looked
     * at.
     */
    private fun drawExperienceBar() {
        val filled = (game.frameBufferWidth * progress.fraction).roundToInt()
        g.drawRect(0, 0, game.frameBufferWidth, XP_BAR_HEIGHT, XP_BAR_EMPTY)
        if (filled > 0) g.drawRect(0, 0, filled, XP_BAR_HEIGHT, EngineColors.CYAN)
    }

    override fun present(deltaTime: Float) {
        g.clear(EngineColors.BLACK)
        world.draw(g)
        drawStats()
        drawExperienceBar()
        if (stageNameDisplayTime > 0) {
            drawStageName()
        }
        bannerText?.takeIf { bannerTime > 0 }?.let { drawBanner(it) }

        if (offer.isNotEmpty()) drawPowerUpOffer()
        if (stageComplete) drawStageCompleteOverlay()
        if (paused) drawPauseOverlay()
    }

    /**
     * The text HUD. Health is deliberately not part of it: it rides under the bat, where the
     * player is already looking.
     */
    private fun drawStats() {
        g.apply {
            drawString("Score: ${scoring.score}", 5, 20, 15, EngineColors.CYAN)
            drawString("Highscore: $highscore", 5, 40, 15, EngineColors.CYAN)
            // Always shown, even at x1: a multiplier the player only sees once they have
            // earned it is a mechanic they never learn exists.
            val multiplier = scoring.multiplier
            val comboColor = if (multiplier > 1) EngineColors.YELLOW else EngineColors.CYAN
            drawString("Combo: x$multiplier", 5, 60, 15, comboColor)
            // How far into the stage the player is, which is the only reading they get on how
            // much harder the next minute is about to be - and on how close the boss is.
            drawString(waveLabel(), 5, 80, 15, EngineColors.CYAN)
            // The other half of that race: how much stronger the bat has got while the cave was
            // getting harder. The bar across the top edge is the fine detail; this is the count.
            drawString("Level: ${progress.level}", 5, 100, 15, EngineColors.YELLOW)
        }
    }

    /**
     * The wave readout: which minute the player is in, or that the boss is here.
     *
     * Waves are numbered from one for the player, where the code indexes them from zero.
     */
    private fun waveLabel(): String = when {
        enmGen.bossSpawned -> "BOSS"
        else -> "Wave: ${enmGen.currentWave.index + 1}/${progression.bossWave}"
    }

    private fun drawStageName() {
        g.drawString(
            currentStage.name,
            game.frameBufferWidth / 4,
            game.frameBufferHeight / 2,
            30,
            EngineColors.YELLOW
        )
    }

    /** Starts or resumes the stage theme, if music is enabled. */
    private fun startStageMusic() {
        if (!env.audioSettings.musicEnabled) return
        currentStage.music.apply {
            isLooping = true
            play()
        }
    }

    /**
     * The host going away pauses the run outright, not just its music. The player is not at the
     * controls, and a game that carries on the moment the window comes back costs them a life
     * before they have looked at it.
     */
    override fun pause() {
        setPaused(true)
    }

    /**
     * Deliberately does not clear [paused]: coming back to the app should not drop the player
     * straight into a dodge. They resume when they are ready.
     */
    override fun resume() {
        // The run survives untouched - only playback needs restoring, and only if the player had
        // not paused by hand. After death the game over music owns playback, so leave it alone.
        if (paused) return
        val health = world.getComponent(batId, HealthComponent::class)
        if (health?.alive == true) {
            startStageMusic()
        }
    }

    override fun dispose() {
        screenScope.cancel()
    }

    private companion object {
        /** A power-up card's panel: dark enough to read white text on, over a dimmed run. */
        const val CARD_FILL = 0xE6101820.toInt()

        /** Roughly what fits on a card at 11px, counted rather than measured; see [wrapped]. */
        const val CARD_TEXT_CHARS = 22

        /** The unfilled part of the experience bar. */
        const val XP_BAR_EMPTY = 0x80000000.toInt()

        const val DEGREES_PER_RADIAN = 57.29578f
    }
}
