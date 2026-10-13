package at.smiech.cyanbat.scenery

import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.PixelPixmap
import at.smiech.engine.ecs.LightingSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Makes nothing but pixels: the scenery's chunks, kept as [PixelPixmap]s. */
private object PixelsOnly : Graphics {
    override fun newPixmap(filename: String): Pixmap = PixelPixmap(1, 1, IntArray(1))
    override fun clear(color: Int) = Unit
    override fun drawPixel(x: Int, y: Int, color: Int) = Unit
    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) = Unit
    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int) = Unit
    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) = Unit
    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int,
    ) = Unit

    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int,
        rotationDegrees: Float,
    ) = Unit

    override fun drawPixmapSilhouette(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int,
        color: Int,
    ) = Unit

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) = Unit
    override fun measureString(s: String, fontSize: Int): Int = 0
    override val width = 640
    override val height = 360
}

/** The endless stage's country as the run goes on: its lands, its weather and its dark. */
class EndlessSceneryTest {

    /** Run seconds at which the day is at [position], from the run's start. */
    private fun secondsAt(position: Float): Float =
        ((position - EndlessDay.START + 1f) % 1f) * EndlessDay.SECONDS

    @Test
    fun `the dark comes only with the night and never with a storm by day`() {
        // The swamp, whose rain peaks in storms with lightning.
        val scenery = EndlessScenery(PixelsOnly, 640, Random(3), seed = 11, first = Biome.SWAMP, startSeconds = secondsAt(0.08f))
        var stormy = false
        while (scenery.position < 0.55f) {
            scenery.update(TICK_INITIAL)
            if (scenery.storm > 0f) stormy = true
            assertEquals(LightingSystem.DAYLIGHT, scenery.ambient(), "dark by day at ${scenery.position}, storm ${scenery.storm}")
        }
        assertTrue(stormy, "no storm came to test the day against")
    }

    @Test
    fun `at night the dark falls`() {
        val scenery = EndlessScenery(PixelsOnly, 640, Random(3), seed = 11, first = Biome.PEAKS, startSeconds = secondsAt(0.8f))
        scenery.update(TICK_INITIAL)
        assertNotEquals(LightingSystem.DAYLIGHT, scenery.ambient())
    }

    @Test
    fun `the land changes every so often and never to the land it was`() {
        val scenery = EndlessScenery(PixelsOnly, 640, Random(9), seed = 4)
        val seen = mutableListOf(scenery.biome)
        repeat((1000f / TICK_INITIAL).toInt()) {
            scenery.update(TICK_INITIAL)
            if (scenery.biome != seen.last()) seen += scenery.biome
        }
        assertTrue(seen.size >= 9, "only ${seen.size} lands in a thousand seconds")
        seen.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        assertEquals(Biome.entries.toSet(), seen.take(Biome.entries.size).toSet(), "a land came twice before every land came once")
    }
}
