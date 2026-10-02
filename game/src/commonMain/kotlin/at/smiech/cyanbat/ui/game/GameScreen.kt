package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.ScoreTracker
import at.smiech.cyanbat.ecs.BackgroundScrollingSystem
import at.smiech.cyanbat.ecs.BossPartComponent
import at.smiech.cyanbat.ecs.EliteComponent
import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.NightfallSystem
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.Volley
import at.smiech.cyanbat.music.MusicDirector
import at.smiech.cyanbat.progress.PlayerLoadout
import at.smiech.cyanbat.progress.PlayerProgress
import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.resource.Backdrop
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.SoundEffect
import at.smiech.cyanbat.scenery.Daylight
import at.smiech.cyanbat.service.BossKind
import at.smiech.cyanbat.service.EnemyGenerator
import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.service.ObstacleGenerator
import at.smiech.cyanbat.service.StageProgression
import at.smiech.cyanbat.util.BANNER_FONT_SIZE
import at.smiech.cyanbat.util.BAT_DEATH_FRAME_COUNT
import at.smiech.cyanbat.util.BAT_DEATH_FRAME_SECONDS
import at.smiech.cyanbat.util.BAT_FRAME_WIDTH
import at.smiech.cyanbat.util.BAT_LIGHT_FADE_SECONDS
import at.smiech.cyanbat.util.BOSS_AFTERSHOCK_FALLOFF
import at.smiech.cyanbat.util.BOSS_AFTERSHOCK_SCALE
import at.smiech.cyanbat.util.BOSS_AFTERSHOCK_SECONDS
import at.smiech.cyanbat.util.BURST_DRIFT
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.DEATH_GRAVITY
import at.smiech.cyanbat.util.DEATH_PUFF_INTERVAL_SECONDS
import at.smiech.cyanbat.util.DEATH_PUFF_SCALE
import at.smiech.cyanbat.util.DEATH_SPIN_DEGREES_PER_SECOND
import at.smiech.cyanbat.util.DEATH_TERMINAL_VELOCITY
import at.smiech.cyanbat.util.HIT_FLASH_COLOR
import at.smiech.cyanbat.util.HIT_FLASH_SECONDS
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
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
import at.smiech.cyanbat.util.STAGE_COMPLETE_ARMING_SECONDS
import at.smiech.cyanbat.util.STAGE_COMPLETE_DELAY_SECONDS
import at.smiech.cyanbat.util.STAGE_TIMER_FONT_SIZE
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.TRAIL_SEGMENT_HEIGHT_FRACTION
import at.smiech.cyanbat.util.TRAIL_SEGMENT_WIDTH_FRACTION
import at.smiech.cyanbat.util.VICTORY_FANFARE_DELAY_SECONDS
import at.smiech.cyanbat.util.WAVE_BANNER_SECONDS
import at.smiech.cyanbat.util.WOUNDED_FIRE_RATE
import at.smiech.cyanbat.util.WOUNDED_PACE
import at.smiech.cyanbat.util.XP_BAR_HEIGHT
import at.smiech.cyanbat.util.XP_PER_BOSS
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.Graphics
import at.smiech.engine.LayeredMusic
import at.smiech.engine.Music
import at.smiech.engine.Screen
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
import at.smiech.engine.ecs.LightComponent
import at.smiech.engine.ecs.LightingSystem
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.PaceComponent
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
import at.smiech.engine.ecs.WoundSystem
import at.smiech.engine.math.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.pow
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
    private val factory = EntityFactory(world, lit = currentStage.lighting != null)

    /**
     * Canceled in [dispose], so nothing started here outlives the screen. On the main dispatcher,
     * which is the thread the game loop runs on, so what a coroutine here writes needs no locking.
     */
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val batId: EntityId

    private val scoring = ScoreTracker()

    /** The burning combo readout in the HUD; see [ComboMeter]. */
    private val comboMeter = ComboMeter()

    /** The highscore of the stage being flown, raised to this run's score whenever it is banked. */
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
        onBossSpawned = {
            announce(progression.design.bossName)
            director?.onBossArrived()
        },
        bossPixmap = currentStage.bossSheet,
        onBossPhaseChanged = { phase ->
            announce(bossPhaseBanner(phase))
            director?.onBossPhaseChanged()
        },
    )
    var obsGen = ObstacleGenerator(
        worldWidth = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        currentStage
    )

    /**
     * The stage's music, opened the first time it is played - which for a run with music turned
     * off is never, so that run reads no stems at all. Its own, not shared through the assets,
     * because it goes with the run: disposing the screen disposes it.
     */
    private var music: LayeredMusic? = null

    /** Turns the run into the music's layers; see [MusicDirector]. Opened with [music]. */
    private var director: MusicDirector? = null

    /**
     * The fanfare of a won stage, opened as it is first played; see [GameAssets.VICTORY_MUSIC].
     * Its own, like [music], and disposed with the screen.
     */
    private var fanfare: Music? = null

    /** Set when a pause cut the fanfare short, so that resuming carries it on; see [setPaused]. */
    private var fanfareHeld = false

    /** The run's sound effects; see [SoundBoard]. */
    private val sounds = SoundBoard(env.assets.audio.effects) { env.audioSettings.soundsEnabled }

    private lateinit var g: Graphics
    private var stageNameDisplayTime = 3.0f

    /** The wave or boss announcement currently on screen, and what is left of its time. */
    private var bannerText: String? = null
    private var bannerTime = 0f

    /**
     * Set when the stage's boss goes down. The run is over, but won rather than lost: it plays on
     * for [STAGE_COMPLETE_DELAY_SECONDS] (see [playOutVictory]), and then the player reads their
     * score off a screen they earned.
     */
    private var stageComplete = false

    /** Time since the boss went down; the run's own time, so a pause holds it. */
    private var victorySeconds = 0f

    /** The stage complete overlay is up: the boss is down, and the run has played out after it. */
    private val stageCompleteShown: Boolean
        get() = stageComplete && victorySeconds >= STAGE_COMPLETE_DELAY_SECONDS

    /**
     * Where the boss was when it went down, piece by piece - one piece for a boss that is one
     * entity, and every one in sight for one with a body - for the aftershocks to go off in. Moved
     * on with the blasts every tick, since the wreck is in the world, not on the screen.
     */
    private val wreck = mutableListOf<Rect>()

    /** Time before the victory overlay will accept a tap as "done"; see [handleStageCompleteControls]. */
    private var stageCompleteArmingTime = 0f

    /**
     * The stage clock at the moment the run ended, won or lost, which is where the timer stops.
     * Null while the run is still being flown.
     *
     * The clock itself, [EnemyGenerator.elapsedSeconds], is not stopped by a death: the cave carries
     * on around the bat as it falls. A timer still counting over a dead bat would be timing a run
     * that is already over.
     */
    private var finalStageSeconds: Float? = null

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
        // A sky that turns from day to night is the back of the picture, so it goes first: every
        // system after it draws on top. Its update puts the obstacles in the same light on the same
        // tick, and reads the stage clock as it stands after the tick before, which is where the
        // generator leaves it.
        val backdrop = currentStage.backdrop
        if (backdrop is Backdrop.Nightfall) {
            world.addSystem(
                NightfallSystem(
                    backdrop,
                    game.frameBufferWidth,
                    game.frameBufferHeight,
                    dayPosition = {
                        Daylight.position(
                            enmGen.elapsedSeconds,
                            progression.bossTimeSeconds
                        )
                    },
                )
            )
        }
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
        // After the collisions that land the hits, so the blow that takes something past a mark
        // shows on the frame it lands - and slows it from the next. The Sand Wyrm's brain reads the
        // head's row off it later in the same tick, to draw the body from.
        world.addSystem(WoundSystem { id, row -> slowWounded(id, row) })
        // With the height too: enemy fire is aimed and fanned now, and leaves through the top and
        // bottom as well as the sides.
        world.addSystem(LifetimeSystem(game.frameBufferWidth, game.frameBufferHeight))
        // The scenery strip of the cave and the forest, on a pass of its own under every other
        // sprite: it is a sprite itself, and drawn in one pass with the rest it went down over every
        // halo, so no aura in either stage ever showed one.
        world.addSystem(RenderSystem(layers = Int.MIN_VALUE until SPRITE_LAYERS_FROM))
        // Over the scenery and under the sprites, so the halo is light coming off whatever wears it
        // rather than a wash over it. This is also the pass that advances the aura's clock; see
        // [AuraSystem.Layer].
        world.addSystem(AuraSystem(AuraSystem.Layer.HALO))
        // The sprites in two passes, with the dark of a stage flown in the dark between them: under it
        // the obstacles and everything hostile, as lit as whatever light reaches them; over it the
        // shots, the bat and the blasts, each a light itself or the heart of one, and as bright as it
        // is drawn. The dark goes over the scenery and the halos too, and under every effect after
        // it. In daylight the two passes draw what one would.
        world.addSystem(RenderSystem(layers = SPRITE_LAYERS_FROM until LIGHTS_FROM))
        currentStage.lighting?.let {
            world.addSystem(LightingSystem(game.frameBufferWidth, game.frameBufferHeight, it.ambient, it.glow))
        }
        world.addSystem(RenderSystem(layers = LIGHTS_FROM..Int.MAX_VALUE))
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

        // A strip is the first tile of the scrolling scenery, which BackgroundScrollingSystem keeps
        // topped up from here. A sky needs nothing laid down: its system draws it whole.
        if (backdrop is Backdrop.Strip) factory.createBackground(0f, backdrop.pixmap)

        batId = factory.createBat(
            x = (game.frameBufferWidth / 3).toFloat(),
            y = (game.frameBufferHeight / 2).toFloat(),
            width = 45f,
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
        // Once the stage is won nothing fires. Nothing hostile is left to fire or to be fired at,
        // and the bat's gun going on under the fanfare would be a sound with no purpose.
        if (stageComplete) return
        val transform = world.getComponent(shooterId, TransformComponent::class) ?: return
        val isPlayer = world.hasComponent(shooterId, PlayerControlComponent::class)
        // An enemy holds its fire until it is on screen. A volley fired from past the right edge
        // would arrive out of nowhere, and nothing the player could see would have warned them -
        // and nor would one from under the sand, where an elite wyrmling cruises in armed.
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

        for (angle in spreadAngles(if (isPlayer) 1 + loadout.extraShots else 1)) {
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
        // per projectile would make a five-way fan five times as loud as a single shot. An enemy's
        // gun has a voice of its own, darker and well under the bat's, so the gun the player is
        // operating is still the one they hear.
        sounds.play(if (isPlayer) SoundEffect.SHOT else SoundEffect.ENEMY_SHOT)
    }

    /**
     * Wholly inside the frame across, and with its middle inside it from top to bottom. Looser up
     * and down, because a swarm or a diver routinely dips part of itself past the top or the bottom
     * and is still plainly there; what is ruled out is firing from where nothing can be seen.
     */
    private fun isOnScreen(rect: Rect): Boolean =
        rect.left >= 0f && rect.right <= game.frameBufferWidth &&
                rect.centerY >= 0f && rect.centerY <= game.frameBufferHeight

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
        // Once for the volley, however many shots are in it, as for a straight bolt.
        sounds.play(SoundEffect.ENEMY_SHOT)
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
                val enemyId = if (group1 == CollisionGroup.ENEMY) id1 else id2
                val elite = world.hasComponent(enemyId, EliteComponent::class)
                scoring.registerEnemyDestroyed(elite)
                // The boss is banked by completeStage, which knows it was the boss - and a part of
                // its body going down is the boss going down. Everything else is worth what its
                // wave is worth, and an elite several times that.
                if (enemyId != bossId && !isBossPart(enemyId)) {
                    awardExperience(PlayerProgress.experienceForKill(enmGen.currentWave.index, elite))
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
        sounds.play(SoundEffect.AURA_SURGE)
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

        // A shot that hits a part of a boss's body lands on the boss, and is shown - number and
        // flash - on the part it hit, which is where the player was aiming. The bat flying into it
        // lands nothing. The body sweeping over the bat is the boss's attack, and every tick of the
        // overlap, part by part, would otherwise hand the boss a hit: one pass through the bat
        // would cost it a third of its health. See [BossPartComponent].
        val part = world.getComponent(id, BossPartComponent::class)
        if (part != null && collisionGroupOf(dealtBy) != CollisionGroup.PLAYER_PROJECTILE) return false
        val target = if (part != null) enmGen.bossId ?: return false else id
        // A plate is armor and passes on only its share; see [BossPartComponent.share].
        val landing =
            if (part != null) (amount * part.share).roundToInt().coerceAtLeast(1) else amount

        if (absorbedByShield(target, landing)) return false

        val dealt = applyDamage(target, landing)
        if (dealt > 0 && collisionGroupOf(id) == CollisionGroup.ENEMY) {
            // Read off whatever landed the blow, not off the amount: a crit is a property of the
            // shot, and comparing the number against some threshold would call a heavily upgraded
            // ordinary shot critical.
            showDamageText(id, dealt, critical = isCritical(dealtBy))
            lightUp(id)
        }

        val died = dealt > 0 && world.getComponent(target, HealthComponent::class)?.alive == false
        if (died) {
            // What is left behind depends on what died; see [burst], which is also what decides
            // that most things leave nothing at all.
            burst(target)
        } else if (dealt > 0 && collisionGroupOf(dealtBy) == CollisionGroup.PLAYER_PROJECTILE) {
            // A shot that lands is heard landing, in an enemy or in a rock it is wearing down. One
            // that kills is heard in what it killed instead.
            sounds.play(SoundEffect.HIT)
        }
        return died
    }

    private fun isBossPart(id: EntityId): Boolean = world.hasComponent(id, BossPartComponent::class)

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

        // A ping rather than a hit's thump, so the player hears as well as sees that nothing got
        // through.
        sounds.play(SoundEffect.SHIELD_HIT)
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
        // Nothing else leaves a blast. A blast on every shot that lands would bury a tough enemy
        // behind its own hit effects, and the bat's death has an animation of its own. A shot that
        // gives off light leaves a flash of it where it was spent, though: in the dark that is the
        // moment the player sees what it struck.
        val (pixmap, spawn, sound) = when (collisionGroupOf(id)) {
            CollisionGroup.ENEMY ->
                Triple(graphics.explosion, factory::createExplosion, SoundEffect.ENEMY_DEATH)
            CollisionGroup.OBSTACLE ->
                Triple(graphics.shatter, factory::createShatter, SoundEffect.OBSTACLE_SHATTER)
            CollisionGroup.PLAYER_PROJECTILE, CollisionGroup.ENEMY_PROJECTILE -> {
                world.getComponent(id, LightComponent::class)
                    ?.let { factory.createFlash(rect.centerX, rect.centerY, it.color) }
                return
            }
            else -> return
        }

        // Never smaller than the artwork was drawn: an ordinary enemy keeps the blast it always
        // had, and only something bigger than one scales the effect up.
        spawn(rect.centerX, rect.centerY, pixmap, (rect.height / pixmap.height).coerceAtLeast(1f))

        // Not once the stage is won. The boss's own blast was played as it went down, and it is
        // the sound of the boss and of everything that goes up with it; see [completeStage].
        if (!stageComplete) sounds.play(sound)
    }

    /**
     * Takes [amount] off [targetId]'s health and kills it at zero, returning what actually landed.
     *
     * Nothing lands on something with no health to lose, on something already dead, or on a player
     * still inside the cooldown that follows their last hit - or on one who has won the stage.
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
            // Everything hostile went down with the boss, but not before the end of the tick: the
            // rest of the collision pass the boss died in still sees it. A stage won is won, and the
            // bat flies out the seconds after it untouchable.
            if (stageComplete || control.hitCooldown > 0f) return 0
            control.hitCooldown = loadout.hitCooldownSeconds
            scoring.registerPlayerHit()
            director?.onPlayerHit()

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
            if (control != null && revive(health)) {
                sounds.play(SoundEffect.BAT_HIT)
                return dealt
            }

            health.hitPoints = 0
            health.alive = false
            if (control != null) {
                beginDeath(targetId)
                endRun()
            }
            if (targetId == enmGen.bossId) completeStage()
        } else if (control != null) {
            // Not on the blow that kills it, which the death sound speaks for.
            sounds.play(SoundEffect.BAT_HIT)
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
     * be beaten and then followed by more of the same would not be a boss. Everything hostile goes
     * down with it, the run plays out a few seconds more - the wreck going up, the fanfare coming in
     * (see [playOutVictory]) - and then the player reads their total off the overlay and taps out
     * when they are ready.
     */
    private fun completeStage() {
        if (stageComplete) return
        stageComplete = true
        finalStageSeconds = enmGen.elapsedSeconds
        // Read before the boss is forgotten: the aftershocks go off where it was.
        enmGen.bossId?.let { world.getComponent(it, TransformComponent::class)?.rect }
            ?.takeIf(::inSight)
            ?.let(wreck::add)
        explodeBossBody()
        clearHostiles()
        enmGen.clearBoss()
        scoring.awardStageCleared()
        // Banked even though the run ends here: the total is what the victory screen reports, and
        // a boss worth nothing would read as a boss that did not count.
        awardExperience(XP_PER_BOSS)
        // The highscore is not banked yet. This runs partway through a tick, and the score moves
        // on before the tick is over; update banks it once it is.
        // Unlocked the moment it is earned, not when the player taps through: a player who quits
        // from the victory screen has still beaten the stage.
        nextStageId?.let { env.stageUnlocks.unlockAsync(it) }

        // The stage's music stops dead on the kill, and the blast fills the silence it leaves. The
        // victory's fanfare takes over once the blast has had its moment; see [playOutVictory].
        music?.pause()
        sounds.play(SoundEffect.BOSS_DEATH)
        // Its name went up as it arrived, and goes up again as it falls - held until the overlay
        // takes over, so the seconds between are seen to be the victory and not the game hanging.
        announce("${progression.design.bossName} FALLS", seconds = STAGE_COMPLETE_DELAY_SECONDS)
    }

    /**
     * A boss with a body goes up all along it, not only where the killing blow landed: every part
     * bursts where it is and is taken off. Only its brain ever removed them, and the brain goes with
     * the boss, so this is the one other place that does.
     */
    private fun explodeBossBody() {
        for (part in world.query(BossPartComponent::class, TransformComponent::class)) {
            // The boss itself went up where the killing blow landed, and the health cull takes it.
            if (part == enmGen.bossId) continue
            val rect = world.getComponent(part, TransformComponent::class)?.rect ?: continue
            // Only the ones in sight: a part still under the sand has nothing to show for it.
            if (inSight(rect)) {
                val blast = env.assets.graphics.explosion
                factory.createExplosion(
                    rect.centerX,
                    rect.centerY,
                    blast,
                    rect.height / blast.height * BODY_BLAST_SCALE
                )
                wreck += rect
            }
            world.removeEntity(part)
        }
    }

    /**
     * Everything hostile goes down with its boss: whatever it called in goes up where it is, and
     * every shot still in the air is gone. The run plays on for a few seconds after the boss, and
     * nothing in them should be left to hurt the bat, or to need shooting.
     */
    private fun clearHostiles() {
        for (id in world.query(CollisionComponent::class, TransformComponent::class)) {
            val group = collisionGroupOf(id)
            if (group == CollisionGroup.ENEMY_PROJECTILE) world.removeEntity(id)
            // The boss and its body have gone up already, where they were.
            if (group != CollisionGroup.ENEMY || id == enmGen.bossId || isBossPart(id)) continue
            // Only what is in sight goes up. A blast off the edge would drift into the frame with
            // nothing behind it, and one under the sand would never be seen at all.
            if (world.getComponent(id, TransformComponent::class)?.rect?.let(::inSight) == true) burst(id)
            world.removeEntity(id)
        }
    }

    /** Whether any of [rect] is inside the frame. */
    private fun inSight(rect: Rect): Boolean =
        rect.right > 0f && rect.left < game.frameBufferWidth &&
                rect.bottom > 0f && rect.top < game.frameBufferHeight

    /** The stage after this one, or null when this is the last. */
    private val nextStageId: Int?
        get() = (currentStage.id + 1).takeIf { env.assets.hasStageAfter(currentStage.id) }

    /** What a boss with phases announces on entering phase [phase]. */
    private fun bossPhaseBanner(phase: Int): String = when (progression.design.boss) {
        BossKind.SAND_WYRM -> if (phase >= 3) "WYRM ENRAGED" else "THE BROOD RISES"
        else -> if (phase >= 3) "QUEEN ENRAGED" else "SWARM CALLED"
    }

    /** Puts [text] up over the run for [seconds], replacing whatever was there. */
    private fun announce(text: String, seconds: Float = WAVE_BANNER_SECONDS) {
        bannerText = text
        bannerTime = seconds
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
        // In the dark, its light goes out as it falls, rather than going over the bottom edge with it.
        world.getComponent(batId, LightComponent::class)?.let { light ->
            world.addComponent(
                batId,
                LightComponent(light.color, light.radius, light.strength, fadeSeconds = BAT_LIGHT_FADE_SECONDS),
            )
        }
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

    /** Banks the score, stops the timer, and hands playback over to the game over track. */
    private fun endRun() {
        saveHighscore()
        finalStageSeconds = enmGen.elapsedSeconds

        sounds.play(SoundEffect.BAT_DEATH)
        music?.pause()
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

    /**
     * Sets a wounded enemy's pace to what its [row] of wounds leaves it; see [WOUNDED_PACE] and
     * [WOUNDED_FIRE_RATE].
     *
     * Only ordinary enemies carry a pace, so the bat and the bosses go on at full pace however hurt
     * they look. Called on the way back up a row too, which nothing hostile does yet, so the pace
     * would follow a heal the way the picture does.
     */
    private fun slowWounded(id: EntityId, row: Int) {
        val pace = world.getComponent(id, PaceComponent::class) ?: return
        pace.motion = WOUNDED_PACE[row]
        pace.fire = WOUNDED_FIRE_RATE[row]
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
        highscore = maxOf(highscore, env.highscores.read(currentStage.id))
    }

    override fun update(deltaTime: Float) {
        // Ahead of everything that returns early. The music plays on under the level up dialog,
        // and what it does there - muffled - is the point.
        steerMusic(deltaTime)
        // The clock the sound effects are held apart by. It can run on through a pause, since
        // nothing plays during one.
        sounds.advance(deltaTime)

        // Ahead of the pause controls, which would otherwise read the player's way out of a won
        // stage as a request to pause it. A pause the host made while it was up is still answered
        // by them, so the player can tap their way back to it.
        if (stageCompleteShown && !paused) {
            handleStageCompleteControls(deltaTime)
            return
        }
        if (handlePauseControls(deltaTime)) return
        if (stageComplete) {
            playOutVictory(deltaTime)
            return
        }

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
            regenerate(tick)
            // After the world, so a kill or a hit in this tick's collisions shows on this tick.
            comboMeter.update(tick, scoring.hitStreak, scoring.multiplier)

            // The stage clock is the fixed tick, not the wall clock: a paused game is a paused
            // stage, and a slow frame costs the player no ground on the wave they are in.
            enmGen.update(tick)
            // Held back for the boss. The duel is fought in an open cave, because a boss pinning
            // the player against scenery they cannot outrun is a death with nothing to read in it.
            if (!enmGen.bossSpawned) obsGen.update(tick)

            // The score of a won stage is banked here, once the tick that won it has resolved,
            // rather than as the boss goes down. That happens partway through the collision pass,
            // and the score still moves after it: the kill itself is counted, and so is any later
            // kill in the same pass. The overlay shows the record and the score side by side, where
            // a record lower than the score beside it reads as a bug. The frame's remaining ticks
            // are dropped: from the next frame the run plays out its victory instead, where nothing
            // is left that scores; see [playOutVictory].
            if (stageComplete) {
                saveHighscore()
                break
            }
        }

        val health = world.getComponent(batId, HealthComponent::class)!!
        if (!health.alive) {
            val transform = world.getComponent(batId, TransformComponent::class)!!
            if (transform.rect.top > game.frameBufferHeight) {
                // Banked again on the way out. The score can still move while the bat falls - a
                // shot already in flight can land a kill - and the screen that follows shows the
                // two side by side, where a record lower than the score beside it reads as a bug.
                saveHighscore()
                game.setScreen(GameOverScreen(game, env, scoring.score, highscore))
            }
        }
    }

    /** Draws the next owed level up, freezing the run until the player has taken something. */
    private fun openLevelUpOffer() {
        pendingLevelUps--
        offer = PowerUp.offer(loadout)
        offerArmingTime = POWER_UP_ARMING_SECONDS
        overlayTaps.reset()
        // The music is not paused for this. It plays on under a muffle while the offer is up,
        // which [steerMusic] reads off the offer itself; an empty one - which the uncapped
        // power-ups rule out - is never shown, and so never muffles anything either.
    }

    /** Hands the director where the run stands, once a frame; see [MusicDirector]. */
    private fun steerMusic(deltaTime: Float) {
        director?.update(
            deltaTime,
            waveIndex = enmGen.currentWave.index,
            comboMultiplier = scoring.multiplier,
            bossFight = enmGen.bossSpawned,
            suspended = offer.isNotEmpty(),
        )
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
     * The seconds between the boss going down and the overlay coming up, which the run plays on
     * through, so the boss is seen going up rather than frozen in its first frame of fire.
     *
     * The bat still flies, though nothing is left for it to shoot or to be hurt by: everything
     * hostile went down with the boss ([clearHostiles]). The wreck goes up again on each of
     * [BOSS_AFTERSHOCK_SECONDS], the fanfare comes in at [VICTORY_FANFARE_DELAY_SECONDS], and the
     * overlay lands on its last chord. The stage clock holds where the boss left it, nothing new
     * arrives, and no level up is offered: a pick would change nothing now.
     */
    private fun playOutVictory(deltaTime: Float) {
        val before = victorySeconds
        victorySeconds += deltaTime
        fun reached(seconds: Float) = before < seconds && victorySeconds >= seconds

        BOSS_AFTERSHOCK_SECONDS.forEachIndexed { index, seconds -> if (reached(seconds)) aftershock(index) }
        if (reached(VICTORY_FANFARE_DELAY_SECONDS)) playFanfare()
        if (bannerTime > 0) bannerTime -= deltaTime

        tickTime += deltaTime
        while (tickTime > tick) {
            tickTime -= tick
            world.update(tick, game.input)
            comboMeter.update(tick, scoring.hitStreak, scoring.multiplier)
            // The blasts drift with the scenery, so the wreck they go off in drifts with them.
            for (i in wreck.indices) wreck[i] = wreck[i].offset(BURST_DRIFT, 0f)
        }

        if (stageCompleteShown) {
            stageCompleteArmingTime = STAGE_COMPLETE_ARMING_SECONDS
            overlayTaps.reset()
            // The overlay says it now. Nothing counts a banner down under the overlay, so one left
            // a hair short of its end would stay there.
            bannerTime = 0f
        }
    }

    /**
     * The wreck going up again, [index] blasts after the first, somewhere on a piece of the boss:
     * one of the blasts in the boss's death sound, seen as well as heard.
     */
    private fun aftershock(index: Int) {
        val piece = wreck.randomOrNull(random) ?: return
        val blast = env.assets.graphics.explosion
        val scale = piece.height / blast.height * BOSS_AFTERSHOCK_SCALE * BOSS_AFTERSHOCK_FALLOFF.pow(index)
        factory.createExplosion(
            centerX = piece.left + piece.width * (0.2f + 0.6f * random.nextFloat()),
            centerY = piece.top + piece.height * (0.2f + 0.6f * random.nextFloat()),
            pixmap = blast,
            scale = scale.coerceAtLeast(1f),
        )
    }

    /**
     * Hands playback to the victory's fanfare, opening it the first time; see [fanfare]. Not if
     * the bat went down in the same tick as the boss: the game over track owns playback then.
     */
    private fun playFanfare() {
        if (!env.audioSettings.musicEnabled) return
        if (world.getComponent(batId, HealthComponent::class)?.alive != true) return
        val audio = game.audio ?: return
        (fanfare ?: audio.newMusic(GameAssets.VICTORY_MUSIC).also { fanfare = it }).play()
    }

    /**
     * The stage is won. A tap or Confirm flies on to the next stage, where there is one; Back
     * leaves for the menu, and so does a tap on the last stage.
     *
     * Armed on a delay for the same reason the pause overlay is: the player can still be steering
     * with a finger down as it comes up, and the lift that follows is not them asking to leave.
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
     * level 1, and no power-ups. The highscore was already banked on the tick the boss went down.
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
            music?.pause()
            // Held only if it is still going: a fanfare that has played out would start again
            // from the top if it were played once more.
            if (fanfare?.isPlaying == true) {
                fanfare?.pause()
                fanfareHeld = true
            }
        } else {
            resumeMusic()
        }
    }

    /**
     * Puts back whatever a pause cut off: the stage's music while the run is being flown, and the
     * fanfare once it has been won. Nothing once the bat is dead: the game over track owns playback
     * then, and resuming would put two tracks on top of each other.
     */
    private fun resumeMusic() {
        when {
            stageComplete -> if (fanfareHeld) {
                fanfareHeld = false
                fanfare?.play()
            }

            world.getComponent(batId, HealthComponent::class)?.alive == true -> startStageMusic()
        }
    }

    private fun drawPauseOverlay() {
        g.drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
        drawCentered("PAUSED", 160, 30, EngineColors.CYAN)
        drawCentered("Tap or click to resume", 195, 15, EngineColors.WHITE)
        drawCentered("Back or Q to quit", 217, 15, EngineColors.WHITE)
    }

    fun saveHighscore() {
        if (scoring.score > highscore) {
            highscore = scoring.score
        }

        // Deliberately not on screenScope: this runs as the bat dies, moments before the screen is
        // swapped out and disposed, and the write has to survive that.
        env.highscores.saveAsync(currentStage.id, highscore)
    }

    /**
     * The banner a new wave or the boss arrives on, held for [WAVE_BANNER_SECONDS].
     *
     * Outlined rather than plain, because it lands over whatever the run happens to be drawing,
     * and centered on its measured width, because what it says runs from "WAVE 2" to "THE MOTH
     * QUEEN".
     */
    private fun drawBanner(text: String) {
        g.drawOutlinedString(
            text,
            centeredX(text, BANNER_FONT_SIZE),
            game.frameBufferHeight / 3,
            BANNER_FONT_SIZE,
            EngineColors.YELLOW,
        )
    }

    /**
     * Draws [text] centered across the framebuffer, which is how every overlay line is placed.
     *
     * Centered on its measured width rather than at an x picked by eye: the faces the platforms
     * draw text in disagree on widths, so a picked x is off center in all but one of them, and
     * has to be picked again whenever the line is reworded.
     */
    private fun drawCentered(text: String, y: Int, fontSize: Int, color: Int) {
        g.drawString(text, centeredX(text, fontSize), y, fontSize, color)
    }

    /** The x that centers [text] at [fontSize] on the framebuffer. */
    private fun centeredX(text: String, fontSize: Int): Int =
        (game.frameBufferWidth - g.measureString(text, fontSize)) / 2

    /**
     * What the player gets for clearing the stage: the run's total against the stage's highscore,
     * and the way out.
     *
     * Drawn over the stage rather than on a screen of its own, so the last thing they see is the
     * cave they beat with the wreckage of the boss still clearing off it.
     */
    private fun drawStageCompleteOverlay() {
        g.drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
        drawCentered("STAGE COMPLETE", 140, 30, EngineColors.YELLOW)
        drawCentered(currentStage.name, 170, 15, EngineColors.CYAN)

        val score = "Score: ${scoring.score}"
        // Already raised by this run if it beat the record, which is how the player can tell that
        // it did: the two numbers match.
        val record = "Highscore: $highscore"
        // The two share a left edge, so they read as one block, and it is the block that is
        // centered, by the wider of the two. Centering each line on its own would stagger them.
        val width = maxOf(g.measureString(score, 20), g.measureString(record, 15))
        val left = (game.frameBufferWidth - width) / 2
        g.drawString(score, left, 195, 20, EngineColors.WHITE)
        g.drawString(record, left, 217, 15, EngineColors.CYAN)

        if (nextStageId != null) {
            drawCentered("Tap or press Enter for stage $nextStageId", 247, 15, EngineColors.WHITE)
            drawCentered("Back or Q for the menu", 269, 15, EngineColors.WHITE)
            drawCentered("Score, level and power-ups start over", 294, 13, EngineColors.CYAN)
        } else {
            drawCentered("Tap or press Enter to continue", 247, 15, EngineColors.WHITE)
        }
    }

    /**
     * The level up dialog: what the bat just reached, and the three things it can become.
     *
     * Cards are drawn rather than composed, because this screen draws a 640x360 frame: the
     * row of cards is centered on that frame, and so are the two lines over it, by their measured
     * widths, as the other overlays' lines are. A card is its own tap target, and carries no
     * number: what it does is the whole of what the player needs to read. The number keys still
     * pick by position for anyone on a keyboard, which is a shortcut rather than the advertised
     * way in.
     */
    private fun drawPowerUpOffer() {
        g.drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
        drawCentered("LEVEL ${progress.level}", 90, 30, EngineColors.YELLOW)
        drawCentered("Choose an upgrade", 120, 15, EngineColors.WHITE)

        offer.forEachIndexed { index, powerUp ->
            drawPowerUpCard(index, powerUp)
        }
    }

    private fun drawPowerUpCard(index: Int, powerUp: PowerUp) {
        val left = cardLeft(index)
        g.apply {
            // A filled panel behind the text, then a cyan lip along the top, so a card reads as a
            // thing to press rather than as words floating over the run.
            drawRect(left, POWER_UP_CARD_TOP, POWER_UP_CARD_WIDTH, POWER_UP_CARD_HEIGHT, CARD_FILL)
            drawRect(left, POWER_UP_CARD_TOP, POWER_UP_CARD_WIDTH, 2, EngineColors.CYAN)

            drawString(
                powerUp.title,
                left + CARD_PADDING,
                POWER_UP_CARD_TOP + 26,
                14,
                EngineColors.CYAN
            )
            // Broken on whole words by measured width rather than by counting characters: a count
            // that fits in Arial runs off the card in the wider DejaVu Sans.
            wrapped(powerUp.description, POWER_UP_CARD_WIDTH - 2 * CARD_PADDING, 11)
                .forEachIndexed { line, text ->
                    drawString(
                        text,
                        left + CARD_PADDING,
                        POWER_UP_CARD_TOP + 48 + line * 14,
                        11,
                        EngineColors.WHITE
                    )
                }
        }
    }

    /**
     * Greedy word wrap into lines no wider than [width] at [fontSize], which is all the card layout
     * needs.
     */
    private fun wrapped(text: String, width: Int, fontSize: Int): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val longer = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && g.measureString(longer, fontSize) > width) {
                lines += line
                line = word
            } else {
                line = longer
            }
        }
        if (line.isNotEmpty()) lines += line
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

    /**
     * The stage timer, top center: how long this stage has been flown, in minutes and seconds.
     *
     * Read off the same clock as the wave readout, so it holds still wherever the stage does - the
     * pause overlay, the level up dialog - and stops where the run ended; see [finalStageSeconds].
     * Level with the score, just under the experience bar, and outlined like the banner because the
     * stalactites hang through this strip and the bat can fly up into it. White rather than the
     * HUD's cyan, so it does not run into the bar filling above it.
     *
     * Centered on its measured width, which holds still as the seconds tick over: the sans-serif
     * faces it is drawn in give every digit the same advance, so every reading is as wide as the
     * last.
     */
    private fun drawStageTimer() {
        val time = formatStageTime(finalStageSeconds ?: enmGen.elapsedSeconds)
        g.drawOutlinedString(
            time,
            centeredX(time, STAGE_TIMER_FONT_SIZE),
            20,
            STAGE_TIMER_FONT_SIZE,
            EngineColors.WHITE,
        )
    }

    override fun present(deltaTime: Float) {
        g.clear(EngineColors.BLACK)
        world.draw(g)
        drawStats()
        drawExperienceBar()
        drawStageTimer()
        if (stageNameDisplayTime > 0) {
            drawStageName()
        }
        bannerText?.takeIf { bannerTime > 0 }?.let { drawBanner(it) }

        if (offer.isNotEmpty()) drawPowerUpOffer()
        if (stageCompleteShown) drawStageCompleteOverlay()
        if (paused) drawPauseOverlay()
    }

    /**
     * The text HUD. Health is deliberately not part of it: it rides under the bat, where the
     * player is already looking. Nor is the highscore, which is a record to read between runs
     * rather than a number to watch during one - the end screens and the stage select show it.
     *
     * Every line of it is outlined. The cave and the forest are dark enough for plain cyan, but
     * the desert flies under a bleached noon sky, where cyan on pale yellow all but disappears.
     */
    private fun drawStats() {
        // Always shown, even at x1: a multiplier the player only sees once they have earned it is
        // a mechanic they never learn exists. Drawn first, because its fire reaches up behind the
        // lines above it, and last in the column, because the hotter it burns the bigger its count
        // grows, and down there it grows into nothing else.
        comboMeter.draw(g, 5, COMBO_BASELINE)
        g.apply {
            drawOutlinedString("Score: ${scoring.score}", 5, 20, 15, EngineColors.CYAN)
            // How far into the stage the player is, which is the only reading they get on how
            // much harder the next minute is about to be - and on how close the boss is.
            drawOutlinedString(waveLabel(), 5, 40, 15, EngineColors.CYAN)
            // The other half of that race: how much stronger the bat has got while the cave was
            // getting harder. The bar across the top edge is the fine detail; this is the count,
            // in the top right corner the bar fills toward, kept the same 5px off the edge as the
            // column on the left. Right-aligned by its measured width, because the face it comes
            // out in, and so its width, varies by platform.
            val level = "Level: ${progress.level}"
            drawOutlinedString(
                level,
                game.frameBufferWidth - 5 - measureString(level, 15),
                20,
                15,
                EngineColors.YELLOW,
            )
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

    /**
     * The stage's name, across the middle of the frame for the opening seconds of the run. Outlined
     * like the banners, because the desert opens at noon, and yellow on its pale sky does not read.
     */
    private fun drawStageName() {
        val name = currentStage.name
        g.drawOutlinedString(
            name,
            centeredX(name, 30),
            game.frameBufferHeight / 2,
            30,
            EngineColors.YELLOW
        )
    }

    /** Starts or resumes the stage's music, if music is enabled, opening it the first time. */
    private fun startStageMusic() {
        if (!env.audioSettings.musicEnabled) return
        (music ?: openStageMusic())?.play()
    }

    private fun openStageMusic(): LayeredMusic? {
        val audio = game.audio ?: return null
        val score = currentStage.music
        return audio.newLayeredMusic(score.stems, score.grid).also {
            music = it
            director = MusicDirector(it, score.grid, progression.bossWave, progression.difficulty)
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
        // not paused by hand.
        if (paused) return
        resumeMusic()
    }

    override fun dispose() {
        screenScope.cancel()
        // Flying on to the next stage builds a fresh screen, which opens that stage's music; this
        // one's has to go, or the two would play over each other - and so does the fanfare, which
        // a player tapping straight through would otherwise hear on into the next stage.
        music?.dispose()
        music = null
        director = null
        fanfare?.dispose()
        fanfare = null
    }

    private companion object {
        /** A power-up card's panel: dark enough to read white text on, over a dimmed run. */
        const val CARD_FILL = 0xE6101820.toInt()

        /** The room between a card's edges and its text, on the left and on the right. */
        const val CARD_PADDING = 8

        /** The unfilled part of the experience bar. */
        const val XP_BAR_EMPTY = 0x80000000.toInt()

        /**
         * The combo readout's line, under the score and the wave. Further below the wave than the
         * wave is below the score, because the count swells upward as the streak climbs and it
         * needs the room to do it without touching the line above.
         */
        const val COMBO_BASELINE = 66

        const val DEGREES_PER_RADIAN = 57.29578f

        /**
         * The lowest z index that is drawn over a halo. The scenery strip sits well below it, and
         * every sprite of the run - the obstacles up - at or above it.
         */
        const val SPRITE_LAYERS_FROM = 0

        /**
         * The lowest z index drawn over the dark, in a stage flown in it: the shots at 15, the bat at
         * 20 and the blasts at 50. The obstacles, the creatures and the bosses, from 5 to 13, sit
         * under it and are lit by whatever reaches them.
         */
        const val LIGHTS_FROM = 15

        /**
         * How big each part of a boss's body goes up, against the part itself. Under one: ten of
         * them go off at once, and at full size they would be a single wall of fire.
         */
        const val BODY_BLAST_SCALE = 0.85f
    }
}
