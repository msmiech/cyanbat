package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.progress.PowerUp
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * What happened on one frame of a run: the facts the [Montage] picks its clips by.
 *
 * @param seconds the stage clock, which stops while a dialog is up.
 * @param enemies how many enemies are on screen, and [blasts] how many explosions.
 * @param hit whether the bat lost health on this frame.
 * @param pick the power-up taken on this frame, which is the frame a level up dialog closes on.
 */
data class Moment(
    val seconds: Float,
    val wave: Int,
    val bossSpawned: Boolean,
    val complete: Boolean,
    val offer: Boolean,
    val banner: String?,
    val enemies: Int,
    val blasts: Int,
    val health: Float,
    val hit: Boolean,
    val level: Int,
    val score: Int,
    val pick: PowerUp?,
)

/**
 * Every frame of one run, kept deflated so a whole stage fits in memory: at 480x320 a frame is
 * 600 KB raw and a few tens of KB compressed, and a stage runs to thousands of frames.
 *
 * Compression runs on worker threads so the run itself is not held up by it.
 */
class Tape(private val width: Int, private val height: Int) {
    private val workers: ExecutorService = Executors.newFixedThreadPool(WORKERS) { task ->
        Thread(task, "cyanbat-tape").apply { isDaemon = true }
    }
    private val frames = ArrayList<Future<ByteArray>>()
    private var settled = 0

    val moments = ArrayList<Moment>()
    val size: Int get() = frames.size

    fun add(pixels: IntArray, moment: Moment) {
        val copy = pixels.copyOf()
        frames += workers.submit<ByteArray> { deflate(copy) }
        moments += moment
        // A bound on what is waiting to be compressed, and so on the memory it holds.
        while (frames.size - settled > MAX_PENDING) frames[settled++].get()
    }

    /** Frame [index] as 0xRRGGBB pixels. */
    fun pixels(index: Int): IntArray = inflate(frames[index].get())

    fun close() {
        workers.shutdown()
    }

    private fun deflate(pixels: IntArray): ByteArray {
        val raw = ByteArray(pixels.size * 3)
        for (i in pixels.indices) {
            val rgb = pixels[i]
            raw[i * 3] = (rgb shr 16).toByte()
            raw[i * 3 + 1] = (rgb shr 8).toByte()
            raw[i * 3 + 2] = rgb.toByte()
        }
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(raw)
        deflater.finish()
        val out = java.io.ByteArrayOutputStream(raw.size / 8)
        val buffer = ByteArray(64 * 1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(packed: ByteArray): IntArray {
        val raw = ByteArray(width * height * 3)
        val inflater = Inflater()
        inflater.setInput(packed)
        var offset = 0
        while (offset < raw.size) offset += inflater.inflate(raw, offset, raw.size - offset)
        inflater.end()
        return IntArray(width * height) { i ->
            ((raw[i * 3].toInt() and 0xFF) shl 16) or
                ((raw[i * 3 + 1].toInt() and 0xFF) shl 8) or
                (raw[i * 3 + 2].toInt() and 0xFF)
        }
    }

    private companion object {
        const val WORKERS = 3
        const val MAX_PENDING = 48
    }
}
