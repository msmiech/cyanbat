package at.smiech.cyanbat.scenery

import at.smiech.cyanbat.scenery.EndlessDay.TimeOfDay
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The endless stage's palettes: one code per biome, material and facing, lit by the hour. */
class ScenePalettesTest {

    @Test
    fun `every code is its own`() {
        val seen = HashSet<Int>()
        for (biome in Biome.entries) for (material in 1 until biome.materials.size) for (shade in 0 until ScenePalettes.SHADES) {
            assertTrue(seen.add(ScenePalettes.code(biome, material, shade)), "$biome $material $shade")
        }
        for (material in 1..ScenePalettes.ASH_DARK) for (shade in 0 until ScenePalettes.SHADES) {
            assertTrue(seen.add(ScenePalettes.cloud(material, shade)))
        }
        assertTrue(seen.all { it in 1 until ScenePalettes.CODES })
    }

    @Test
    fun `air is never drawn`() {
        for (depth in ScenePalettes.Depth.entries) for (time in TimeOfDay.entries) {
            assertEquals(0, ScenePalettes.table(depth, time)[0])
        }
    }

    @Test
    fun `at noon close up a plain surface is its material's own color`() {
        val noon = ScenePalettes.table(ScenePalettes.Depth.NEAR, TimeOfDay.NOON)
        for (biome in Biome.entries) for (material in 1 until biome.materials.size) {
            val drawn = noon[ScenePalettes.code(biome, material, ScenePalettes.BODY)]
            for (shift in listOf(16, 8, 0)) {
                val want = (biome.materials[material] shr shift) and 0xFF
                val got = (drawn shr shift) and 0xFF
                assertTrue(abs(want - got) <= 1, "$biome's material $material is $got for $want")
            }
        }
    }

    @Test
    fun `what glows keeps its color at night and the rest darkens`() {
        val noon = ScenePalettes.table(ScenePalettes.Depth.NEAR, TimeOfDay.NOON)
        val night = ScenePalettes.table(ScenePalettes.Depth.NEAR, TimeOfDay.NIGHT)
        for (biome in Biome.entries) for (material in 1 until biome.materials.size) {
            val code = ScenePalettes.code(biome, material, ScenePalettes.BODY)
            if (biome.glows(material)) {
                assertEquals(noon[code], night[code], "$biome's glowing $material")
            } else {
                assertTrue(brightness(night[code]) < brightness(noon[code]), "$biome's $material is no darker at night")
            }
        }
    }

    @Test
    fun `a face turned to the light is the brightest`() {
        val morning = ScenePalettes.table(ScenePalettes.Depth.NEAR, TimeOfDay.DAWN)
        val evening = ScenePalettes.table(ScenePalettes.Depth.NEAR, TimeOfDay.SUNSET)
        val rock = ScenePalettes.code(Biome.PEAKS, 2, ScenePalettes.RIGHT)
        val rockLeft = ScenePalettes.code(Biome.PEAKS, 2, ScenePalettes.LEFT)
        assertTrue(brightness(morning[rock]) > brightness(morning[rockLeft]), "the dawn comes from the right")
        assertTrue(brightness(evening[rockLeft]) > brightness(evening[rock]), "the sun sets on the left")
    }

    @Test
    fun `the distance fades into the haze`() {
        val near = ScenePalettes.table(ScenePalettes.Depth.NEAR, TimeOfDay.NOON)
        val far = ScenePalettes.table(ScenePalettes.Depth.FAR, TimeOfDay.NOON)
        val haze = EndlessDay.hazeColor(0.25f)
        val code = ScenePalettes.code(Biome.VOLCANO, 1, ScenePalettes.BODY)
        assertTrue(distance(far[code], haze) < distance(near[code], haze))
    }

    private fun brightness(color: Int): Int = ((color shr 16) and 0xFF) + ((color shr 8) and 0xFF) + (color and 0xFF)

    private fun distance(a: Int, b: Int): Int =
        listOf(16, 8, 0).sumOf { abs(((a shr it) and 0xFF) - ((b shr it) and 0xFF)) }
}
