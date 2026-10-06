package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.resource.GameText
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.combo_blazing
import at.smiech.cyanbat.resources.combo_blue_flame
import at.smiech.cyanbat.resources.combo_cold
import at.smiech.cyanbat.resources.combo_hellfire
import at.smiech.cyanbat.resources.combo_hot
import at.smiech.cyanbat.resources.combo_inferno
import at.smiech.cyanbat.resources.combo_scorching
import at.smiech.cyanbat.resources.combo_supernova
import at.smiech.cyanbat.resources.combo_white_hot
import at.smiech.cyanbat.util.COMBO_KILL_FLARE
import at.smiech.cyanbat.util.TICK_INITIAL
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How [ComboMeter] pops, flares and douses the readout. */
class ComboMeterTest {

    private val meter = ComboMeter(Random(3))
    private val g = RectRecordingGraphics()

    /** The ladder's titles in English, which is all of the game's text the meter reads. */
    private val text = GameText(
        mapOf(
            Res.string.combo_cold to "Combo:",
            Res.string.combo_hot to "HOT",
            Res.string.combo_blazing to "BLAZING",
            Res.string.combo_scorching to "SCORCHING",
            Res.string.combo_inferno to "INFERNO",
            Res.string.combo_hellfire to "HELLFIRE",
            Res.string.combo_blue_flame to "BLUE FLAME",
            Res.string.combo_white_hot to "WHITE HOT",
            Res.string.combo_supernova to "SUPERNOVA",
        )
    )

    /** One frame the way the screen runs it: a tick, then a draw. */
    private fun frame(streak: Int, multiplier: Int = 1 + streak / 3) {
        meter.update(TICK_INITIAL, streak, multiplier)
        meter.draw(g, X, BASELINE, text)
    }

    /** Kills three ticks apart, from the streak after [from] up to [to]. */
    private fun killsTo(to: Int, from: Int = 0) {
        for (streak in from + 1..to) repeat(3) { frame(streak) }
    }

    @Test
    fun `cold it draws as one plain line`() {
        frame(streak = 0)
        assertFalse(meter.burning)
        assertEquals(listOf("Combo: x1"), g.strings.distinct())
        assertTrue(g.rects.isEmpty(), "nothing is burning yet")
    }

    @Test
    fun `a step pops the count and flares the fire`() {
        frame(streak = 2)
        assertEquals(0f, meter.popTime)

        frame(streak = 3)

        assertTrue(meter.burning)
        assertTrue(meter.popTime > 0f)
        assertTrue(meter.flare > 0.9f)
    }

    @Test
    fun `a kill between steps fans the fire without a pop`() {
        killsTo(3)
        // Long enough for the step's pop and flare to have settled.
        repeat(60) { frame(streak = 3) }
        assertEquals(0f, meter.popTime)
        assertEquals(0f, meter.flare)

        frame(streak = 4)

        assertEquals(0f, meter.popTime, "a kill that earns no step is no pop")
        assertTrue(meter.flare > 0f && meter.flare <= COMBO_KILL_FLARE)
    }

    @Test
    fun `a hit douses it and the fire gutters out`() {
        killsTo(15)
        assertTrue(meter.fireLit)
        val burningColors = g.rects.map { it.color }.toSet()

        g.rects.clear()
        frame(streak = 0)

        assertFalse(meter.burning)
        assertEquals(0f, meter.popTime)
        assertEquals(0f, meter.flare)
        assertTrue(g.rects.isNotEmpty(), "the fire should still be going out")
        assertTrue(
            g.rects.all { it.color in burningColors },
            "the dying fire should keep the colors it burned in, not turn the cold readout's cyan",
        )
        // The fire's grid is thirty cells tall, and it goes out in as many ticks as it is tall.
        repeat(30) { frame(streak = 0) }
        assertFalse(meter.fireLit)
    }

    @Test
    fun `the fire rises off the tops of the letters rather than covering them`() {
        killsTo(30)
        // Past the last step's pop, which shakes the letters and the fire with them.
        repeat(20) { frame(streak = 30) }
        g.rects.clear()
        repeat(20) { frame(streak = 30) }
        assertTrue(g.rects.isNotEmpty())
        // Nothing within five pixels of the baseline: the lower half of the letters stays clear.
        assertTrue(g.rects.all { it.y + it.height <= BASELINE - 5 })
    }

    private companion object {
        const val X = 5
        const val BASELINE = 66
    }
}
