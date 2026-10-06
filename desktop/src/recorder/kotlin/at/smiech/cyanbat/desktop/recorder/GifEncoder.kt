package at.smiech.cyanbat.desktop.recorder

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Writes an animated, endlessly looping GIF of palette-indexed frames.
 *
 * Written by hand rather than through ImageIO because that is where the file size goes. A frame
 * identical to the one before it is not stored at all (the one before is shown for longer), and
 * every other frame after the first is stored as only the rectangle that changed.
 *
 * Inside that rectangle, pixels that did not change can be left transparent, so the frame beneath
 * shows through. That is a large saving over a still overlay and a loss over the scrolling scenery,
 * where nearly every pixel moves and the few that happen to match only break up the runs the
 * compression feeds on. So each frame is compressed a few ways (every unchanged pixel transparent,
 * only long runs of them, none) and whichever comes out smallest is the one written.
 *
 * Frames index the palette the encoder is made with, which is written once, as the file's global
 * color table, unless they are handed one of their own. A frame on another palette carries it as a
 * color table of its own. That costs 768 bytes a frame, but a reel of several stages would otherwise
 * squeeze every stage's colors into one palette, and each stage fills one by itself.
 *
 * @param palette RGB colors, at most [MAX_COLORS]. The index after the last one is transparency.
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    private val palette: IntArray,
) {
    /** The last frame handed in, held back until the next one says how long it stays up. */
    private var pending: ByteArray? = null

    /** The palette [pending] indexes. */
    private var pendingPalette = palette

    /** How long [pending] stays up so far, in centiseconds. */
    private var pendingDelay = 0

    /** What the viewer is looking at before [pending] is drawn over it, or null for nothing. */
    private var shown: ByteArray? = null

    /** The palette [shown] indexes. */
    private var shownPalette = palette

    /** The compressor, kept across frames for its tables. */
    private val lzw = LzwEncoder()

    init {
        requireFits(palette)
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        // A 256-entry global table (size field 7), 8 bits of color resolution, unsorted.
        out.write(0xF7)
        out.write(0) // background color index
        out.write(0) // no aspect ratio
        writeColorTable(palette)
        // NETSCAPE2.0: loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
    }

    /**
     * Adds a whole frame of indices into [palette], shown for [delayCentiseconds]. Frames on the
     * same palette are expected to hand in the same array, which is how a change of palette is told.
     */
    fun addFrame(pixels: ByteArray, delayCentiseconds: Int, palette: IntArray = this.palette) {
        require(pixels.size == width * height)
        requireFits(palette)
        val previous = pending
        if (previous != null && pendingPalette === palette && previous.contentEquals(pixels)) {
            pendingDelay += delayCentiseconds
            return
        }
        flushPending()
        pending = pixels.copyOf()
        pendingPalette = palette
        pendingDelay = delayCentiseconds
    }

    /** Writes the last frame and the trailer. The stream is left open. */
    fun finish() {
        flushPending()
        out.write(0x3B)
        out.flush()
    }

    /** Writes [pending], whole or as its changes from [shown]. */
    private fun flushPending() {
        val frame = pending ?: return
        val base = shown
        // An index means another color on another palette, so a frame that changes palettes cannot
        // be told apart from the one before it by its indices; it is written whole.
        if (base == null || pendingPalette !== shownPalette) {
            writeFrame(
                0,
                0,
                width,
                height,
                pendingDelay,
                useTransparency = false,
                lzw.encode(frame)
            )
        } else {
            writeChanges(frame, base)
        }
        shown = frame
        shownPalette = pendingPalette
        pending = null
    }

    /** The smallest rectangle holding every changed pixel, compressed whichever way is smallest. */
    private fun writeChanges(frame: ByteArray, base: ByteArray) {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (frame[row + x] != base[row + x]) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        // Only reached for frames that differ from the last, so the box is never empty.
        val boxWidth = right - left + 1
        val boxHeight = bottom - top + 1

        val transparent = pendingPalette.size.toByte()
        var best: ByteArray? = null
        var bestTransparent = false
        for (minRun in TRANSPARENT_RUNS) {
            val box = ByteArray(boxWidth * boxHeight)
            var anyTransparent = false
            for (y in 0 until boxHeight) {
                val source = (top + y) * width + left
                val target = y * boxWidth
                var x = 0
                while (x < boxWidth) {
                    if (frame[source + x] != base[source + x]) {
                        box[target + x] = frame[source + x]
                        x++
                        continue
                    }
                    // A run of unchanged pixels: transparent if it is long enough to be worth it.
                    var end = x
                    while (end < boxWidth && frame[source + end] == base[source + end]) end++
                    val clear = end - x >= minRun
                    for (i in x until end) box[target + i] =
                        if (clear) transparent else frame[source + i]
                    anyTransparent = anyTransparent || clear
                    x = end
                }
            }
            val encoded = lzw.encode(box)
            if (best == null || encoded.size < best.size) {
                best = encoded
                bestTransparent = anyTransparent
            }
        }
        writeFrame(left, top, boxWidth, boxHeight, pendingDelay, bestTransparent, best!!)
    }

    /**
     * Writes one frame: its control extension, its image descriptor, its own color table if it is
     * not on the global one, and [imageData].
     */
    private fun writeFrame(
        left: Int,
        top: Int,
        frameWidth: Int,
        frameHeight: Int,
        delay: Int,
        useTransparency: Boolean,
        imageData: ByteArray,
    ) {
        // Graphic control extension: leave the frame in place (disposal 1) for the next to draw on.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04))
        out.write((1 shl 2) or (if (useTransparency) 1 else 0))
        writeShort(delay)
        out.write(if (useTransparency) pendingPalette.size else 0)
        out.write(0)

        out.write(0x2C)
        writeShort(left)
        writeShort(top)
        writeShort(frameWidth)
        writeShort(frameHeight)
        if (pendingPalette === palette) {
            out.write(0) // no local color table, not interlaced
        } else {
            out.write(0x87) // a 256-entry local color table (size field 7), not interlaced
            writeColorTable(pendingPalette)
        }

        out.write(imageData)
    }

    /** [colors] padded out to a full 256-entry table, as three bytes each. */
    private fun writeColorTable(colors: IntArray) {
        for (i in 0 until TABLE_SIZE) {
            val rgb = if (i < colors.size) colors[i] else 0
            out.write(rgb shr 16 and 0xFF)
            out.write(rgb shr 8 and 0xFF)
            out.write(rgb and 0xFF)
        }
    }

    /** Writes [value] as a little-endian 16-bit number, as GIF stores every size and delay. */
    private fun writeShort(value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
    }

    /** Fails unless [colors] leaves an index free for transparency. */
    private fun requireFits(colors: IntArray) =
        require(colors.size <= MAX_COLORS) { "At most $MAX_COLORS colors, one index is transparency" }

    companion object {
        /** Real colors a palette may hold; the 256th index is spent on transparency. */
        const val MAX_COLORS = 255

        /** Entries in a written color table, the most GIF allows. */
        private const val TABLE_SIZE = 256

        /**
         * The shortest run of unchanged pixels each attempt leaves transparent: every one of them,
         * only runs long enough to compress well, and (past any row's width) none at all.
         */
        private val TRANSPARENT_RUNS = intArrayOf(1, 6, 24, Int.MAX_VALUE)
    }
}

/**
 * GIF's variable-width LZW over 8-bit indices, packed into 255-byte sub-blocks.
 *
 * The code width grows when the next free code no longer fits, and the table is cleared once all
 * 4096 codes are spent: the scheme every GIF decoder expects.
 */
private class LzwEncoder {
    /** The string table, open-addressed: each prefix code and symbol, packed into one key. */
    private val keys = IntArray(HASH_SIZE)

    /** The code each of [keys] was given. */
    private val codes = IntArray(HASH_SIZE)

    /** The image data of the frame being encoded. */
    private var out = ByteArrayOutputStream()

    /** The sub-block being filled, and how much of it is. */
    private val block = ByteArray(255)
    private var blockLength = 0

    /** Bits written but not yet a whole byte, low bits first, and how many there are. */
    private var bitBuffer = 0L
    private var bitCount = 0

    /** [pixels] as a GIF image data section: the minimum code size, the sub-blocks, the terminator. */
    fun encode(pixels: ByteArray): ByteArray {
        out = ByteArrayOutputStream(pixels.size / 4)
        out.write(MIN_CODE_SIZE)
        val clear = 1 shl MIN_CODE_SIZE
        val end = clear + 1

        var codeSize = MIN_CODE_SIZE + 1
        var next = clear + 2
        keys.fill(EMPTY)
        writeCode(clear, codeSize)

        var prefix = pixels[0].toInt() and 0xFF
        for (i in 1 until pixels.size) {
            val symbol = pixels[i].toInt() and 0xFF
            val key = (prefix shl 8) or symbol
            var slot = slotOf(key)
            while (keys[slot] != EMPTY && keys[slot] != key) slot = (slot + 1) and HASH_MASK
            if (keys[slot] == key) {
                prefix = codes[slot]
                continue
            }

            writeCode(prefix, codeSize)
            if (next < MAX_CODES) {
                keys[slot] = key
                codes[slot] = next++
                // The decoder adds each entry one code later than this side does, so it widens
                // its reads once the next code is past what the current width can hold.
                if (next > (1 shl codeSize) && codeSize < MAX_CODE_SIZE) codeSize++
            } else {
                writeCode(clear, codeSize)
                keys.fill(EMPTY)
                next = clear + 2
                codeSize = MIN_CODE_SIZE + 1
            }
            prefix = symbol
        }
        writeCode(prefix, codeSize)
        writeCode(end, codeSize)

        if (bitCount > 0) writeByte(bitBuffer.toInt() and 0xFF)
        bitBuffer = 0L
        bitCount = 0
        flushBlock()
        out.write(0) // block terminator
        return out.toByteArray()
    }

    /** Where the search for [key] starts in the table. */
    private fun slotOf(key: Int): Int = (key * HASH_MULTIPLIER) ushr (32 - HASH_BITS)

    /** Appends [code], [size] bits wide, low bits first. */
    private fun writeCode(code: Int, size: Int) {
        bitBuffer = bitBuffer or (code.toLong() shl bitCount)
        bitCount += size
        while (bitCount >= 8) {
            writeByte(bitBuffer.toInt() and 0xFF)
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    /** Appends a byte to the sub-block, writing it out once full. */
    private fun writeByte(value: Int) {
        block[blockLength++] = value.toByte()
        if (blockLength == block.size) flushBlock()
    }

    /** Writes out the sub-block, if it holds anything. */
    private fun flushBlock() {
        if (blockLength == 0) return
        out.write(blockLength)
        out.write(block, 0, blockLength)
        blockLength = 0
    }

    private companion object {
        /** Bits per pixel index; the codes start a bit wider, to make room for clear and end. */
        const val MIN_CODE_SIZE = 8

        /** The widest code GIF allows, and so how many codes a table holds. */
        const val MAX_CODE_SIZE = 12
        const val MAX_CODES = 1 shl MAX_CODE_SIZE

        /** The hash table's size as a power of two, comfortably more than [MAX_CODES]. */
        const val HASH_BITS = 14
        const val HASH_SIZE = 1 shl HASH_BITS
        const val HASH_MASK = HASH_SIZE - 1
        const val HASH_MULTIPLIER = -0x61c88647 // 0x9E3779B9, Fibonacci hashing

        /** A free slot in [keys]. */
        const val EMPTY = -1
    }
}
