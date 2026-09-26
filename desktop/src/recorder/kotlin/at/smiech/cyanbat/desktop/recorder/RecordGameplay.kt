package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.desktop.DesktopGame
import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.service.StageDesign
import at.smiech.cyanbat.ui.game.GameScreen
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
import java.awt.image.DataBufferInt
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.system.exitProcess

/**
 * Records the README's gameplay GIFs: flies each stage from its opening seconds to its boss on the
 * [Autopilot], and cuts the run down to a short clip of each part of it.
 *
 *     ./gradlew :desktop:recordGameplay
 *
 * Everything on screen is the game's own: the run is a [GameScreen] in a [DesktopGame], driven by
 * the shared [GameLoop] and drawn by the desktop graphics into its 480x320 framebuffer, which is
 * what gets recorded. Nothing is shown and nothing waits on the wall clock - the loop is handed a
 * clock that moves one tick per frame - so a five minute stage records in well under a minute.
 *
 * Runs are not repeatable, since the game rolls its spawns on an unseeded Random, so each recording
 * is a different flight; and the autopilot can lose, in which case the stage is simply flown again.
 *
 * Options: `--out=DIR` (default docs/gameplay), `--stages=1,2`, `--attempts=N`, `--ticks-per-frame=N`
 * for the GIF's frame rate, `--frames=DIR` to also write every clip frame as a PNG for a look, and
 * `--dry-run` to fly and report without recording.
 */
fun main(args: Array<String>) {
    val options = Options.parse(args)
    val failed = runBlocking(Dispatchers.Main) {
        options.stages.filterNot { record(it, options) }
    }
    if (failed.isNotEmpty()) {
        System.err.println("No recording for stage(s) ${failed.joinToString()}: the autopilot never cleared them")
    }
    // The run's coroutines and the Swing event thread would otherwise keep the JVM up.
    exitProcess(if (failed.isEmpty()) 0 else 1)
}

private const val FRAME_WIDTH = 480
private const val FRAME_HEIGHT = 320

/**
 * What the loop is handed per step: one tick and half a microsecond, so its float accumulator always
 * has a whole tick to spend and never rounds down to none. The excess piles up to a tick's worth only
 * after thirty-eight thousand steps, twice the length of a stage.
 */
private val STEP_NANOS = (TICK_INITIAL * 1e9).roundToLong() + 500L

/**
 * Game ticks per GIF frame. Three is about 17.5 frames a second: smooth enough for a side-scroller,
 * and a third fewer frames than two - which matters, because the scenery scrolls under every frame
 * and GIF has no way to say so, so each frame costs nearly a whole picture.
 */
private const val DEFAULT_TICKS_PER_FRAME = 3

/** How long the recorder lets a level up dialog sit before the autopilot picks, so it can be read. */
private const val OFFER_READ_SECONDS = 1.3f

/** How long the stage complete overlay is held at the end. */
private const val OUTRO_SECONDS = 2.6f

/** A stage still running this long is not going to be won; its boss arrives at five minutes. */
private const val GIVE_UP_SECONDS = 540f

/** The most hits a win may take and still be the one recorded; see [record]. */
private const val CLEAN_HITS = 2

/** Stage ids and the names their GIFs are written under. */
private val STAGE_FILES = mapOf(1 to "cave", 2 to "forest")

private class Options(
    val out: File,
    val stages: List<Int>,
    val attempts: Int,
    val ticksPerFrame: Int,
    val frames: File?,
    val dryRun: Boolean,
) {
    val frameSeconds: Float get() = TICK_INITIAL * ticksPerFrame

    companion object {
        fun parse(args: Array<String>): Options {
            val values = args.associate { arg ->
                require(arg.startsWith("--")) { "Unknown argument $arg" }
                arg.removePrefix("--").substringBefore('=') to arg.substringAfter('=', "")
            }
            return Options(
                out = File(values["out"]?.takeIf { it.isNotEmpty() } ?: "docs/gameplay"),
                stages = values["stages"]?.split(',')?.map { it.trim().toInt() } ?: STAGE_FILES.keys.toList(),
                attempts = values["attempts"]?.toInt() ?: 8,
                ticksPerFrame = values["ticks-per-frame"]?.toInt() ?: DEFAULT_TICKS_PER_FRAME,
                frames = values["frames"]?.let(::File),
                dryRun = "dry-run" in values,
            )
        }
    }
}

/**
 * Flies [stageId] until a run wins cleanly, then writes its GIF. False if every attempt was lost.
 *
 * Clean means taking no more than [CLEAN_HITS] hits: the footage is there to show the game, and a
 * bat that keeps getting clipped mostly shows the autopilot. When no win is that clean, the
 * cleanest one is used. A dry run records nothing and flies every attempt, as a measure of how
 * often the autopilot wins.
 */
private suspend fun record(stageId: Int, options: Options): Boolean {
    var won = false
    var best: Result? = null
    var bestTape: Tape? = null
    for (attempt in 1..options.attempts) {
        val flight = Flight(stageId, options.ticksPerFrame, capture = !options.dryRun)
        val result = flight.fly()
        println("Stage $stageId, attempt $attempt: $result")
        won = won || result.won
        val tape = flight.tape ?: continue
        if (result.won && (best == null || result.hits < best.hits)) {
            bestTape?.close()
            best = result
            bestTape = tape
            if (result.hits <= CLEAN_HITS) break
        } else {
            tape.close()
        }
    }
    val tape = bestTape ?: return won
    try {
        write(stageId, tape, options)
    } finally {
        tape.close()
    }
    return true
}

private fun write(stageId: Int, tape: Tape, options: Options) {
    val name = STAGE_FILES[stageId] ?: "stage$stageId"
    // The wave with the widest mix of enemies, where the stage shows the most of itself at once.
    val actionWave = StageDesign.forStage(stageId).waves.withIndex().maxBy { it.value.species.size }.index
    val clips = Montage.cut(tape.moments, options.frameSeconds, actionWave)
    val frames = clips.flatten()
    println(
        "  clips " + clips.joinToString { "%.1f-%.1fs".format(tape.moments[it.first()].seconds, tape.moments[it.last()].seconds) } +
            " of the stage clock, ${frames.size} frames"
    )

    val builder = Palette.Builder()
    for (i in frames) builder.add(tape.pixels(i))
    val palette = builder.build()

    options.out.mkdirs()
    val file = File(options.out, "$name.gif")
    // GIF delays are whole centiseconds. Rounding the running total rather than each frame keeps
    // the clip at the game's own speed on average, whatever the frame length.
    val frameCentiseconds = options.frameSeconds * 100f
    file.outputStream().buffered().use { out ->
        val gif = GifEncoder(out, FRAME_WIDTH, FRAME_HEIGHT, palette.colors)
        frames.forEachIndexed { n, i ->
            val delay = ((n + 1) * frameCentiseconds).roundToInt() - (n * frameCentiseconds).roundToInt()
            gif.addFrame(palette.indicesOf(tape.pixels(i)), delay)
        }
        gif.finish()
    }
    println("  wrote ${file.path}: ${file.length() / 1024} KB, ${palette.colors.size} colors")

    options.frames?.let { dir ->
        val stageDir = File(dir, name).apply { mkdirs() }
        val image = BufferedImage(FRAME_WIDTH, FRAME_HEIGHT, BufferedImage.TYPE_INT_RGB)
        frames.forEachIndexed { n, i ->
            image.setRGB(0, 0, FRAME_WIDTH, FRAME_HEIGHT, tape.pixels(i), 0, FRAME_WIDTH)
            ImageIO.write(image, "png", File(stageDir, "%04d.png".format(n)))
        }
    }
}

private class Result(val won: Boolean, val seconds: Float, val level: Int, val score: Int, val hits: Int, val reason: String) {
    override fun toString() =
        "${if (won) "won" else "lost ($reason)"} at %.1fs, level $level, score $score, $hits hits taken".format(seconds)
}

/**
 * One run of one stage, from a fresh [GameScreen] to the boss going down or the bat doing so.
 *
 * The host is the desktop's own [DesktopGame], so the frames are drawn exactly as the game window
 * draws them; only its surroundings are stand-ins. Scores and unlocks go nowhere, so recording
 * never touches the player's own highscores, and it is silent.
 *
 * The loop steps a tick at a time and the autopilot steers before every step, the way a player's
 * hand is on the stick for every frame; every [ticksPerFrame]th step is recorded.
 */
private class Flight(stageId: Int, private val ticksPerFrame: Int, capture: Boolean) {
    private val controls = ControlHandler()
    private val game = DesktopGame(FRAME_WIDTH, FRAME_HEIGHT, controls)
    private val assets = GameAssets.load(game.graphics, game.audio)
    private val screen = GameScreen(
        game,
        CyanBatEnvironment(
            assets = assets,
            haptics = Haptics.None,
            highscores = Unsaved,
            stageUnlocks = StageUnlockStore.InMemory(),
            onExitToMenu = {},
            audioSettings = Silent,
        ),
        stageId,
    )
    private val probe = RunProbe(screen)
    private val autopilot = Autopilot(controls, FRAME_WIDTH, FRAME_HEIGHT)
    val tape: Tape? = if (capture) Tape(FRAME_WIDTH, FRAME_HEIGHT) else null

    private var offerSteps = 0
    private var heldChoice: GameButton? = null

    /** The power-up the autopilot has asked for, and the offer it was picked from, until it lands. */
    private var picking: PowerUp? = null
    private var pickedFrom: List<PowerUp>? = null

    /** A pick that landed during the frame being stepped, for that frame's [Moment]. */
    private var landed: PowerUp? = null

    suspend fun fly(): Result {
        game.setScreen(screen)
        val loop = GameLoop(game)
        var clock = STEP_NANOS
        loop.frame(clock) // the first frame only starts the loop's clock

        val outroFrames = (OUTRO_SECONDS / (TICK_INITIAL * ticksPerFrame)).roundToInt()
        var completeFrames = 0
        var health = batHealth()
        var hits = 0

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

                val now = batHealth()
                val hit = now < health
                if (hit) hits++
                health = now
                tape?.add((game.frameBuffer.raster.dataBuffer as DataBufferInt).data, moment(hit, landed))
                landed = null

                when {
                    game.currentScreen !== screen || now <= 0f -> return result(false, hits, "shot down")
                    probe.stageComplete && ++completeFrames >= outroFrames -> return result(true, hits, "")
                    screen.enmGen.elapsedSeconds > GIVE_UP_SECONDS -> return result(false, hits, "out of time")
                }
            }
        } finally {
            screen.dispose()
            game.audio.dispose()
        }
    }

    /** Hands the autopilot the stick, or, while a level up dialog is up, lets it be read and then picks. */
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

    private fun result(won: Boolean, hits: Int, reason: String) =
        Result(won, screen.enmGen.elapsedSeconds, probe.level, probe.score, hits, reason)

    private fun batHealth(): Float {
        val health = probe.world.getComponent(probe.batId, HealthComponent::class) ?: return 0f
        return if (health.alive) health.hitPoints.toFloat() / health.maxHitPoints else 0f
    }

    private fun moment(hit: Boolean, pick: PowerUp?): Moment {
        val world = probe.world
        var enemies = 0
        for (id in world.query(CollisionComponent::class, TransformComponent::class)) {
            if (world.getComponent(id, CollisionComponent::class)?.group != CollisionGroup.ENEMY) continue
            val rect = world.getComponent(id, TransformComponent::class)!!.rect
            if (rect.right > 0f && rect.left < FRAME_WIDTH) enemies++
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
