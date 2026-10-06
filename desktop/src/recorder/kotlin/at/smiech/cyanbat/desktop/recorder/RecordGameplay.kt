package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.desktop.DesktopGame
import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.resource.Backdrop
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.GameText
import at.smiech.cyanbat.service.StageDesign
import at.smiech.cyanbat.service.StageProgression
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.STAGE_COMPLETE_DELAY_SECONDS
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.GameButton
import at.smiech.engine.GameLoop
import at.smiech.engine.Haptics
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.impl.ControlHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.system.exitProcess

/**
 * Records the README's gameplay GIF: flies each stage on the [Autopilot], cuts each run down to a
 * few short clips, and strings the stages together into one reel, in play order.
 *
 *     ./gradlew :desktop:recordGameplay
 *
 * The reel gives away less of each stage than of the one before, so it shows what the game is
 * without playing through it for anyone; [STAGE_COVERAGE] says how much.
 *
 * Everything on screen is the game's own: the run is a [GameScreen] in a [DesktopGame], driven by
 * the shared [GameLoop], and each frame it presents is drawn at its own 640x360 for the tape
 * ([DesktopGame.capture]). Nothing is shown and nothing waits on the wall clock (the loop is handed
 * a clock that moves one tick per frame), so a five-minute stage records in well under a minute.
 *
 * Runs are not repeatable, since the game rolls its spawns on an unseeded Random, so each recording
 * is a different flight; and the autopilot can lose, in which case the stage is simply flown again.
 *
 * Options: `--out=FILE` (default docs/gameplay.gif), `--stages=1,2,3,4`, `--attempts=N`,
 * `--ticks-per-frame=N` for the GIF's frame rate, `--frames=DIR` to also write every frame of the
 * reel as a PNG for a look, and `--dry-run` to fly and report without recording.
 */
fun main(args: Array<String>) {
    // In English whatever the machine is set to: the reel is the README's, which is, and Montage
    // tells a wave's banner by its English word.
    Locale.setDefault(Locale.US)
    val options = Options.parse(args)
    val reel = mutableListOf<Footage>()
    val failed = runBlocking(Dispatchers.Main) {
        options.stages.filterNot { film(it, options, reel) }
    }
    when {
        // A reel missing a stage would quietly replace a whole one, so there is none.
        failed.isNotEmpty() -> System.err.println(
            "No reel written: the autopilot never got through stage(s) ${failed.joinToString()}"
        )

        !options.dryRun -> write(reel, options)
    }
    // The run's coroutines and the Swing event thread would otherwise keep the JVM up.
    exitProcess(if (failed.isEmpty()) 0 else 1)
}

/**
 * What the loop is handed per step: one tick and half a microsecond, so its float accumulator always
 * has a whole tick to spend and never rounds down to none. The excess piles up to a tick's worth only
 * after thirty-eight thousand steps, twice the length of a stage.
 */
private val STEP_NANOS = (TICK_INITIAL * 1e9).roundToLong() + 500L

/**
 * Game ticks per GIF frame. Three is about 17.5 frames a second: smooth enough for a side-scroller,
 * and a third fewer frames than two, which matters because the scenery scrolls under every frame
 * and GIF has no way to say so, so each frame costs nearly a whole picture.
 */
private const val DEFAULT_TICKS_PER_FRAME = 3

/** How long the recorder lets a level up dialog sit before the autopilot picks, so it can be read. */
private const val OFFER_READ_SECONDS = 1.3f

/**
 * How long a won run is taped after its boss goes down: the boss going up and the run playing on
 * until the stage complete overlay comes up, then the overlay held for a moment at the end.
 */
private const val OUTRO_SECONDS = STAGE_COMPLETE_DELAY_SECONDS + 2.6f

/**
 * How far past its last hour a flight whose footage is only the stage's hours goes on, on the stage
 * clock: a frame or two for the clip's end to land on.
 */
private const val SCENERY_TAIL_SECONDS = 0.5f

/** A stage still running this long is not going to be won; its boss arrives at five minutes. */
private const val GIVE_UP_SECONDS = 540f

/** The most hits a run may take and still be the one recorded; see [film]. */
private const val CLEAN_HITS = 2

/**
 * The stages in the reel, in play order, and how much of each it shows: less of each than of the
 * one before, so a player who has watched it still has most of the game to find. The jungle is
 * shown whole, from its title to the Moth Queen going down. The cave gets three glimpses: its
 * title, its busiest stretch and the Caco Imp arriving (lit, so what it does with its light is left
 * to find). The desert gets two, its title at noon and the sun going down, and its boss is never
 * shown: the Sand Wyrm is left for the player to find. The lagoon gets its title at night and a
 * shorter look at the sun coming up out of the sea, and nothing of its temple or its Naga.
 */
private val STAGE_COVERAGE = mapOf(
    1 to Coverage.WHOLE,
    2 to Coverage(actionSeconds = 2.4f, arrivalSeconds = 1.6f),
    3 to Coverage(scenerySeconds = 2.0f),
    4 to Coverage(scenerySeconds = 1.6f),
)

/** The command line's options; see [main]. */
private class Options(
    val out: File,
    val stages: List<Int>,
    val attempts: Int,
    val ticksPerFrame: Int,
    val frames: File?,
    val dryRun: Boolean,
) {
    /** Seconds of stage time per GIF frame. */
    val frameSeconds: Float get() = TICK_INITIAL * ticksPerFrame

    companion object {
        /** Reads `--name=value` and `--flag` arguments, and fails on anything else. */
        fun parse(args: Array<String>): Options {
            val values = args.associate { arg ->
                require(arg.startsWith("--")) { "Unknown argument $arg" }
                arg.removePrefix("--").substringBefore('=') to arg.substringAfter('=', "")
            }
            val stages = values["stages"]?.split(',')?.map { it.trim().toInt() }
                ?: STAGE_COVERAGE.keys.toList()
            require(stages.all { it in STAGE_COVERAGE }) {
                "The reel has no place for stage(s) ${(stages - STAGE_COVERAGE.keys).joinToString()}"
            }
            return Options(
                out = File(values["out"]?.takeIf { it.isNotEmpty() } ?: "docs/gameplay.gif"),
                stages = stages,
                attempts = values["attempts"]?.toInt() ?: 8,
                ticksPerFrame = values["ticks-per-frame"]?.toInt() ?: DEFAULT_TICKS_PER_FRAME,
                frames = values["frames"]?.let(::File),
                dryRun = "dry-run" in values,
            )
        }
    }
}

/**
 * One stage's part of the reel: its clips, back to back, as indices into a palette of its own.
 *
 * Each stage gets its own because each fills one by itself (the stages' colors have little in
 * common), and one palette cut down to hold them all would show.
 */
private class Footage(val palette: Palette, val frames: List<ByteArray>)

/**
 * Flies [stageId] until a run gets cleanly as far as its footage needs, then cuts that footage and
 * adds it to [reel]. False if every attempt fell short.
 *
 * Clean means taking no more than [CLEAN_HITS] hits: the footage is there to show the game, and a
 * bat that keeps getting clipped mostly shows the autopilot. When no run is that clean, the
 * cleanest one is used. A dry run records nothing and flies every attempt, as a measure of how
 * often the autopilot gets through.
 */
private suspend fun film(stageId: Int, options: Options, reel: MutableList<Footage>): Boolean {
    val coverage = STAGE_COVERAGE.getValue(stageId)
    var reached = false
    var best: Result? = null
    var bestTape: Tape? = null
    var scenery = emptyList<Float>()
    for (attempt in 1..options.attempts) {
        val flight = Flight(stageId, coverage, options.ticksPerFrame, capture = !options.dryRun)
        scenery = flight.scenery
        val result = flight.fly()
        println("Stage $stageId, attempt $attempt: $result")
        reached = reached || result.reached
        val tape = flight.tape ?: continue
        if (result.reached && (best == null || result.hits < best.hits)) {
            bestTape?.close()
            best = result
            bestTape = tape
            if (result.hits <= CLEAN_HITS) break
        } else {
            tape.close()
        }
    }
    val tape = bestTape ?: return reached
    try {
        reel += cut(stageId, coverage, tape, options, scenery)
    } finally {
        tape.close()
    }
    return true
}

/** Cuts [tape] down to the clips [coverage] asks for, on a palette of their own. */
private fun cut(
    stageId: Int,
    coverage: Coverage,
    tape: Tape,
    options: Options,
    scenery: List<Float>,
): Footage {
    // The wave with the widest mix of enemies, where the stage shows the most of itself at once.
    val actionWave =
        StageDesign.forStage(stageId).waves.withIndex().maxBy { it.value.species.size }.index
    val clips = Montage.cut(tape.moments, options.frameSeconds, actionWave, scenery, coverage)
    val frames = clips.flatten()

    val builder = Palette.Builder()
    for (i in frames) builder.add(tape.pixels(i))
    val palette = builder.build()
    val spans = clips.joinToString {
        "%.1f-%.1fs".format(tape.moments[it.first()].seconds, tape.moments[it.last()].seconds)
    }
    println("  clips $spans of the stage clock, ${frames.size} frames, ${palette.colors.size} colors")
    return Footage(palette, frames.map { palette.indicesOf(tape.pixels(it)) })
}

/** Writes [reel] as one GIF, and every frame of it as a PNG when asked to. */
private fun write(reel: List<Footage>, options: Options) {
    val file = options.out
    file.absoluteFile.parentFile.mkdirs()
    // GIF delays are whole centiseconds. Rounding the running total rather than each frame keeps
    // the reel at the game's own speed on average, whatever the frame length.
    val frameCentiseconds = options.frameSeconds * 100f
    var n = 0
    file.outputStream().buffered().use { out ->
        val gif =
            GifEncoder(out, FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, reel.first().palette.colors)
        for (footage in reel) {
            for (frame in footage.frames) {
                val delay =
                    ((n + 1) * frameCentiseconds).roundToInt() - (n * frameCentiseconds).roundToInt()
                gif.addFrame(frame, delay, footage.palette.colors)
                n++
            }
        }
        gif.finish()
    }
    val seconds = "%.1f".format(n * options.frameSeconds)
    println("Wrote ${file.path}: ${file.length() / 1024} KB, $n frames, ${seconds}s")

    options.frames?.let { dir ->
        dir.mkdirs()
        val image =
            BufferedImage(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, BufferedImage.TYPE_INT_RGB)
        var written = 0
        for (footage in reel) {
            val colors = footage.palette.colors
            for (frame in footage.frames) {
                // In the colors the GIF shows, which are the frame's own unless its palette overflowed.
                val pixels = IntArray(frame.size) { colors[frame[it].toInt() and 0xFF] }
                image.setRGB(
                    0, 0, FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, pixels, 0, FRAME_BUFFER_WIDTH,
                )
                ImageIO.write(image, "png", File(dir, "%04d.png".format(written++)))
            }
        }
    }
}

/**
 * How a flight ended. [reached] is whether it got as far as its stage's footage needs, which for a
 * stage not shown whole is short of winning it.
 */
private class Result(
    val reached: Boolean,
    val seconds: Float,
    val level: Int,
    val score: Int,
    val hits: Int,
    val outcome: String,
) {
    override fun toString() =
        "$outcome at %.1fs, level $level, score $score, $hits hits taken".format(seconds)
}

/**
 * One run of one stage, from a fresh [GameScreen] to as far as the stage's [coverage] needs (the
 * boss going down, its arrival, or the last moment before it), or to the bat going down first.
 *
 * The host is the desktop's own [DesktopGame], so the frames are drawn exactly as the game window
 * draws them; only its surroundings are stand-ins. Scores and unlocks go nowhere, so recording
 * never touches the player's own highscores, and it is silent.
 *
 * The loop steps a tick at a time and the autopilot steers before every step, the way a player's
 * hand is on the stick for every frame; every [ticksPerFrame]th step is recorded.
 */
private class Flight(
    stageId: Int,
    private val coverage: Coverage,
    private val ticksPerFrame: Int,
    capture: Boolean,
) {
    private val controls = ControlHandler()
    private val game = DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, controls)
    private val assets = GameAssets.load(game.graphics, game.audio)
    private val screen = GameScreen(
        game,
        CyanBatEnvironment(
            assets = assets,
            text = runBlocking { GameText.load() },
            haptics = Haptics.None,
            highscores = Unsaved,
            stageUnlocks = StageUnlockStore.InMemory(),
            onExitToMenu = {},
            audioSettings = Silent,
        ),
        stageId,
    )
    private val probe = RunProbe(screen)

    /**
     * Seconds of the stage clock worth a clip for how the stage looks then: for a stage whose sky
     * changes with the time of day, its showcase hour (the desert's sun going down behind the
     * dunes, the lagoon's coming up out of the sea), which the footage would otherwise skip.
     * Nothing for a stage that looks the same throughout.
     */
    val scenery: List<Float> = when (val backdrop = assets.stage(stageId).backdrop) {
        is Backdrop.Sky ->
            listOf(StageProgression.forStage(stageId).bossTimeSeconds * backdrop.day.showcase)
        is Backdrop.Strip -> emptyList()
    }
    private val autopilot = Autopilot(controls, FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT)

    /** Every frame recorded, or null on a dry run. */
    val tape: Tape? = if (capture) Tape(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT) else null

    /** Steps the level up dialog on screen has been up. */
    private var offerSteps = 0

    /** The choice key pressed on the last step, released on the next. */
    private var heldChoice: GameButton? = null

    /** The power-up the autopilot has asked for, and the offer it was picked from, until it lands. */
    private var picking: PowerUp? = null
    private var pickedFrom: List<PowerUp>? = null

    /** A pick that landed during the frame being stepped, for that frame's [Moment]. */
    private var landed: PowerUp? = null

    /** Flies the run to its end; see [Flight]. */
    suspend fun fly(): Result {
        game.setScreen(screen)
        val loop = GameLoop(game)
        var clock = STEP_NANOS
        loop.frame(clock) // the first frame only starts the loop's clock

        val frameSeconds = TICK_INITIAL * ticksPerFrame
        val outroFrames = (OUTRO_SECONDS / frameSeconds).roundToInt()
        var completeFrames = 0
        var health = batHealth()
        var hits = 0
        var bossArrivedAt = -1f
        val enmGen = screen.enmGen

        try {
            while (true) {
                repeat(ticksPerFrame) {
                    steer()
                    clock += STEP_NANOS
                    loop.frame(clock)
                    // Lets the run's own coroutines have the thread, as the window's frame callback would.
                    yield()
                    // Landed once the offer it was picked from is gone - even if, on a kill worth two
                    // levels, the next dialog has already taken its place.
                    if (picking != null && probe.offer !== pickedFrom) {
                        landed = picking
                        picking = null
                        pickedFrom = null
                    }
                }

                // A boss the footage keeps hidden is never taped, not even the frame it arrives on.
                if (enmGen.bossSpawned && !coverage.showsBoss) {
                    return result(true, hits, "flown to the boss")
                }

                val now = batHealth()
                val hit = now < health
                if (hit) hits++
                health = now
                tape?.add(game.capture(), moment(hit, landed))
                landed = null
                if (enmGen.bossSpawned && bossArrivedAt < 0f) bossArrivedAt = enmGen.elapsedSeconds

                when {
                    game.currentScreen !== screen || now <= 0f ->
                        return result(false, hits, "shot down")

                    // Footage that is only the stage's hours needs flying no further than the last of
                    // them: the lagoon's sunrise is two minutes in, and its Naga three minutes further.
                    coverage.onlyScenery && scenery.isNotEmpty() &&
                            enmGen.elapsedSeconds > scenery.max() + coverage.scenerySeconds + SCENERY_TAIL_SECONDS ->
                        return result(true, hits, "flown through its hours")

                    probe.stageComplete && ++completeFrames >= outroFrames ->
                        return result(true, hits, "won")

                    // A frame past the arrival clip, on the stage clock, which stops for dialogs just
                    // as the clip's own count of frames skips them.
                    !coverage.finale && bossArrivedAt >= 0f &&
                            enmGen.elapsedSeconds - bossArrivedAt > coverage.arrivalSeconds + frameSeconds ->
                        return result(true, hits, "flown through the boss's arrival")

                    enmGen.elapsedSeconds > GIVE_UP_SECONDS ->
                        return result(false, hits, "out of time")
                }
            }
        } finally {
            screen.dispose()
            game.audio.dispose()
        }
    }

    /**
     * Hands the autopilot the stick, or, while a level up dialog is up, lets it be read and then
     * picks.
     */
    private fun steer() {
        heldChoice?.let { controls.onButton(it, false) }
        heldChoice = null
        val offer = probe.offer
        if (offer.isEmpty()) {
            offerSteps = 0
            autopilot.fly(probe.world, probe.batId)
            return
        }
        autopilot.letGo()
        if (++offerSteps == (OFFER_READ_SECONDS / TICK_INITIAL).roundToInt()) {
            val choice = autopilot.choose(offer)
            picking = offer[choice]
            pickedFrom = offer
            GameButton.CHOICES[choice].let {
                controls.onButton(it, true)
                heldChoice = it
            }
        }
    }

    /** How the flight ended, as of now. */
    private fun result(reached: Boolean, hits: Int, outcome: String) =
        Result(reached, screen.enmGen.elapsedSeconds, probe.level, probe.score, hits, outcome)

    /** The bat's health as a fraction of its bar, or 0 once it is dead. */
    private fun batHealth(): Float {
        val health = probe.world.getComponent(probe.batId, HealthComponent::class) ?: return 0f
        return if (health.alive) health.hitPoints.toFloat() / health.maxHitPoints else 0f
    }

    /** What happened on the frame just stepped; see [Moment]. */
    private fun moment(hit: Boolean, pick: PowerUp?): Moment {
        val world = probe.world
        var enemies = 0
        for (id in world.query(CollisionComponent::class, TransformComponent::class)) {
            val group = world.getComponent(id, CollisionComponent::class)?.group
            if (group != CollisionGroup.ENEMY) continue
            val rect = world.getComponent(id, TransformComponent::class)!!.rect
            if (rect.right > 0f && rect.left < FRAME_BUFFER_WIDTH) enemies++
        }
        val explosion = assets.graphics.explosion
        val blasts = world.query(SpriteComponent::class).count {
            world.getComponent(it, SpriteComponent::class)?.pixmap === explosion
        }
        return Moment(
            seconds = screen.enmGen.elapsedSeconds,
            wave = screen.enmGen.currentWave.index,
            bossSpawned = screen.enmGen.bossSpawned,
            complete = probe.stageComplete,
            offer = probe.offer.isNotEmpty(),
            banner = probe.banner,
            enemies = enemies,
            blasts = blasts,
            health = batHealth(),
            hit = hit,
            level = probe.level,
            score = probe.score,
            pick = pick,
        )
    }
}

/** Highscores that go nowhere, so a recording never touches the player's own. */
private object Unsaved : HighscoreStore {
    override val byStage = MutableStateFlow(emptyMap<Int, Int>())
    override fun saveAsync(stageId: Int, value: Int) = Unit
}

/** A GIF has no sound, so the run makes none. */
private object Silent : AudioSettings {
    override val musicEnabled = false
    override val soundsEnabled = false
}
