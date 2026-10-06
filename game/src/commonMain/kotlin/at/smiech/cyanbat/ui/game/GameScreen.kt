package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.ScoreTracker
import at.smiech.cyanbat.ecs.BackgroundScrollingSystem
import at.smiech.cyanbat.ecs.BossPartComponent
import at.smiech.cyanbat.ecs.ColorwayComponent
import at.smiech.cyanbat.ecs.ContactCooldownComponent
import at.smiech.cyanbat.ecs.ContactWeapon
import at.smiech.cyanbat.ecs.ContactWeaponComponent
import at.smiech.cyanbat.ecs.EliteComponent
import at.smiech.cyanbat.ecs.FrostBeamSystem
import at.smiech.cyanbat.ecs.FrostSystem
import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.InvulnerabilitySystem
import at.smiech.cyanbat.ecs.OrbComponent
import at.smiech.cyanbat.ecs.OrbitSystem
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.SkySystem
import at.smiech.cyanbat.ecs.Volley
import at.smiech.cyanbat.music.MusicDirector
import at.smiech.cyanbat.progress.PlayerLoadout
import at.smiech.cyanbat.progress.PlayerProgress
import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.resource.Backdrop
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.GameText
import at.smiech.cyanbat.resource.SoundEffect
import at.smiech.cyanbat.resource.StageMusic
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.banner_second_life
import at.smiech.cyanbat.resources.banner_wave
import at.smiech.cyanbat.resources.boss_caco_imp
import at.smiech.cyanbat.resources.boss_caco_imp_blazes
import at.smiech.cyanbat.resources.boss_caco_imp_falls
import at.smiech.cyanbat.resources.boss_caco_imp_lights_out
import at.smiech.cyanbat.resources.boss_moth_queen
import at.smiech.cyanbat.resources.boss_moth_queen_enraged
import at.smiech.cyanbat.resources.boss_moth_queen_falls
import at.smiech.cyanbat.resources.boss_moth_queen_swarm
import at.smiech.cyanbat.resources.boss_naga
import at.smiech.cyanbat.resources.boss_naga_brood
import at.smiech.cyanbat.resources.boss_naga_enraged
import at.smiech.cyanbat.resources.boss_naga_falls
import at.smiech.cyanbat.resources.boss_naga_strikes
import at.smiech.cyanbat.resources.boss_naga_tide
import at.smiech.cyanbat.resources.boss_sand_wyrm
import at.smiech.cyanbat.resources.boss_sand_wyrm_brood
import at.smiech.cyanbat.resources.boss_sand_wyrm_enraged
import at.smiech.cyanbat.resources.boss_sand_wyrm_falls
import at.smiech.cyanbat.resources.hud_boss
import at.smiech.cyanbat.resources.hud_level
import at.smiech.cyanbat.resources.hud_wave
import at.smiech.cyanbat.resources.level_up_choose
import at.smiech.cyanbat.resources.level_up_title
import at.smiech.cyanbat.resources.pause_quit
import at.smiech.cyanbat.resources.pause_resume
import at.smiech.cyanbat.resources.pause_title
import at.smiech.cyanbat.resources.score
import at.smiech.cyanbat.resources.stage_complete_continue
import at.smiech.cyanbat.resources.stage_complete_fresh_start
import at.smiech.cyanbat.resources.stage_complete_menu
import at.smiech.cyanbat.resources.stage_complete_next
import at.smiech.cyanbat.resources.stage_complete_title
import at.smiech.cyanbat.resources.stage_highscore
import at.smiech.cyanbat.scenery.Day
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
import at.smiech.cyanbat.util.FROST_FLASH_RADIUS
import at.smiech.cyanbat.util.FROST_FLASH_SECONDS
import at.smiech.cyanbat.util.FROST_LIGHT_COLOR
import at.smiech.cyanbat.util.HIT_FLASH_COLOR
import at.smiech.cyanbat.util.HIT_FLASH_SECONDS
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
import at.smiech.cyanbat.util.IMPACT_LEAD
import at.smiech.cyanbat.util.ORB_REHIT_SECONDS
import at.smiech.cyanbat.util.PAUSE_DIM
import at.smiech.cyanbat.util.PLAYER_SHOT_VARIANT
import at.smiech.cyanbat.util.POWER_UP_ARMING_SECONDS
import at.smiech.cyanbat.util.POWER_UP_CARD_GAP
import at.smiech.cyanbat.util.POWER_UP_CARD_HEIGHT
import at.smiech.cyanbat.util.POWER_UP_CARD_PADDING
import at.smiech.cyanbat.util.POWER_UP_CARD_TOP
import at.smiech.cyanbat.util.POWER_UP_CARD_WIDTH
import at.smiech.cyanbat.util.POWER_UP_GRACE_SECONDS
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
import at.smiech.cyanbat.util.WAKE_HARMLESS_FROM
import at.smiech.cyanbat.util.WAKE_REHIT_SECONDS
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
import at.smiech.engine.ecs.TrailComponent
import at.smiech.engine.ecs.TrailSystem
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.VelocityComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.WeaponSystem
import at.smiech.engine.ecs.World
import at.smiech.engine.ecs.WoundComponent
import at.smiech.engine.ecs.WoundSystem
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * One run through one stage.
 *
 * Moving on to the next stage builds a fresh screen, which is what resets the score, the bat's
 * experience and its power-ups, so every stage is beaten from a standing start however it was
 * reached.
 *
 * @param stageId which stage to fly, 1-based. An id with no stage behind it flies the first.
 */
class GameScreen(
    override val game: Game,
    private val env: CyanBatEnvironment,
    stageId: Int = 1,
) : Screen {
    /** The stage being flown. */
    val currentStage = env.assets.stage(stageId)

    /** What the run says, in the player's language. Read off the host every time, which may swap it. */
    private val text: GameText get() = env.text

    /** The run's entities and systems. */
    private val world = World()

    /** Whether the stage is flown in the dark, where whatever gives off light carries one. */
    private val lit = currentStage.lighting != null

    /** Builds the run's entities. */
    private val factory = EntityFactory(world, lit)

    /**
     * Canceled in [dispose], so nothing started here outlives the screen. On the main dispatcher,
     * the game loop's thread, so what a coroutine here writes needs no locking.
     */
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The bat, which lives for the whole run. */
    private val batId: EntityId

    /** The run's score, streak and multiplier; see [ScoreTracker]. */
    private val scoring = ScoreTracker()

    /** The burning combo readout in the HUD; see [ComboMeter]. */
    private val comboMeter = ComboMeter()

    /** The highscore of the stage being flown, raised to this run's score whenever it is banked. */
    var highscore: Int = 0
        private set

    /** The fixed step the world is advanced by. */
    private val tick = TICK_INITIAL

    /** Frame time not yet spent on a whole tick. */
    private var tickTime = 0f

    /** The difficulty curve of the stage being played; see [StageProgression]. */
    private val progression = StageProgression.forStage(currentStage.id)

    /** Spawns the stage's waves and its boss, and keeps the stage clock. */
    val enmGen = EnemyGenerator(
        xSpawnPosition = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        currentStage.enemySheet,
        progression = progression,
        onWaveChanged = { wave -> announce(text.format(Res.string.banner_wave, wave.index + 1)) },
        onBossSpawned = {
            announce(text[bossName()])
            onBossMusic()
        },
        bossPixmap = currentStage.bossSheet,
        onBossPhaseChanged = { phase ->
            announce(text[bossPhaseBanner(phase)])
            director?.onBossPhaseChanged()
        },
    )

    /** Spawns the stage's obstacles. */
    private val obsGen = ObstacleGenerator(
        worldWidth = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        currentStage,
        dayPosition = ::dayPosition,
    )

    /**
     * The stage's music, opened the first time it plays, so a run with music off reads no stems.
     * Owned by the run rather than the assets, and disposed with the screen.
     */
    private var music: LayeredMusic? = null

    /** Turns the run into the music's layers; see [MusicDirector]. Opened with [music]. */
    private var director: MusicDirector? = null

    /**
     * The fanfare of a won stage, opened when it first plays; see [GameAssets.VICTORY_MUSIC]. Owned
     * and disposed like [music].
     */
    private var fanfare: Music? = null

    /** Set when a pause cut the fanfare short, so that resuming carries it on; see [setPaused]. */
    private var fanfareHeld = false

    /** The run's sound effects; see [SoundBoard]. */
    private val sounds = SoundBoard(env.assets.audio.effects) { env.audioSettings.soundsEnabled }

    /** The host's graphics, which every overlay and HUD line is drawn through. */
    private lateinit var g: Graphics

    /** Seconds left of the stage's name across the frame at the start of the run. */
    private var stageNameDisplayTime = 3.0f

    /** The wave or boss announcement currently on screen, and what is left of its time. */
    private var bannerText: String? = null
    private var bannerTime = 0f

    /**
     * Set when the stage's boss goes down. The run is won: it plays on for
     * [STAGE_COMPLETE_DELAY_SECONDS] (see [playOutVictory]) before the overlay shows the score.
     */
    private var stageComplete = false

    /** Time since the boss went down; the run's own time, so a pause holds it. */
    private var victorySeconds = 0f

    /** The stage complete overlay is up: the boss is down, and the run has played out after it. */
    private val stageCompleteShown: Boolean
        get() = stageComplete && victorySeconds >= STAGE_COMPLETE_DELAY_SECONDS

    /**
     * Where the boss was when it went down, for the aftershocks to go off in: one piece for a boss
     * that is one entity, every part in sight for one with a body. Drifted with the blasts every
     * tick, since the wreck is in the world, not on the screen.
     */
    private val wreck = mutableListOf<Rect>()

    /** Time before the victory overlay will accept a tap as "done"; see [handleStageCompleteControls]. */
    private var stageCompleteArmingTime = 0f

    /**
     * The stage clock when the run ended, won or lost, where the timer stops; null while the run is
     * flown. The clock itself, [EnemyGenerator.elapsedSeconds], runs on while the bat falls.
     */
    private var finalStageSeconds: Float? = null

    /** Experience earned this run, and the levels it has bought. */
    private val progress = PlayerProgress()

    /** What the run's power-ups have made of the bat; see [PlayerLoadout]. */
    private val loadout = PlayerLoadout()

    /** Rolls the bat's critical hits. Its own, so nothing else in the run shifts the sequence. */
    private val random = Random.Default

    /**
     * Level ups owed but not yet spent. A count, since one kill can cross two thresholds; they are
     * offered one dialog at a time.
     */
    private var pendingLevelUps = 0

    /** The power-ups currently on offer, or empty when the run is not waiting on a choice. */
    private var offer: List<PowerUp> = emptyList()

    /** Time before the level up dialog will accept a tap; see [handlePowerUpChoice]. */
    private var offerArmingTime = 0f

    /**
     * Taps on whichever overlay is up: level up, pause or stage complete. One detector is enough,
     * since only one overlay reads the events in a frame (pause first, over the level-up dialog),
     * and each [TapDetector.reset]s it as it opens.
     */
    private val overlayTaps = TapDetector()

    /** Fractional health owed by Regeneration, carried between ticks; see [regenerate]. */
    private var regenCarry = 0f

    /**
     * The run's own clock, in ticks flown, on which the contact weapons' rehit times are kept; see
     * [strike]. Separate from the stage clock, which the generator owns.
     */
    private var contactClock = 0f

    /** The orbs' ring, kept to place new orbs at once; see [syncOrbs]. */
    private val orbit = OrbitSystem { batRect() }

    /** The collision pass, kept to ask partway through it what else it meets; see [hasAlreadyStruck]. */
    private val collisions = CollisionSystem { id1, id2 -> handleCollision(id1, id2) }

    /** The frost beam, armed by the loadout; see [applyLoadout]. */
    private val frostBeam = FrostBeamSystem(
        game.frameBufferWidth,
        game.frameBufferHeight,
        random,
        // Not once the stage is won: nothing hostile is left, and the bat flies on unarmed.
        origin = { if (stageComplete) null else batRect() },
        canFreeze = ::canFreeze,
        onFire = { frozen, _ -> frostBeamFired(frozen) },
    )

    /** Set by the player, and by the host backgrounding the app. Cleared only by the player. */
    private var paused = false

    /**
     * Time before a tap counts as "resume", in seconds, so the finger lifting at the end of the
     * gesture that paused the game does not unpause it.
     */
    private var resumeArmingTime = 0f

    init {
        game.graphics?.let { g = it }

        // The systems, in the order they update and draw.
        // A sky that changes with the time of day is the back of the picture, so it goes first.
        // Its update lights the obstacles on the same tick, reading the stage clock as the
        // generator left it after the tick before.
        val backdrop = currentStage.backdrop
        if (backdrop is Backdrop.Sky) {
            world.addSystem(
                SkySystem(
                    backdrop,
                    game.frameBufferWidth,
                    game.frameBufferHeight,
                    dayPosition = ::dayPosition
                )
            )
        }
        world.addSystem(PlayerInputSystem(game.frameBufferWidth, game.frameBufferHeight))
        // Before the movement it feeds, so the gravity it adds moves the bat this
        // tick, not the next.
        world.addSystem(DeathSystem { dyingId -> shedDeathPuff(dyingId) })
        world.addSystem(MovementSystem())
        // Straight after the movement that carries a shot into an edge, and before the culling
        // that would remove it there.
        world.addSystem(BounceSystem(game.frameBufferWidth, game.frameBufferHeight))
        // After the movement, which a frozen enemy's pace holds still, so drifting with the scenery
        // is all it does; and before the weapons, which its pace holds too.
        world.addSystem(FrostSystem { id -> thaw(id) })
        // After the bat has moved, so the ring circles where it is now, and before the collisions,
        // so an orb hits what it is drawn over.
        world.addSystem(orbit)
        world.addSystem(WeaponSystem { shooterId -> fireShot(shooterId) })
        world.addSystem(BackgroundScrollingSystem(game.frameBufferWidth, factory))
        world.addSystem(EnemyBehaviorSystem())
        // After everything that can set a velocity (steering, movement, bounce, enemy patterns), so
        // a shot points the way it travels on this frame, not the last.
        world.addSystem(FacingSystem())
        world.addSystem(AnimationSystem())
        // Before the collisions that arm a flash, so a hit landing this tick is lit for a full
        // frame rather than aged on the tick it happened.
        world.addSystem(HitFlashSystem())
        world.addSystem(collisions)
        // After the collisions, so the blow that takes something past a mark shows on the frame it
        // lands and slows it from the next. The Sand Wyrm's brain reads the head's row later in the
        // tick, to draw the body from.
        world.addSystem(WoundSystem { id, row -> slowWounded(id, row) })
        // After the collisions too, which start the bat's mercy on a hit, so the bat shows it
        // cannot be hurt on the frame the hit lands.
        world.addSystem(InvulnerabilitySystem())
        // With the height too: aimed and fanned enemy fire leaves through the top and bottom as
        // well as the sides.
        world.addSystem(LifetimeSystem(game.frameBufferWidth, game.frameBufferHeight))
        // The jungle's and the cave's scenery strip, in a pass of its own under every other sprite:
        // drawn in one pass with the rest, it covered every halo.
        world.addSystem(RenderSystem(layers = Int.MIN_VALUE until SPRITE_LAYERS_FROM))
        // Over the scenery and under the sprites, so a halo is light coming off its wearer rather
        // than a wash over it. This pass also advances the aura's clock; see [AuraSystem.Layer].
        world.addSystem(AuraSystem(AuraSystem.Layer.HALO))
        // The sprites in two passes, with a dark stage's dark between them: under it the obstacles
        // and everything hostile, lit by whatever reaches them; over it the shots, the bat and the
        // blasts, which are lights and drawn at full brightness. The dark covers the scenery and
        // halos too, and lies under every effect after it. In daylight the two passes draw what
        // one would.
        world.addSystem(RenderSystem(layers = SPRITE_LAYERS_FROM until LIGHTS_FROM))
        currentStage.lighting?.let {
            world.addSystem(
                LightingSystem(
                    game.frameBufferWidth,
                    game.frameBufferHeight,
                    it.ambient,
                    it.glow
                )
            )
        }
        world.addSystem(RenderSystem(layers = LIGHTS_FROM..Int.MAX_VALUE))
        // Straight after the sprites, so a bubble encloses its enemy rather than being painted
        // over, and before the health bars, which must never be hidden behind one.
        world.addSystem(ShieldSystem())
        // After the sprites, these draw over the run. The wake goes first, so the health bars and
        // damage numbers stay legible over it; the arcs go over the wake and under the bar, which
        // no effect may hide.
        world.addSystem(TrailSystem { emitterId -> shedTrail(emitterId) })
        // Over the wake, and in the cave over the dark, since a beam is light. Its update needs
        // only where things are, so it can fire from here.
        world.addSystem(frostBeam)
        world.addSystem(AuraSystem(AuraSystem.Layer.ARCS))
        world.addSystem(HealthBarSystem(game.frameBufferHeight))
        world.addSystem(FloatingTextSystem())

        // A strip is the first tile of the scrolling scenery, which BackgroundScrollingSystem keeps
        // topped up from here. A sky needs nothing laid down: its system draws it whole.
        if (backdrop is Backdrop.Strip) factory.createBackground(0f, backdrop.pixmap)

        batId = factory.createBat(
            x = (game.frameBufferWidth / 3).toFloat(),
            y = (game.frameBufferHeight / 2).toFloat(),
            width = BAT_FRAME_WIDTH.toFloat(),
            pixmap = env.assets.graphics.bat,
            // From the loadout rather than the constant, so the bat starts from the stats the
            // power-ups change.
            shotIntervalSeconds = loadout.shotIntervalSeconds,
        )

        startStageMusic()
        initStats()
    }

    /**
     * How far through its day the stage is, off the stage clock, for a stage whose sky and scenery
     * change on the way to its boss; see [Day.position].
     */
    private fun dayPosition(): Float =
        Day.position(enmGen.elapsedSeconds, progression.bossTimeSeconds)

    /**
     * Fires [shooterId]'s weapon: a shot, or the bat's fan of them, from its leading edge and
     * centered on it vertically, or an enemy gun's next [Volley] (see [fireVolley]).
     *
     * The bat shoots right and enemies shoot left, so each shot leaves from the side it travels
     * toward rather than through the shooter. A shot carries its shooter's damage.
     */
    private fun fireShot(shooterId: EntityId) {
        // Nothing fires once the stage is won: nothing hostile is left, and the bat's gun would
        // only be noise under the fanfare.
        if (stageComplete) return
        val transform = world.getComponent(shooterId, TransformComponent::class) ?: return
        val isPlayer = world.hasComponent(shooterId, PlayerControlComponent::class)
        // An enemy holds its fire until it is on screen, so no shot arrives unwarned from past the
        // right edge, or from under the sand, where an elite wyrmling cruises in armed.
        if (!isPlayer && !isOnScreen(transform.rect)) return
        val gun = if (isPlayer) null else world.getComponent(shooterId, GunComponent::class)
        if (gun != null) {
            fireVolley(shooterId, transform.rect, gun)
            return
        }
        val shot = env.assets.graphics.shot
        // The sheet lays every colorway side by side, so a shot is placed and sized by one frame of
        // it, not by the pixmap.
        val x = if (isPlayer) transform.rect.right else transform.rect.left - SHOT_FRAME_WIDTH
        val y = transform.rect.centerY - shot.height / 2f
        // A bolt takes its shooter's color; the bat has no style and falls back to its own cyan.
        val variant = world.getComponent(shooterId, ProjectileStyleComponent::class)?.variant
            ?: PLAYER_SHOT_VARIANT
        // The bat's damage is a run stat the power-ups raise; everything else deals what it was
        // spawned with.
        val damage = if (isPlayer) loadout.shotDamage else damageOf(shooterId)

        for (angle in spreadAngles(if (isPlayer) 1 + loadout.extraShots else 1)) {
            // Rolled per projectile, so a wider fan is more chances at one. The bat's shots only: a
            // critical is a reward, not a death the player cannot account for.
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
                // Piercing and ricochet are the bat's alone: an enemy shot coming back off a wall
                // would be a hazard the player cannot read.
                pierce = if (isPlayer) loadout.shotPierce else 0,
                bounce = if (isPlayer) loadout.shotBounce else 0,
            )
        }

        // Once per volley, not per shot: a fan is one pull of the trigger. Enemy guns have a
        // darker, quieter voice, so the player's own gun is still the one they hear.
        sounds.play(if (isPlayer) SoundEffect.SHOT else SoundEffect.ENEMY_SHOT)
    }

    /**
     * Whether [rect] is wholly inside the frame across, with its middle inside it top to bottom.
     * Looser vertically, because swarms and divers routinely dip past the top or bottom while
     * plainly in view.
     */
    private fun isOnScreen(rect: Rect): Boolean =
        rect.left >= 0f && rect.right <= game.frameBufferWidth &&
                rect.centerY >= 0f && rect.centerY <= game.frameBufferHeight

    /**
     * One pull of an enemy's trigger: [gun]'s next [Volley], in the shooter's color and at its
     * damage. A straight bolt leaves from the edge it travels toward; a fan or a ring spreads from
     * the shooter's center.
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
        // Once per volley, however many shots it holds.
        sounds.play(SoundEffect.ENEMY_SHOT)
    }

    /**
     * The headings of an enemy volley, as [EntityFactory.createShot] takes them: degrees off
     * straight ahead (to the left, for an enemy), positive downward.
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
                // Turned half a step each ring, so the next ring closes the lanes this one leaves
                // open.
                gun.spin = (gun.spin + step / 2f) % 360f
                List(volley.count) { start + it * step }
            }
        }

    /**
     * The heading from [rect]'s center to the bat's, or straight ahead with no bat to aim at.
     * Aimed where the bat is, not where it is going, so moving always dodges.
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
     * Sheds one segment of the bat's wake, just off its back: centered on the sprite's lower half,
     * where the tail is, since a wake off its belly would seem to come from the health bar.
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
            // Charged Trail lengthens the wake and arms it. A segment keeps what it was shed as, so
            // a new pick spreads down the wake within its length.
            seconds = loadout.wakeSeconds,
            charged = loadout.wakeLevel > 0,
        )
    }

    /** Resolves one overlapping pair from the collision pass, which reports it in either order. */
    private fun handleCollision(id1: EntityId, id2: EntityId) {
        val group1 = collisionGroupOf(id1)
        val group2 = collisionGroupOf(id2)
        // Contact weapons only ever meet enemies (see CollisionGroup.PLAYER_CONTACT), and land by
        // rules of their own.
        if (group1 == CollisionGroup.PLAYER_CONTACT) return strike(id1, id2)
        if (group2 == CollisionGroup.PLAYER_CONTACT) return strike(id2, id1)
        // A frozen enemy is harmless: the bat passes through it, and does not wear
        // it down either, or a frozen swarm would be a row of free kills. The bat's
        // shots, orbs and wake still hurt it.
        if (isBatMeetingFrost(group1, id2) || isBatMeetingFrost(group2, id1)) return
        // Read first: killing the boss clears it from the generator, and the kill below still has
        // to know it was the boss.
        val bossId = enmGen.bossId

        // Before anything is hurt: a shot that has already struck this enemy, or this boss through
        // any of its parts, is done with it however long they overlap. Otherwise the pair would
        // re-hit every frame, spending pierce and killing the enemy over and over.
        if (hasAlreadyStruck(id1, id2) || hasAlreadyStruck(id2, id1)) return

        // Both read before either takes its hit, so an entity that dies here still lands its blow.
        val damage1 = damageOf(id1)
        val damage2 = damageOf(id2)
        val died1 = damage(id1, damage2, dealtBy = id2)
        val died2 = damage(id2, damage1, dealtBy = id1)

        // A kill scores when the enemy dies, not on every shot that lands.
        if (isEnemyShotDown(group1, group2)) {
            val enemyDied = if (group1 == CollisionGroup.ENEMY) died1 else died2
            if (enemyDied) registerKill(if (group1 == CollisionGroup.ENEMY) id1 else id2, bossId)
        }
    }

    /**
     * Scores [enemyId] going down to the bat's weapons: a kill to the streak, and its wave's worth
     * of experience, an elite several times both.
     *
     * @param bossId the boss as it was before the blow landed: killing it clears it from the
     *   generator, and this still has to know it was the boss.
     */
    private fun registerKill(enemyId: EntityId, bossId: EntityId?) {
        val elite = world.hasComponent(enemyId, EliteComponent::class)
        scoring.registerEnemyDestroyed(elite)
        // The boss, or a part of its body, is banked by completeStage. Everything else is worth its
        // wave's experience, an elite several times that.
        if (enemyId != bossId && !isBossPart(enemyId)) {
            awardExperience(PlayerProgress.experienceForKill(enmGen.currentWave.index, elite))
        }
    }

    /**
     * One of the bat's contact weapons (an orb, or a segment of the charged wake) touching
     * [enemyId]. Never spent, and nothing hurts it back.
     *
     * It lands at most once per rehit time on a target, however long they overlap and however
     * many parts it touches; see [ContactCooldownComponent]. A boss part counts as its boss, so a
     * wake the Sand Wyrm pours through lands once, not once a plate.
     */
    private fun strike(weaponId: EntityId, enemyId: EntityId) {
        val weapon = world.getComponent(weaponId, ContactWeaponComponent::class)?.weapon ?: return
        if (weapon == ContactWeapon.WAKE && !stillCharged(weaponId)) return
        val bossId = enmGen.bossId
        val target = if (isBossPart(enemyId)) bossId ?: return else enemyId
        if (world.getComponent(target, HealthComponent::class)?.alive != true) return

        val (amount, rehitSeconds) = when (weapon) {
            ContactWeapon.ORB -> loadout.orbDamage to ORB_REHIT_SECONDS
            ContactWeapon.WAKE -> loadout.wakeDamage to WAKE_REHIT_SECONDS
        }
        val cooldown = world.getComponent(target, ContactCooldownComponent::class)
            ?: ContactCooldownComponent().also { world.addComponent(target, it) }
        if (!cooldown.take(weapon, contactClock, rehitSeconds)) return

        if (damage(enemyId, amount, dealtBy = weaponId)) registerKill(enemyId, bossId)
    }

    /**
     * Whether a segment of the charged wake still shocks: not once it has faded past
     * [WAKE_HARMLESS_FROM] of its life, too faint to be seen doing anything.
     */
    private fun stillCharged(segmentId: EntityId): Boolean {
        val trail = world.getComponent(segmentId, TrailComponent::class) ?: return false
        return trail.elapsed < trail.duration * WAKE_HARMLESS_FROM
    }

    /** Whether the bat, in [group], is meeting [other] while [other] is frozen. */
    private fun isBatMeetingFrost(group: CollisionGroup?, other: EntityId): Boolean =
        group == CollisionGroup.PLAYER && FrostSystem.isFrozen(world, other)

    /** Whether [id] is one of the bat's weapons: a shot, or something that hurts by touch. */
    private fun isBatsWeapon(id: EntityId): Boolean = when (collisionGroupOf(id)) {
        CollisionGroup.PLAYER_PROJECTILE, CollisionGroup.PLAYER_CONTACT -> true
        else -> false
    }

    /**
     * Banks [amount] of experience and queues a power-up pick for every level it buys. Queued,
     * because this runs inside a world update; the dialog opens next frame, once the tick resolves.
     */
    private fun awardExperience(amount: Int) {
        pendingLevelUps += progress.award((amount * loadout.experienceMultiplier).roundToInt())
        syncAura()
    }

    /**
     * Sets the bat's aura from its level: the glow from [PlayerProgress.auraIntensity] and the tier
     * from [PlayerProgress.auraTier]. Called on every award, not only on a level up, so the two
     * never disagree.
     *
     * Crossing a tier (more sparks, another arc) gets a flare and a sound. Compared rather than
     * counted, so two tiers crossed by one kill flare once.
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
     * True when this pair is one of the bat's shots meeting an enemy, in either order. Obstacles do
     * not count: a shot clearing scenery is not a kill.
     */
    private fun isEnemyShotDown(group1: CollisionGroup?, group2: CollisionGroup?): Boolean =
        (group1 == CollisionGroup.PLAYER_PROJECTILE && group2 == CollisionGroup.ENEMY) ||
                (group1 == CollisionGroup.ENEMY && group2 == CollisionGroup.PLAYER_PROJECTILE)

    /** [id]'s collision group, or null for something that collides with nothing. */
    private fun collisionGroupOf(id: EntityId): CollisionGroup? =
        world.getComponent(id, CollisionComponent::class)?.group

    /**
     * What [id] deals to whatever it runs into. Everything a wave spawns carries a
     * [DamageComponent]; the fallback covers what never varies: the bat itself and the obstacles.
     */
    private fun damageOf(id: EntityId): Int =
        world.getComponent(id, DamageComponent::class)?.amount ?: DAMAGE_PER_HIT

    /**
     * Whether [id] carries a critical blow. Anything without a [DamageComponent] (the bat, an
     * obstacle) never does, so ramming an enemy stays an ordinary hit.
     */
    private fun isCritical(id: EntityId): Boolean =
        world.getComponent(id, DamageComponent::class)?.isCritical == true

    /**
     * True when [shotId] has already struck [targetId] and has nothing more to land on it; false
     * otherwise, including on the first meeting, which it records.
     *
     * A piercing shot strikes an enemy once and passes through, however many frames they overlap.
     * A boss's body is one enemy in many parts, and a shot strikes it once, for one pierce, however
     * many parts it touches. A body bunched up as it breaches or rears puts several parts under one
     * shot, and a shot spent on the first is still in the pass until the tick's removals are
     * finalized, so without this a single fan would land once per part.
     */
    private fun hasAlreadyStruck(shotId: EntityId, targetId: EntityId): Boolean {
        if (collisionGroupOf(targetId) != CollisionGroup.ENEMY) return false
        val pierce = world.getComponent(shotId, PierceComponent::class)
        if (!isBossPart(targetId)) return pierce?.meet(targetId) == true

        if (collisionGroupOf(shotId) != CollisionGroup.PLAYER_PROJECTILE) return false
        val bossId = enmGen.bossId ?: return false
        // Touching the head as well, it is left for the head to take whole, whether or not the pass
        // has reached the head yet: the head is drawn over the body and is the place to aim, and
        // the pass reports the body first, since it was made first.
        if (targetId != bossId && collisions.overlaps(shotId, bossId)) return true
        // Spent on another part earlier in this pass, and still in it.
        if (world.getComponent(shotId, HealthComponent::class)?.alive == false) return true
        return pierce?.meet(bossId) == true
    }

    /**
     * Hurts [id] for [amount], shows the cost over an enemy that took the hit, and blows up
     * whatever the hit destroyed. Only enemies get a number: an obstacle is scenery, and the bat's
     * loss shows on its health bar.
     *
     * @param dealtBy the other side of the collision, which decides whether a piercing shot spends
     *   a pierce here or is spent itself.
     * @return true if this hit killed it.
     */
    private fun damage(id: EntityId, amount: Int, dealtBy: EntityId): Boolean {
        // A shot with pierce left passes through an enemy, leaving its hit where it went in.
        // Scenery stops any shot.
        val pierce = world.getComponent(id, PierceComponent::class)
        if (pierce != null && collisionGroupOf(dealtBy) == CollisionGroup.ENEMY && pierce.spend()) {
            leaveHit(id)
            return false
        }

        // Only the bat's weapons wear a boss down. The bat touching one is the boss's attack; the
        // boss has no mercy window, so ramming would hurt it every tick of the overlap, and once a
        // part for a boss with a body.
        val part = world.getComponent(id, BossPartComponent::class)
        if ((part != null || id == enmGen.bossId) && !isBatsWeapon(dealtBy)) return false
        // A hit on a boss part lands on the boss but shows (number and flash) on the part, where
        // the player aimed; see [BossPartComponent].
        val target = if (part != null) enmGen.bossId ?: return false else id
        // A plate is armor and passes on only its share; see [BossPartComponent.share].
        val landing =
            if (part != null) (amount * part.share).roundToInt().coerceAtLeast(1) else amount

        if (absorbedByShield(target, landing)) return false

        val dealt = applyDamage(target, landing)
        if (dealt > 0 && collisionGroupOf(id) == CollisionGroup.ENEMY) {
            // Read off the blow, not the amount, which a heavily upgraded ordinary shot can match.
            showDamageText(id, dealt, critical = isCritical(dealtBy))
            lightUp(id)
        }

        val died = dealt > 0 && world.getComponent(target, HealthComponent::class)?.alive == false
        if (died) {
            // What is left behind depends on what died; see [burst].
            burst(target)
        } else if (dealt > 0 && isBatsWeapon(dealtBy)) {
            // A hit from the bat's weapons is heard landing, on an enemy or a rock; a killing one
            // is heard in what it killed instead.
            sounds.play(SoundEffect.HIT)
        }
        return died
    }

    /** Whether [id] belongs to a boss with a body, its head included; see [BossPartComponent]. */
    private fun isBossPart(id: EntityId): Boolean = world.hasComponent(id, BossPartComponent::class)

    /**
     * Lets [id]'s shield take a hit of [amount], if it is up, and shows the amount in the shield's
     * color, so the player sees the shot was spent on the bubble.
     *
     * @return true when the bubble took the hit and nothing reaches the enemy inside.
     */
    private fun absorbedByShield(id: EntityId, amount: Int): Boolean {
        if (amount <= 0 || collisionGroupOf(id) != CollisionGroup.ENEMY) return false
        if (world.getComponent(id, HealthComponent::class)?.alive != true) return false
        val shield = world.getComponent(id, ShieldComponent::class) ?: return false
        if (!shield.absorb(amount)) return false

        // A ping rather than a thump, so the player hears that nothing got through.
        sounds.play(SoundEffect.SHIELD_HIT)
        val rect = world.getComponent(id, TransformComponent::class)?.rect ?: return true
        factory.createDamageText(rect.left, rect.centerY, amount, color = shield.color)
        return true
    }

    /** A blast the size of whatever just died, so the boss goes out bigger than its escort. */
    private fun burst(id: EntityId) {
        val rect = world.getComponent(id, TransformComponent::class)?.rect ?: return
        val graphics = env.assets.graphics

        // What died decides what is left behind: an enemy burns, a limestone spire breaks. One
        // shared effect made obstacles look detonated in a cave where nothing burns.
        //
        // Nothing else leaves a blast: one per landed shot would bury a tough enemy in hit effects,
        // and the bat's death has its own animation. A spent shot leaves its hit, a small spark,
        // and in the dark a flare of light that shows what it struck.
        val (pixmap, spawn, sound) = when (collisionGroupOf(id)) {
            CollisionGroup.ENEMY ->
                Triple(graphics.explosion, factory::createExplosion, SoundEffect.ENEMY_DEATH)

            CollisionGroup.OBSTACLE ->
                Triple(graphics.shatter, factory::createShatter, SoundEffect.OBSTACLE_SHATTER)

            CollisionGroup.PLAYER_PROJECTILE, CollisionGroup.ENEMY_PROJECTILE -> {
                leaveHit(id)
                return
            }

            else -> return
        }

        // Never smaller than the art: only something bigger than an ordinary enemy scales it up.
        spawn(rect.centerX, rect.centerY, pixmap, (rect.height / pixmap.height).coerceAtLeast(1f))

        // Silent once the stage is won: the boss's blast, played as it fell, covers everything
        // going up with it; see [completeStage].
        if (!stageComplete) sounds.play(sound)
    }

    /**
     * The hit [shotId] leaves where it struck, in its colorway; see [EntityFactory.createImpact].
     *
     * At its nose rather than its middle, where the spark went off inside the shot, short of what
     * it hit. The nose is found along the shot's heading, since shots fly at every angle and
     * bounced ones come back.
     */
    private fun leaveHit(shotId: EntityId) {
        val rect = world.getComponent(shotId, TransformComponent::class)?.rect ?: return
        val variant = world.getComponent(shotId, ColorwayComponent::class)?.variant ?: return
        val velocity =
            world.getComponent(shotId, VelocityComponent::class)?.velocity ?: Vector2.Zero
        val speed = hypot(velocity.x, velocity.y)
        val lead = if (speed > 0f) IMPACT_LEAD / speed else 0f
        factory.createImpact(
            centerX = rect.centerX + velocity.x * lead,
            centerY = rect.centerY + velocity.y * lead,
            pixmap = env.assets.graphics.impact,
            variant = variant,
            isPlayer = collisionGroupOf(shotId) == CollisionGroup.PLAYER_PROJECTILE,
        )
    }

    /**
     * Takes [amount] off [targetId]'s health, killing it at zero, and returns what landed. Nothing
     * lands on something without health or already dead, nor on a bat inside its mercy window or
     * one that has won the stage.
     */
    private fun applyDamage(targetId: EntityId, amount: Int): Int {
        val health = world.getComponent(targetId, HealthComponent::class) ?: return 0
        if (!health.alive) return 0

        var incoming = amount

        // Only the bat has a cooldown; without it one obstacle would strip the
        // whole bar while the two overlap. Its length, and how much of a hit gets
        // through, are stats the power-ups raise.
        val control = world.getComponent(targetId, PlayerControlComponent::class)
        if (control != null) {
            // The hostiles cleared with the boss are still in the rest of that tick's collision
            // pass; a won stage leaves the bat untouchable.
            if (stageComplete || control.hitCooldown > 0f) return 0
            control.hitCooldown = loadout.hitCooldownSeconds
            scoring.registerPlayerHit()
            director?.onPlayerHit()

            // The flat cut comes off first and armor scales the rest, so the two stack as expected.
            // Floored at one, so no hit is ever free and a run can always be lost.
            incoming = ((incoming - loadout.flatDamageReduction) * loadout.damageTaken)
                .roundToInt()
                .coerceAtLeast(1)

            // Felt as well as seen.
            env.haptics.vibrate(HIT_VIBRATION_MILLIS)
        }

        // Capped at what is left, so an overkill reports the damage the target could actually take.
        val dealt = minOf(incoming, health.hitPoints)
        health.hitPoints -= dealt
        if (health.hitPoints <= 0) {
            // Still reported: a revive survives the blow rather than undoing it.
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
     * Spends a Second Life, if the run has one, putting the bat back at [REVIVE_HEALTH_FRACTION] of
     * its bar: enough to keep the run alive without undoing what ended it. The mercy window
     * restarts too, or the same enemy would take the new health at once.
     */
    private fun revive(health: HealthComponent): Boolean {
        if (!loadout.useRevive()) return false

        health.hitPoints =
            (health.maxHitPoints * REVIVE_HEALTH_FRACTION).roundToInt().coerceAtLeast(1)
        world.getComponent(batId, PlayerControlComponent::class)?.hitCooldown =
            loadout.hitCooldownSeconds
        announce(text[Res.string.banner_second_life])
        return true
    }

    /**
     * The stage's boss is down, so the stage is won. Everything hostile goes down with it, the run
     * plays on a few seconds (the wreck going up, the fanfare coming in; see [playOutVictory]), and
     * the overlay then shows the total.
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
        // Banked though the run ends here: a boss worth nothing would read as one
        // that did not count.
        awardExperience(XP_PER_BOSS)
        // The highscore is banked by update once the tick is over, since the score can still move
        // within it. The next stage unlocks now, not on tapping through: a player who quits from
        // the overlay has still won.
        nextStageId?.let { env.stageUnlocks.unlockAsync(it) }

        // The music stops dead on the kill and the blast fills the silence; the fanfare follows
        // (see [playOutVictory]).
        music?.pause()
        sounds.play(SoundEffect.BOSS_DEATH)
        // Its fall is announced and held until the overlay, so the seconds between read as the
        // victory rather than a hang.
        announce(text[bossFalls()], seconds = STAGE_COMPLETE_DELAY_SECONDS)
    }

    /**
     * A boss with a body goes up all along it: every part in sight bursts, and
     * every part is removed. This is the one place besides the boss's brain, which
     * goes with the boss, that removes them.
     */
    private fun explodeBossBody() {
        for (part in world.query(BossPartComponent::class, TransformComponent::class)) {
            // The head burst where the killing blow landed, and goes with the dead.
            if (part == enmGen.bossId) continue
            val rect = world.getComponent(part, TransformComponent::class)?.rect ?: continue
            // Only parts in sight burst; one under the sand would never be seen.
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
     * Everything hostile goes down with its boss: whatever it called in bursts
     * where it is, and every enemy shot is removed, so nothing in the seconds after
     * can hurt the bat or need shooting.
     */
    private fun clearHostiles() {
        for (id in world.query(CollisionComponent::class, TransformComponent::class)) {
            val group = collisionGroupOf(id)
            if (group == CollisionGroup.ENEMY_PROJECTILE) world.removeEntity(id)
            // The boss and its body have already gone up.
            if (group != CollisionGroup.ENEMY || id == enmGen.bossId || isBossPart(id)) continue
            // Only what is in sight bursts: a blast off the edge would drift in with nothing behind
            // it, and one under the sand would never be seen.
            val rect = world.getComponent(id, TransformComponent::class)?.rect
            if (rect != null && inSight(rect)) burst(id)
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

    /** What the stage's boss is called, which is the banner it arrives on. */
    private fun bossName(): StringResource = when (progression.design.boss) {
        BossKind.MOTH_QUEEN -> Res.string.boss_moth_queen
        BossKind.CACO_IMP -> Res.string.boss_caco_imp
        BossKind.SAND_WYRM -> Res.string.boss_sand_wyrm
        BossKind.NAGA -> Res.string.boss_naga
    }

    /**
     * The banner the stage's boss falls under: a whole line rather than its name plus a word, since
     * in some languages the word has to agree with the name.
     */
    private fun bossFalls(): StringResource = when (progression.design.boss) {
        BossKind.MOTH_QUEEN -> Res.string.boss_moth_queen_falls
        BossKind.CACO_IMP -> Res.string.boss_caco_imp_falls
        BossKind.SAND_WYRM -> Res.string.boss_sand_wyrm_falls
        BossKind.NAGA -> Res.string.boss_naga_falls
    }

    /** What a boss announces on entering phase [phase]. */
    private fun bossPhaseBanner(phase: Int): StringResource = when (progression.design.boss) {
        BossKind.CACO_IMP ->
            if (phase >= 3) Res.string.boss_caco_imp_blazes else Res.string.boss_caco_imp_lights_out

        BossKind.MOTH_QUEEN ->
            if (phase >= 3) Res.string.boss_moth_queen_enraged else Res.string.boss_moth_queen_swarm

        BossKind.SAND_WYRM ->
            if (phase >= 3) Res.string.boss_sand_wyrm_enraged else Res.string.boss_sand_wyrm_brood

        BossKind.NAGA -> when (phase) {
            2 -> Res.string.boss_naga_strikes
            3 -> Res.string.boss_naga_brood
            4 -> Res.string.boss_naga_tide
            else -> Res.string.boss_naga_enraged
        }
    }

    /** Puts [text] up over the run for [seconds], replacing whatever was there. */
    private fun announce(text: String, seconds: Float = WAVE_BANNER_SECONDS) {
        bannerText = text
        bannerTime = seconds
    }

    /**
     * Puts the bat into its death throes: the limp sheet, played once, and a tumbling fall.
     *
     * The sprite and animation are replaced rather than a stand-in spawned, so everything watching
     * this entity (the screen's health and position checks, the aura, the wake) keeps watching it,
     * and already stops when its owner is dead.
     *
     * [LifetimeSystem] leaves the player alone when this one-shot animation finishes; otherwise the
     * bat would be removed halfway through its fall.
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
        // Carried over from the previous sprite: zero for the bat, but not for anything turned
        // before it died.
        world.getComponent(batId, SpriteComponent::class)?.rotationDegrees = sprite.rotationDegrees
        // In the dark, its light fades as it falls rather than dropping off the
        // bottom edge with it.
        world.getComponent(batId, LightComponent::class)?.let { light ->
            world.addComponent(
                batId,
                LightComponent(
                    light.color,
                    light.radius,
                    light.strength,
                    fadeSeconds = BAT_LIGHT_FADE_SECONDS
                ),
            )
        }
        // Its orbs go out with it, each in a puff: one left hanging would go on hurting whatever
        // flew into it.
        for (orb in world.query(OrbComponent::class)) {
            world.getComponent(orb, TransformComponent::class)?.rect?.let { rect ->
                factory.createExplosion(
                    rect.centerX,
                    rect.centerY,
                    env.assets.graphics.explosion,
                    DEATH_PUFF_SCALE
                )
            }
            world.removeEntity(orb)
        }
    }

    /** One of the pieces coming off the bat on its way down; see [DeathThroesComponent]. */
    private fun shedDeathPuff(dyingId: EntityId) {
        val rect = world.getComponent(dyingId, TransformComponent::class)?.rect ?: return
        factory.createExplosion(
            // Scattered over the sprite, so the pieces come off the whole animal, not one point.
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
     * Flashes [enemyId] for [HIT_FLASH_SECONDS], so a hit shows on what it landed on, not only in
     * its number.
     *
     * An enemy already lit is re-armed, so sustained fire reads as separate impacts rather than one
     * glow. Fires on any damage, not only shots, so a number never appears without a flash.
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
     * Sets a wounded enemy's pace for its [row] of wounds; see [WOUNDED_PACE] and
     * [WOUNDED_FIRE_RATE]. Only ordinary enemies carry a pace, so the bat and the bosses never
     * slow. Also called going back up a row, so a heal would restore the pace with the picture.
     *
     * A frozen enemy's pace is the frost's until it thaws; see [thaw].
     */
    private fun slowWounded(id: EntityId, row: Int) {
        if (FrostSystem.isFrozen(world, id)) return
        val pace = world.getComponent(id, PaceComponent::class) ?: return
        pace.motion = WOUNDED_PACE[row]
        pace.fire = WOUNDED_FIRE_RATE[row]
    }

    /**
     * A frozen enemy coming free: back to the pace of the row of wounds it has reached, which hits
     * while it was frozen may have moved.
     */
    private fun thaw(id: EntityId) {
        slowWounded(id, world.getComponent(id, WoundComponent::class)?.row ?: 0)
    }

    /** Puts the number at the enemy's leading edge, which is the side the bat's shots arrive from. */
    private fun showDamageText(enemyId: EntityId, damage: Int, critical: Boolean) {
        val rect = world.getComponent(enemyId, TransformComponent::class)?.rect ?: return
        factory.createDamageText(rect.left, rect.centerY, damage, critical)
    }

    /** Starts the run's score from zero and reads the stage's highscore. */
    private fun initStats() {
        scoring.reset()
        readHighscore()
    }

    /**
     * Reads the stage's highscore once, since nothing else writes it during a run. On the main
     * thread, the only one that touches [highscore].
     */
    private fun readHighscore() = screenScope.launch {
        // Merged rather than assigned, in case this run has already beaten the stored value.
        highscore = maxOf(highscore, env.highscores.read(currentStage.id))
    }

    override fun update(deltaTime: Float) {
        // Ahead of every early return: the music plays on, muffled, under the level-up dialog.
        steerMusic(deltaTime)
        // The sound effects' clock; it may run through a pause, since nothing plays then.
        sounds.advance(deltaTime)

        // Ahead of the pause controls, which would read the way out of a won stage as a pause. A
        // pause the host made over it is still theirs to answer, so a tap returns to the overlay.
        if (stageCompleteShown && !paused) {
            handleStageCompleteControls(deltaTime)
            return
        }
        if (handlePauseControls(deltaTime)) return
        if (stageComplete) {
            playOutVictory(deltaTime)
            return
        }

        // An owed level up is offered before anything moves, so the pick changes the fight the
        // player is in.
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
            contactClock += tick
            world.update(tick, game.input)
            regenerate(tick)
            // After the world, so a kill or a hit in this tick's collisions shows on this tick.
            comboMeter.update(tick, scoring.hitStreak, scoring.multiplier)

            // The stage clock runs on the fixed tick, not the wall clock: a paused game is a paused
            // stage.
            enmGen.update(tick)
            // No obstacles during the boss: one pinning the player against scenery they cannot
            // outrun is an unfair death.
            if (!enmGen.bossSpawned) obsGen.update(tick)

            // A won stage's score is banked once the winning tick has resolved, not as the boss
            // goes down partway through the collision pass, after which the kill and any later
            // ones in the pass still score; banked early, the overlay could show a record below
            // the score. The frame's remaining ticks are dropped: from the next frame the run
            // plays out its victory; see [playOutVictory].
            if (stageComplete) {
                saveHighscore()
                break
            }
        }

        val health = world.getComponent(batId, HealthComponent::class)!!
        if (!health.alive) {
            val transform = world.getComponent(batId, TransformComponent::class)!!
            if (transform.rect.top > game.frameBufferHeight) {
                // Banked again on the way out: a shot in flight can still score while the bat
                // falls, and the game over screen shows the record beside the score.
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
        // The music plays on, muffled while the offer is up; [steerMusic] reads that off the offer.
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
     * Reads a pick off the level-up dialog: a tap on a card, or its number key.
     *
     * Armed on a delay, like the pause overlay, so the finger that was steering when it opened does
     * not pick. It cannot be dismissed without picking: the pick is the reward.
     */
    private fun handlePowerUpChoice(deltaTime: Float) {
        offerArmingTime -= deltaTime

        val input = game.input
        val controls = input?.controls
        // Read whether or not they can act yet, so no backlog reaches the run when the dialog
        // closes. Only taps begun on the dialog count; see [TapDetector].
        val touches = overlayTaps.taps(input?.touchEvents.orEmpty())
        // CONFIRM picks the leftmost card: a pad has no number keys, and A is what a player reaches
        // for first.
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

    /**
     * Takes the pick, makes the bat match, and hands the run back, with a moment's grace before
     * anything can hurt the bat: the run picks up where the dialog froze it, and the finger that
     * tapped a card has let go of the bat.
     */
    private fun choosePowerUp(powerUp: PowerUp) {
        powerUp.applyTo(loadout)
        applyLoadout()
        offer = emptyList()
        announce(text[powerUp.title])
        InvulnerabilitySystem.grant(world, batId, POWER_UP_GRACE_SECONDS)
    }

    /**
     * Pushes the run's earned stats onto the bat's components, in one place: a power-up says what
     * the bat is now, and this makes it so.
     */
    private fun applyLoadout() {
        world.getComponent(batId, WeaponComponent::class)?.interval = loadout.shotIntervalSeconds
        scoring.bonusMultiplier = loadout.scoreMultiplier
        syncOrbs()
        frostBeam.intervalSeconds = if (loadout.frostLevel > 0) loadout.frostIntervalSeconds else 0f
        frostBeam.freezeSeconds = loadout.frostSeconds

        val health = world.getComponent(batId, HealthComponent::class) ?: return
        health.maxHitPoints = loadout.maxHitPoints
        // Capped at the bar, so healing a nearly full bat fills only the room left.
        val heal = loadout.takePendingHeal()
        if (heal > 0) health.hitPoints = (health.hitPoints + heal).coerceAtMost(health.maxHitPoints)
    }

    /**
     * Adds orbs until the bat has as many as the loadout says, each made at its share of the full
     * ring; the orbs already circling ease over to theirs.
     */
    private fun syncOrbs() {
        val circling = world.query(OrbComponent::class).size
        if (circling >= loadout.orbs) return
        val bat = batRect() ?: return
        for (index in circling until loadout.orbs) {
            factory.createOrb(
                bat.centerX,
                bat.centerY,
                env.assets.graphics.orb,
                OrbitSystem.shareOf(index, loadout.orbs)
            )
        }
        // Placed on the ring now rather than next tick, since the run is drawn under the dialog
        // that bought them.
        orbit.arrange(world)
    }

    /** The living bat's box, or null once it has died. */
    private fun batRect(): Rect? {
        if (world.getComponent(batId, HealthComponent::class)?.alive != true) return null
        return world.getComponent(batId, TransformComponent::class)?.rect
    }

    /**
     * What the frost beam may freeze: an ordinary enemy, whose pace the frost can hold. Never a
     * boss or its parts, which carry none, and never an elite, which is a prize to chase rather
     * than a hazard to switch off.
     */
    private fun canFreeze(id: EntityId): Boolean =
        id != enmGen.bossId && !isBossPart(id) &&
                world.hasComponent(id, PaceComponent::class) &&
                !world.hasComponent(id, EliteComponent::class)

    /**
     * A frost beam going off is heard, and in the dark it flashes its cold light on everything it
     * froze, as a shot does where it lands, so the player sees what it caught.
     */
    private fun frostBeamFired(frozen: List<EntityId>) {
        sounds.play(SoundEffect.FROST_BEAM)
        if (!lit) return
        for (id in frozen) {
            val rect = world.getComponent(id, TransformComponent::class)?.rect ?: continue
            factory.createFlash(
                rect.centerX,
                rect.centerY,
                FROST_LIGHT_COLOR,
                FROST_FLASH_RADIUS,
                FROST_FLASH_SECONDS
            )
        }
    }

    /**
     * Restores whatever Regeneration owes this tick. Fractional health carries over, as the score
     * bonus does: two health a second is well under a point a tick, and rounding each tick would
     * heal nothing. Only a living bat regenerates, and only up to its bar.
     */
    private fun regenerate(deltaTime: Float) {
        if (loadout.healthRegenPerSecond <= 0f) return
        val health = world.getComponent(batId, HealthComponent::class) ?: return
        if (!health.alive || health.hitPoints >= health.maxHitPoints) {
            // Dropped, not banked, or health owed while full would pour out in one lump at the next
            // hit.
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
     * Plays the run on between the boss going down and the overlay coming up, so the boss is seen
     * exploding rather than frozen in its first frame of fire.
     *
     * The bat still flies, with nothing left to shoot or be hurt by ([clearHostiles]). The wreck
     * bursts again at each of [BOSS_AFTERSHOCK_SECONDS], the fanfare comes in at
     * [VICTORY_FANFARE_DELAY_SECONDS], and the overlay lands on its drop. The stage clock holds,
     * nothing new arrives, and no level up is offered.
     */
    private fun playOutVictory(deltaTime: Float) {
        val before = victorySeconds
        victorySeconds += deltaTime
        fun reached(seconds: Float) = before < seconds && victorySeconds >= seconds

        BOSS_AFTERSHOCK_SECONDS.forEachIndexed { index, seconds ->
            if (reached(seconds)) aftershock(index)
        }
        if (reached(VICTORY_FANFARE_DELAY_SECONDS)) playFanfare()
        if (bannerTime > 0) bannerTime -= deltaTime

        tickTime += deltaTime
        while (tickTime > tick) {
            tickTime -= tick
            contactClock += tick
            world.update(tick, game.input)
            comboMeter.update(tick, scoring.hitStreak, scoring.multiplier)
            // The blasts drift with the scenery, so the wreck they go off in drifts with them.
            for (i in wreck.indices) wreck[i] = wreck[i].offset(BURST_DRIFT, 0f)
        }

        if (stageCompleteShown) {
            stageCompleteArmingTime = STAGE_COMPLETE_ARMING_SECONDS
            overlayTaps.reset()
            // The overlay says it now, and nothing counts the banner down under it.
            bannerTime = 0f
        }
    }

    /**
     * The wreck bursting again, [index] blasts after the first, somewhere on a piece of the boss:
     * one of the blasts in the boss's death sound, seen as well as heard.
     */
    private fun aftershock(index: Int) {
        val piece = wreck.randomOrNull(random) ?: return
        val blast = env.assets.graphics.explosion
        val scale =
            piece.height / blast.height * BOSS_AFTERSHOCK_SCALE * BOSS_AFTERSHOCK_FALLOFF.pow(index)
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
     * The stage complete overlay's controls: a tap or Confirm flies on to the next stage, if there
     * is one; Back leaves for the menu, as does a tap on the last stage. Armed on a delay, like the
     * pause overlay, so a steering finger lifting is not read as leaving.
     */
    private fun handleStageCompleteControls(deltaTime: Float) {
        stageCompleteArmingTime -= deltaTime

        val input = game.input
        // Read whether or not it can act on them, so events do not pile up.
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
     * Flies on to stage [id] on a fresh screen, which resets the score, the bat's level and its
     * power-ups. The highscore was banked on the tick the boss went down.
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

        // Back pauses a running game and quits a paused one, so taking over Android's Back still
        // lets the player out, in two presses.
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
        // Read even when it cannot resume, so events do not pile up and reach the bat all at once.
        val tapped = overlayTaps.taps(input?.touchEvents.orEmpty()).isNotEmpty()
        if (tapped && resumeArmingTime <= 0f) setPaused(false)
        return true
    }

    /** Pauses or resumes the run and its music, re-arming the pause overlay on every pause. */
    private fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        if (value) {
            resumeArmingTime = RESUME_ARMING_SECONDS
            overlayTaps.reset()
            music?.pause()
            // Held only while still playing: one that has ended would restart from the top.
            if (fanfare?.isPlaying == true) {
                fanfare?.pause()
                fanfareHeld = true
            }
        } else {
            resumeMusic()
        }
    }

    /**
     * Restores what a pause cut off: the stage's music during the run, the fanfare once it is won.
     * Nothing once the bat is dead, since the game over track owns playback then.
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

    /** The pause overlay: the run dimmed, and how to resume or quit. */
    private fun drawPauseOverlay() {
        g.drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
        drawCentered(text[Res.string.pause_title], 160, 30, EngineColors.CYAN)
        drawCentered(text[Res.string.pause_resume], 195, 15, EngineColors.WHITE)
        drawCentered(text[Res.string.pause_quit], 217, 15, EngineColors.WHITE)
    }

    /** Raises [highscore] to the run's score if it beat it, and stores it. */
    private fun saveHighscore() {
        if (scoring.score > highscore) {
            highscore = scoring.score
        }

        // Not on screenScope: this runs moments before the screen is disposed, and the write has to
        // outlive it.
        env.highscores.saveAsync(currentStage.id, highscore)
    }

    /**
     * The banner a new wave or the boss arrives on, held for [WAVE_BANNER_SECONDS]. Outlined, since
     * it lands over anything, and centered by its measured width.
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
     * Draws [text] centered across the frame by its measured width, as every overlay line is: the
     * platforms' faces disagree on widths, so no fixed x is centered in all of them.
     */
    private fun drawCentered(text: String, y: Int, fontSize: Int, color: Int) {
        g.drawString(text, centeredX(text, fontSize), y, fontSize, color)
    }

    /** The x that centers [text] at [fontSize] on the framebuffer. */
    private fun centeredX(text: String, fontSize: Int): Int =
        (game.frameBufferWidth - g.measureString(text, fontSize)) / 2

    /**
     * The stage complete overlay: the run's total against the stage's highscore, and the way on.
     * Drawn over the stage, so the boss's wreckage is still clearing behind it.
     */
    private fun drawStageCompleteOverlay() {
        g.drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
        drawCentered(text[Res.string.stage_complete_title], 140, 30, EngineColors.YELLOW)
        drawCentered(text[currentStage.name], 170, 15, EngineColors.CYAN)

        val score = text.format(Res.string.score, scoring.score)
        // Already raised by this run if it set the record, so the two numbers then match.
        val record = text.format(Res.string.stage_highscore, highscore)
        // The two share a left edge and are centered as one block, by the wider, so they do not
        // stagger.
        val width = maxOf(g.measureString(score, 20), g.measureString(record, 15))
        val left = (game.frameBufferWidth - width) / 2
        g.drawString(score, left, 195, 20, EngineColors.WHITE)
        g.drawString(record, left, 217, 15, EngineColors.CYAN)

        val next = nextStageId
        if (next != null) {
            drawCentered(text.format(Res.string.stage_complete_next, next), 247, 15, EngineColors.WHITE)
            drawCentered(text[Res.string.stage_complete_menu], 269, 15, EngineColors.WHITE)
            drawCentered(text[Res.string.stage_complete_fresh_start], 294, 13, EngineColors.CYAN)
        } else {
            drawCentered(text[Res.string.stage_complete_continue], 247, 15, EngineColors.WHITE)
        }
    }

    /**
     * The level-up dialog: the bat's new level and the cards on offer, drawn into the frame and
     * centered by measured width like the other overlays. Each card is its own tap target and
     * carries no number; the number keys still pick by position, as a keyboard shortcut.
     */
    private fun drawPowerUpOffer() {
        g.drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
        drawCentered(text.format(Res.string.level_up_title, progress.level), 90, 30, EngineColors.YELLOW)
        drawCentered(text[Res.string.level_up_choose], 120, 15, EngineColors.WHITE)

        offer.forEachIndexed { index, powerUp ->
            drawPowerUpCard(index, powerUp)
        }
    }

    /** Card [index] of the offer: its panel, title and wrapped description. */
    private fun drawPowerUpCard(index: Int, powerUp: PowerUp) {
        val left = cardLeft(index)
        g.apply {
            // A panel with a cyan lip, so a card reads as something to press rather than words
            // over the run.
            drawRect(left, POWER_UP_CARD_TOP, POWER_UP_CARD_WIDTH, POWER_UP_CARD_HEIGHT, CARD_FILL)
            drawRect(left, POWER_UP_CARD_TOP, POWER_UP_CARD_WIDTH, 2, EngineColors.CYAN)

            drawString(
                text[powerUp.title],
                left + POWER_UP_CARD_PADDING,
                POWER_UP_CARD_TOP + 26,
                14,
                EngineColors.CYAN
            )
            // Wrapped by measured width, not character count: a count that fits in Arial runs off
            // the card in the wider DejaVu Sans.
            val description = text.format(powerUp.describe(loadout), *powerUp.numbers.toTypedArray())
            wrapWords(description, POWER_UP_CARD_WIDTH - 2 * POWER_UP_CARD_PADDING) { measureString(it, 11) }
                .forEachIndexed { line, words ->
                    drawString(
                        words,
                        left + POWER_UP_CARD_PADDING,
                        POWER_UP_CARD_TOP + 48 + line * 14,
                        11,
                        EngineColors.WHITE
                    )
                }
        }
    }

    /**
     * The experience bar, along the top edge, a strip nothing else uses: read out of the corner of
     * the eye, it says how close the next pick is without asking to be looked at.
     */
    private fun drawExperienceBar() {
        val filled = (game.frameBufferWidth * progress.fraction).roundToInt()
        g.drawRect(0, 0, game.frameBufferWidth, XP_BAR_HEIGHT, XP_BAR_EMPTY)
        if (filled > 0) g.drawRect(0, 0, filled, XP_BAR_HEIGHT, EngineColors.CYAN)
    }

    /**
     * The stage timer, top center, in minutes and seconds. Read off the wave readout's clock, so it
     * holds still wherever the stage does and stops when the run ends; see [finalStageSeconds].
     *
     * Outlined, since stalactites and the bat pass through this strip, and white, so it does not
     * run into the cyan bar above. Every digit has the same advance in the faces it is drawn in,
     * so centering it by measured width does not jitter.
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
     * The text HUD. Health is not in it, since it rides under the bat, where the player looks; nor
     * the highscore, a record for between runs that the end screens and stage select show.
     *
     * Every line is outlined, since cyan all but vanishes against the desert's bleached noon sky.
     */
    private fun drawStats() {
        // Always shown, even at x1, so the player learns the multiplier exists. Drawn first, since
        // its fire reaches up behind the lines above, and lowest in the column, so its growing
        // count runs into nothing.
        comboMeter.draw(g, 5, COMBO_BASELINE, text)
        g.apply {
            drawOutlinedString(text.format(Res.string.score, scoring.score), 5, 20, 15, EngineColors.CYAN)
            // How far into the stage the player is, and so how close the boss is.
            drawOutlinedString(waveLabel(), 5, 40, 15, EngineColors.CYAN)
            // The bat's level, top right where the experience bar fills toward, 5 px in like the
            // left column and right-aligned by its measured width.
            val level = text.format(Res.string.hud_level, progress.level)
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
     * The wave readout: which minute the player is in, or that the boss is here. Numbered from one
     * for the player, where the code indexes waves from zero.
     */
    private fun waveLabel(): String = when {
        enmGen.bossSpawned -> text[Res.string.hud_boss]
        else -> text.format(Res.string.hud_wave, enmGen.currentWave.index + 1, progression.bossWave)
    }

    /**
     * The stage's name across the middle of the frame for the run's opening seconds, outlined like
     * the banners because yellow does not read on the desert's pale noon sky.
     */
    private fun drawStageName() {
        val name = text[currentStage.name]
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

    /**
     * What the run is playing now: the stage's own piece, or once the boss is here, the boss fight's,
     * for a stage that has one.
     */
    private val score: StageMusic
        get() = currentStage.music.boss?.takeIf { enmGen.bossSpawned } ?: currentStage.music

    /** Opens [score]'s stems and a director for them, or returns null without audio. */
    private fun openStageMusic(): LayeredMusic? {
        val audio = game.audio ?: return null
        val score = score
        return audio.newLayeredMusic(score.stems, score.grid).also {
            music = it
            director = MusicDirector(it, score.grid, progression.bossWave, progression.difficulty)
        }
    }

    /**
     * The boss arrives: the music drops to its bed for a bar and slams in on the downbeat. For a
     * stage whose boss has a piece of its own, the stage's music stops dead and the boss's opens on
     * its bed alone.
     */
    private fun onBossMusic() {
        if (currentStage.music.boss != null && music != null) {
            music?.dispose()
            music = null
            director = null
            startStageMusic()
        }
        director?.onBossArrived()
    }

    /**
     * The host going away pauses the run outright, not just its music, so the player is not hit
     * before looking at the screen again.
     */
    override fun pause() {
        setPaused(true)
    }

    /**
     * Leaves [paused] set, so coming back to the app does not drop the player straight into a
     * dodge; they resume when ready.
     */
    override fun resume() {
        // Only playback needs restoring, and only if the run is not paused.
        if (paused) return
        resumeMusic()
    }

    override fun dispose() {
        screenScope.cancel()
        // The next stage's screen opens its own music, so this one's and the fanfare must go, or
        // they would play on over it.
        music?.dispose()
        music = null
        director = null
        fanfare?.dispose()
        fanfare = null
    }

    private companion object {
        /** A power-up card's panel: dark enough to read white text on, over a dimmed run. */
        const val CARD_FILL = 0xE6101820.toInt()

        /** The unfilled part of the experience bar. */
        const val XP_BAR_EMPTY = 0x80000000.toInt()

        /**
         * The combo readout's baseline, under the score and the wave, with extra room above, since
         * its count swells upward as the streak climbs.
         */
        const val COMBO_BASELINE = 66

        /** Degrees in a radian. */
        const val DEGREES_PER_RADIAN = 57.29578f

        /**
         * The lowest z index drawn over a halo: the scenery strip sits below it, and every sprite
         * of the run, the obstacles up, at or above it.
         */
        const val SPRITE_LAYERS_FROM = 0

        /**
         * The lowest z index drawn over the dark, in a stage flown in it: the shots at 15, the bat at
         * 20 and the blasts at 50. The obstacles, the creatures and the bosses, from 5 to 13, sit
         * under it and are lit by whatever reaches them.
         */
        const val LIGHTS_FROM = 15

        /**
         * The size of each boss part's blast against the part. Under one, since ten go off at once,
         * and at full size they would be a single wall of fire.
         */
        const val BODY_BLAST_SCALE = 0.85f
    }
}
