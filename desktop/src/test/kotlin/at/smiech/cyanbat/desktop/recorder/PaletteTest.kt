package at.smiech.cyanbat.desktop.recorder

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteTest {

    /** Pixel art mostly fits: a clip with few enough colors keeps every one of them exactly. */
    @Test
    fun `a clip with few colors keeps them all`() {
        val frame = IntArray(100) { listOf(0x102030, 0xFFFFFF, 0x00C8FF)[it % 3] }
        val palette = Palette.Builder().apply { add(frame) }.build()

        assertEquals(3, palette.colors.size)
        val indices = palette.indicesOf(frame)
        assertEquals(frame.toList(), indices.map { palette.colors[it.toInt() and 0xFF] })
    }

    /**
     * The blends are what overflow a palette - a fading trail over the scenery, a dimmed overlay - and
     * they are cut down to what fits, each landing on a color near it.
     */
    @Test
    fun `a clip with too many colors is cut down to near ones`() {
        // A thousand shades of one ramp, the way a fade crosses a background.
        val frame = IntArray(1000) { (it / 4) * 0x010101 + (it % 4) }
        val palette = Palette.Builder().apply { add(frame) }.build()

        assertEquals(GifEncoder.MAX_COLORS, palette.colors.size)
        val indices = palette.indicesOf(frame)
        for (i in frame.indices) {
            val mapped = palette.colors[indices[i].toInt() and 0xFF]
            for (shift in intArrayOf(16, 8, 0)) {
                val error = abs((mapped shr shift and 0xFF) - (frame[i] shr shift and 0xFF))
                assertTrue(error <= 4, "%06x went to %06x".format(frame[i], mapped))
            }
        }
    }
}
