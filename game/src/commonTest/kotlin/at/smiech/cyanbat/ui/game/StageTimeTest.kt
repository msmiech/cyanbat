package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.service.StageProgression
import kotlin.test.Test
import kotlin.test.assertEquals

class StageTimeTest {

    @Test
    fun `a stage opens at zero`() {
        assertEquals("00:00", formatStageTime(0f))
    }

    @Test
    fun `a second only counts once it is whole`() {
        assertEquals("00:00", formatStageTime(0.99f))
        assertEquals("00:59", formatStageTime(59.99f))
    }

    @Test
    fun `minutes and seconds are both padded to two digits`() {
        assertEquals("01:05", formatStageTime(65f))
        assertEquals("10:00", formatStageTime(600f))
    }

    @Test
    fun `the minute turns over on the same tick as the wave`() {
        val progression = StageProgression()
        for (seconds in floatArrayOf(59.98f, 60f, 60.02f, 179.99f, 180f)) {
            val minutes = formatStageTime(seconds).substringBefore(':').toInt()
            assertEquals(progression.waveIndexAt(seconds), minutes, "at ${seconds}s")
        }
    }
}
