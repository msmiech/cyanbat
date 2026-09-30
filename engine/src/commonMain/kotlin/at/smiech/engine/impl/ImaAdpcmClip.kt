package at.smiech.engine.impl

/**
 * A WAV file of IMA ADPCM audio, held compressed and decoded a block at a time as it plays.
 *
 * IMA ADPCM stores each sample as a 4-bit step from the one before, so a stem costs a quarter of
 * its PCM size. That is as far as compression goes without a perceptual codec, and it is what the
 * 16-bit consoles did too: the SNES kept its instruments as a close cousin of this. What it buys
 * over MP3 is exactness. The file holds precisely the frames that were written, with no encoder
 * delay or padding at either end, so a stem loops without a gap and every stem of a piece starts on
 * the same sample. And the decoder is fifty lines of common code, where MP3 needs a platform codec.
 *
 * This is Microsoft's layout (format tag 0x0011), which ordinary audio players also open: blocks of
 * [blockAlign] bytes, each starting with every channel's first sample and step index written out in
 * full, followed by the channels' nibbles interleaved four bytes at a time.
 */
class ImaAdpcmClip private constructor(
    val sampleRate: Int,
    val channels: Int,
    /** Length in frames: a sample from every channel. */
    val frames: Int,
    private val blockAlign: Int,
    private val framesPerBlock: Int,
    private val bytes: ByteArray,
    private val dataOffset: Int,
    private val dataLength: Int,
) {
    /** A read position of its own into the clip. Cursors share the clip's bytes and nothing else. */
    fun cursor(): Cursor = Cursor()

    /**
     * Reads the clip from the top, round and round: the frame after the last one is the first.
     *
     * Decoding runs a block ahead of the reader into a buffer of its own, and a block always
     * restarts from the sample and step written in its header. That is what makes the loop exact:
     * going back to the top is starting the first block again, with nothing carried over.
     */
    inner class Cursor internal constructor() {
        private val block = ShortArray(framesPerBlock * channels)
        private var blockIndex = -1
        private var framesInBlock = 0
        private var frameInBlock = 0

        /**
         * Writes the next [count] frames into [dst] as interleaved stereo in -1..1, and moves on
         * past them. A mono clip goes to both sides.
         *
         * @param offset the first frame of [dst] to write, in frames.
         */
        fun read(dst: FloatArray, offset: Int, count: Int) {
            var out = offset * 2
            repeat(count) {
                if (frameInBlock >= framesInBlock) nextBlock()
                val base = frameInBlock * channels
                val left = block[base] * SCALE
                val right = if (channels > 1) block[base + 1] * SCALE else left
                dst[out++] = left
                dst[out++] = right
                frameInBlock++
            }
        }

        /** Back to the first frame, as if the cursor had just been made. */
        fun rewind() {
            blockIndex = -1
            framesInBlock = 0
            frameInBlock = 0
        }

        private fun nextBlock() {
            blockIndex++
            if (blockIndex * framesPerBlock >= frames) blockIndex = 0
            framesInBlock = decodeBlock(blockIndex, block)
            frameInBlock = 0
        }
    }

    /** Decodes block [index] into [out], interleaved, and returns how many frames it held. */
    private fun decodeBlock(index: Int, out: ShortArray): Int {
        val start = dataOffset + index * blockAlign
        val end = minOf(start + blockAlign, dataOffset + dataLength)
        val count = minOf(framesPerBlock, frames - index * framesPerBlock)

        val predictor = IntArray(channels)
        val stepIndex = IntArray(channels)
        for (ch in 0 until channels) {
            val header = start + 4 * ch
            predictor[ch] = ((bytes[header].toInt() and 0xFF) or (bytes[header + 1].toInt() shl 8))
                .toShort().toInt()
            stepIndex[ch] = (bytes[header + 2].toInt() and 0xFF).coerceIn(0, STEP_TABLE.lastIndex)
            out[ch] = predictor[ch].toShort()
        }

        // After the headers, each channel in turn gets four bytes - eight samples, low nibble
        // first - and then the next channel does, until the block runs out.
        var p = start + 4 * channels
        var frame = 1
        while (p + 4 * channels <= end && frame < count) {
            for (ch in 0 until channels) {
                for (b in 0 until 4) {
                    val byte = bytes[p++].toInt()
                    for (half in 0 until 2) {
                        val nibble = if (half == 0) byte and 0x0F else (byte shr 4) and 0x0F
                        val step = STEP_TABLE[stepIndex[ch]]
                        var diff = step shr 3
                        if (nibble and 4 != 0) diff += step
                        if (nibble and 2 != 0) diff += step shr 1
                        if (nibble and 1 != 0) diff += step shr 2
                        val next = if (nibble and 8 != 0) predictor[ch] - diff else predictor[ch] + diff
                        predictor[ch] = next.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        stepIndex[ch] = (stepIndex[ch] + INDEX_TABLE[nibble and 7])
                            .coerceIn(0, STEP_TABLE.lastIndex)
                        val at = frame + b * 2 + half
                        if (at < count) out[at * channels + ch] = predictor[ch].toShort()
                    }
                }
            }
            frame += 8
        }
        return count
    }

    companion object {
        private const val FORMAT_IMA_ADPCM = 0x0011
        private const val SCALE = 1f / 32768f

        /** How far the step size moves for each nibble's magnitude. */
        private val INDEX_TABLE = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8)

        /** The standard 89 step sizes, roughly ten percent apart. */
        private val STEP_TABLE = intArrayOf(
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55,
            60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411,
            1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
            5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500,
            20350, 22385, 24623, 27086, 29794, 32767,
        )

        /**
         * Reads the RIFF chunks this needs - `fmt `, `fact` and `data` - and skips the rest.
         *
         * `fact` is required rather than guessed at: it is the only place the exact length is
         * written down, and the last block is usually part empty.
         */
        fun parse(bytes: ByteArray): ImaAdpcmClip {
            require(bytes.size >= 12 && bytes.ascii(0) == "RIFF" && bytes.ascii(8) == "WAVE") {
                "Not a WAV file"
            }
            var format = -1
            var channels = 0
            var sampleRate = 0
            var blockAlign = 0
            var bits = 0
            var framesPerBlock = 0
            var frames = -1
            var dataOffset = -1
            var dataLength = 0

            var p = 12
            while (p + 8 <= bytes.size) {
                val id = bytes.ascii(p)
                val size = bytes.int32(p + 4)
                val body = p + 8
                when (id) {
                    "fmt " -> {
                        format = bytes.int16(body)
                        channels = bytes.int16(body + 2)
                        sampleRate = bytes.int32(body + 4)
                        blockAlign = bytes.int16(body + 12)
                        bits = bytes.int16(body + 14)
                        if (size >= 20) framesPerBlock = bytes.int16(body + 18)
                    }

                    "fact" -> frames = bytes.int32(body)
                    "data" -> {
                        dataOffset = body
                        dataLength = minOf(size, bytes.size - body)
                    }
                }
                // Chunks are padded to an even length.
                p = body + size + (size and 1)
            }

            require(format == FORMAT_IMA_ADPCM && bits == 4) {
                "Only IMA ADPCM is supported, not format $format at $bits bits"
            }
            require(channels == 1 || channels == 2) { "Mono or stereo only, not $channels channels" }
            require(dataOffset >= 0) { "No data chunk" }
            require(frames > 0) { "No fact chunk, so no exact length" }
            val expectedPerBlock = (blockAlign - 4 * channels) * 8 / (4 * channels) + 1
            require(framesPerBlock == expectedPerBlock) {
                "$framesPerBlock frames per block does not fit a $blockAlign-byte block"
            }
            return ImaAdpcmClip(
                sampleRate, channels, frames, blockAlign, framesPerBlock, bytes, dataOffset, dataLength
            )
        }

        private fun ByteArray.ascii(at: Int): String =
            CharArray(4) { (this[at + it].toInt() and 0xFF).toChar() }.concatToString()

        private fun ByteArray.int16(at: Int): Int =
            (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

        private fun ByteArray.int32(at: Int): Int = int16(at) or (int16(at + 2) shl 16)
    }
}
