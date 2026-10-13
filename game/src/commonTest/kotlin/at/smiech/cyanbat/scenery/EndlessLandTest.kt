package at.smiech.cyanbat.scenery

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The endless stage's land, as [EndlessLand] makes it a chunk at a time. */
class EndlessLandTest {

    private val chunk = EndlessLand.CHUNK
    private val grounds = listOf(Band.FAR, Band.MID, Band.NEAR)
    private val seed = 99

    private fun land(biome: Biome) = listOf(Stretch(0, biome))

    private fun cover(cell: Int) = CloudCover(0.3f + 0.1f * (cell % 5), if (cell % 3 == 0) 0.6f else 0f, 0f)

    @Test
    fun `the same land is made the same every time`() {
        for (biome in Biome.entries) for (band in grounds) {
            val a = EndlessLand.paint(band, chunk * 7, land(biome), seed)
            val b = EndlessLand.paint(band, chunk * 7, land(biome), seed)
            assertContentEquals(a.codes, b.codes, "$biome's $band")
        }
    }

    @Test
    fun `every chunk meets the next without a seam`() {
        for (biome in Biome.entries) for (band in Band.entries) {
            for (k in 0 until 6) assertJoins(band, k * chunk, land(biome), "$biome's $band at chunk $k")
        }
    }

    @Test
    fun `a land blending into the next meets itself across the blend too`() {
        val biomes = Biome.entries
        for (i in biomes.indices) {
            val stretches = listOf(Stretch(0, biomes[i]), Stretch(300, biomes[(i + 3) % biomes.size]))
            for (band in grounds) {
                for (k in 1 until 8) assertJoins(band, k * chunk, stretches, "${biomes[i]} into ${stretches[1].biome}, $band")
            }
        }
    }

    /** The chunk at [left] ends on the column the next one starts with. */
    private fun assertJoins(band: Band, left: Int, stretches: List<Stretch>, what: String) {
        val here = EndlessLand.paint(band, left, stretches, seed, ::cover)
        val next = EndlessLand.paint(band, left + chunk, stretches, seed, ::cover)
        for (y in 0 until here.height) {
            assertEquals(here.codes[y * here.width + chunk], next.codes[y * next.width], "$what, row $y")
        }
    }

    @Test
    fun `no sky shows between the bands near the ground`() {
        for (biome in Biome.entries) {
            for (k in 0 until 10) {
                // The middle band has ground from row 300 of the frame down to its own bottom, and the
                // near band from a little above its bottom edge down to the frame's.
                assertOpaque(Band.MID, k * chunk, biome, 300 until Band.MID.top + Band.MID.height)
                assertOpaque(Band.NEAR, k * chunk, biome, 344 until 360)
            }
        }
    }

    private fun assertOpaque(band: Band, left: Int, biome: Biome, frameRows: IntRange) {
        val painter = EndlessLand.paint(band, left, land(biome), seed)
        for (y in frameRows) {
            val row = y - band.top
            for (x in 0 until chunk) {
                assertTrue(painter.codes[row * painter.width + x] != 0, "$biome's $band is open at frame row $y, column ${left + x}")
            }
        }
    }

    @Test
    fun `volcanoes mark their craters for the smoke`() {
        val craters = (0 until 30).sumOf { EndlessLand.paint(Band.FAR, it * chunk, land(Biome.VOLCANO), seed).features.size }
        assertTrue(craters > 0, "no volcano in thirty chunks of the far band")
        val none = (0 until 30).sumOf { EndlessLand.paint(Band.FAR, it * chunk, land(Biome.PEAKS), seed).features.size }
        assertEquals(0, none)
    }

    @Test
    fun `every land stands obstacles on its ground and only the covered ones hang them`() {
        val openSky = setOf(Biome.DESERT, Biome.COAST, Biome.VOLCANO)
        for (biome in Biome.entries) {
            for (kind in 0 until EndlessLand.GROUND_KINDS) {
                val art = assertNotNull(EndlessLand.obstacle(biome, hanging = false, kind, seed), "$biome $kind")
                assertTrue(art.width in 20..100 && art.height in 24..70, "$biome's ground $kind is ${art.width}x${art.height}")
                // Standing on the ground: something in its last drawn row.
                val row = art.height - 2
                assertTrue((0 until art.width).any { art.codes[row * art.width + it] != 0 }, "$biome's ground $kind floats")
            }
            for (kind in 0 until EndlessLand.HANGING_KINDS) {
                val art = EndlessLand.obstacle(biome, hanging = true, kind, seed)
                if (biome in openSky) {
                    assertNull(art, "$biome has an open sky")
                } else {
                    assertNotNull(art, "$biome $kind")
                    assertTrue((0 until art.width).any { art.codes[it] != 0 || art.codes[art.width + it] != 0 }, "$biome's hanging $kind hangs from nothing")
                }
            }
        }
    }

    @Test
    fun `a land met again brings new obstacles of its kind`() {
        for (biome in Biome.entries) {
            val first = EndlessLand.obstacle(biome, hanging = false, 1, 1000)!!
            val again = EndlessLand.obstacle(biome, hanging = false, 1, 8919)!!
            assertTrue(first.width != again.width || first.height != again.height || !first.codes.contentEquals(again.codes), "$biome")
        }
    }
}
