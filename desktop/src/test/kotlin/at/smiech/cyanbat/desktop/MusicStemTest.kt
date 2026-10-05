package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.music.MusicLayer
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.Stage
import at.smiech.cyanbat.resource.StageMusic
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.engine.impl.ImaAdpcmClip
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The generated stems against the grid the game plays them on.
 *
 * The tempo and meter live in two places: the `tools/generate_*_music.py` script that writes a
 * stage's stems, and the `MusicGrid` in `GameAssets` that the mixer lands its changes on. Nothing
 * else ties them together. Regenerate a piece at a new tempo and forget the grid, and nothing
 * crashes: every layer just comes in a little off the beat, a little further off each time round.
 *
 * So this reads every stage's stems the way the game does and holds them to their grid: whole bars
 * long, every one dividing the longest, so the layers stay in step however long a run goes on. The
 * menu's, the game over's and the victory's tracks, which are generated the same way, are held to
 * the same format.
 */
class MusicStemTest {

    private val stages: List<Stage> = DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT).let {
        GameAssets.load(
            it.graphics,
            it.audio
        ).stages
    }

    /** The music that is not layered, from `tools/generate_{menu,game_over,victory}_music.py`. */
    private val tracks = listOf("music/menu.wav", "music/game_over.wav", GameAssets.VICTORY_MUSIC)

    private fun clip(name: String): ImaAdpcmClip {
        val stream = javaClass.getResourceAsStream("/$name")
        assertNotNull(stream, "$name is missing - regenerate it with its tools/generate_*_music.py")
        return ImaAdpcmClip.parse(stream.use { it.readBytes() })
    }

    /**
     * Every piece a stage plays, by the stage's name: its own, and its boss fight's where the boss is
     * fought to a piece of its own.
     */
    private val pieces: List<Pair<String, StageMusic>> =
        stages.flatMap { stage -> stage.music.pieces.map { stage.name to it } }

    @Test
    fun `every stage has a stem for every layer`() {
        for ((_, piece) in pieces) {
            assertEquals(MusicLayer.entries.size, piece.stems.size)
            piece.stems.forEach { clip(it) }
        }
    }

    @Test
    fun `stems and tracks are stereo at the output rate`() {
        for (name in pieces.flatMap { (_, piece) -> piece.stems } + tracks) {
            val stem = clip(name)
            assertEquals(OUTPUT_RATE, stem.sampleRate, "$name's sample rate")
            assertEquals(2, stem.channels, "$name's channels")
        }
    }

    /**
     * A bar has to be a whole number of frames, or the mixer's grid would drift against the music
     * a fraction of a frame a bar; and each stem a whole number of bars, or it would drift a
     * fraction of a bar each time it came round.
     */
    @Test
    fun `stems are whole bars at the tempo the game lands changes on`() {
        for ((stage, piece) in pieces) {
            val framesPerBar = piece.grid.framesPerBar(OUTPUT_RATE)
            assertEquals(
                framesPerBar,
                round(framesPerBar),
                "$stage (${piece.name}): a bar is not whole frames"
            )
            for (name in piece.stems) {
                val frames = clip(name).frames
                assertEquals(
                    0,
                    frames % framesPerBar.toInt(),
                    "$name is ${frames / framesPerBar} bars long"
                )
            }
        }
    }

    /** A short loop under a long one has to come round a whole number of times per turn of it. */
    @Test
    fun `every stem divides the longest`() {
        for ((_, piece) in pieces) {
            val lengths = piece.stems.associateWith { clip(it).frames }
            val longest = lengths.values.max()
            for ((name, frames) in lengths) {
                assertEquals(
                    0,
                    longest % frames,
                    "$name ($frames frames) against the longest ($longest)"
                )
            }
        }
    }

    /**
     * Something in every stem, and nothing pinned at full scale: the stems are leveled as a set so
     * that even the full mix only rarely reaches the mixer's soft clip, so a single stem that clips
     * on its own was not written by the generator.
     */
    @Test
    fun `stems and tracks carry sound without clipping`() {
        for (name in pieces.flatMap { (_, piece) -> piece.stems } + tracks) {
            val stem = clip(name)
            val samples =
                FloatArray(stem.frames * 2).also { stem.cursor().read(it, 0, stem.frames) }
            val rms = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size)
            val peak = samples.maxOf { abs(it) }
            assertTrue(rms > 0.01, "$name is nearly silent: rms $rms")
            assertTrue(peak < 0.99, "$name reaches full scale: peak $peak")
        }
    }

    private companion object {
        /** What the generators write, and so the rate the mixer runs at. */
        const val OUTPUT_RATE = 22050
    }
}
