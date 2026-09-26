package at.smiech.cyanbat.desktop.recorder

/**
 * The colors of one clip as a single GIF palette, and the lookup that maps its frames onto it.
 *
 * Exact whenever the clip has few enough colors, which pixel art usually does: each background is a
 * dozen colors. What pushes a clip over is blending - the dimmed overlays, the fading wake and damage
 * numbers - and then the palette is cut down by median cut, weighted by how often each color is
 * used, and every color maps to its nearest entry. Never dithered: dither is noise, and noise is
 * what a pixel-art GIF can least afford, to the eye and in bytes.
 */
class Palette private constructor(
    /** RGB entries, at most [GifEncoder.MAX_COLORS]. */
    val colors: IntArray,
    private val lookup: HashMap<Int, Int>,
) {
    /** [frame]'s RGB pixels as indices into [colors]. */
    fun indicesOf(frame: IntArray): ByteArray {
        val out = ByteArray(frame.size)
        var lastRgb = -1
        var lastIndex = 0
        for (i in frame.indices) {
            val rgb = frame[i] and RGB_MASK
            // Runs of one color are most of a pixel-art frame, so the previous answer usually holds.
            if (rgb != lastRgb) {
                lastRgb = rgb
                lastIndex = lookup[rgb] ?: error("Color %06x was not in the frames the palette was built from".format(rgb))
            }
            out[i] = lastIndex.toByte()
        }
        return out
    }

    /** Collects the colors of every frame handed to [add], then builds the palette from them. */
    class Builder {
        private val counts = HashMap<Int, Long>()

        fun add(frame: IntArray) {
            var run = frame[0] and RGB_MASK
            var length = 0L
            for (pixel in frame) {
                val rgb = pixel and RGB_MASK
                if (rgb == run) {
                    length++
                } else {
                    counts.merge(run, length) { a, b -> a + b }
                    run = rgb
                    length = 1
                }
            }
            counts.merge(run, length) { a, b -> a + b }
        }

        fun build(maxColors: Int = GifEncoder.MAX_COLORS): Palette {
            val distinct = counts.keys.toIntArray()
            val entries = if (distinct.size <= maxColors) {
                // Most used first, so the table reads sensibly in a GIF inspector.
                distinct.sortedByDescending { counts.getValue(it) }.toIntArray()
            } else {
                medianCut(distinct, LongArray(distinct.size) { counts.getValue(distinct[it]) }, maxColors)
            }
            val lookup = HashMap<Int, Int>(distinct.size * 2)
            for (rgb in distinct) lookup[rgb] = nearest(entries, rgb)
            return Palette(entries, lookup)
        }
    }

    private companion object {
        const val RGB_MASK = 0xFFFFFF

        fun channel(rgb: Int, shift: Int) = rgb shr shift and 0xFF

        fun nearest(palette: IntArray, rgb: Int): Int {
            var best = 0
            var bestDistance = Int.MAX_VALUE
            for (i in palette.indices) {
                val dr = channel(palette[i], 16) - channel(rgb, 16)
                val dg = channel(palette[i], 8) - channel(rgb, 8)
                val db = channel(palette[i], 0) - channel(rgb, 0)
                // Weighted toward green, roughly as the eye is.
                val distance = 3 * dr * dr + 4 * dg * dg + 2 * db * db
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            return best
        }

        /**
         * Splits the color cube into [target] boxes, always cutting the box that is widest in some
         * channel weighted by how many pixels it covers, at the pixel-weighted median of that channel.
         * Each box becomes its members' pixel-weighted mean.
         */
        fun medianCut(colors: IntArray, counts: LongArray, target: Int): IntArray {
            val boxes = mutableListOf(colors.indices.toList())
            while (boxes.size < target) {
                var pick = -1
                var pickScore = 0.0
                var pickShift = 0
                for ((b, box) in boxes.withIndex()) {
                    if (box.size < 2) continue
                    val weight = box.sumOf { counts[it] }.toDouble()
                    for (shift in intArrayOf(16, 8, 0)) {
                        val range = box.maxOf { channel(colors[it], shift) } - box.minOf { channel(colors[it], shift) }
                        val score = range * Math.sqrt(weight)
                        if (score > pickScore) {
                            pickScore = score
                            pick = b
                            pickShift = shift
                        }
                    }
                }
                if (pick < 0) break
                val sorted = boxes[pick].sortedBy { channel(colors[it], pickShift) }
                val half = sorted.sumOf { counts[it] } / 2
                var running = 0L
                var cut = 1
                for (i in sorted.indices) {
                    running += counts[sorted[i]]
                    if (running >= half) {
                        cut = (i + 1).coerceIn(1, sorted.size - 1)
                        break
                    }
                }
                boxes[pick] = sorted.subList(0, cut)
                boxes += sorted.subList(cut, sorted.size)
            }
            return IntArray(boxes.size) { b ->
                val box = boxes[b]
                val weight = box.sumOf { counts[it] }.toDouble()
                fun mean(shift: Int) = (box.sumOf { channel(colors[it], shift) * counts[it].toDouble() } / weight)
                    .toInt().coerceIn(0, 255)
                (mean(16) shl 16) or (mean(8) shl 8) or mean(0)
            }
        }
    }
}
