package at.smiech.cyanbat.resource

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.hud_wave
import at.smiech.cyanbat.resources.pause_title
import at.smiech.cyanbat.resources.power_up_counterweight_description
import at.smiech.cyanbat.resources.power_up_sharpshooter_description
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** How [GameText] fills in its placeholders. */
class GameTextTest {

    private val text = GameText(
        mapOf(
            Res.string.pause_title to "PAUSED",
            Res.string.hud_wave to "Wave: %1\$d/%2\$d",
            Res.string.power_up_sharpshooter_description to "+%1\$d% critical chance",
            Res.string.power_up_counterweight_description to "%2\$d% dealt, -%1\$d taken",
        )
    )

    @Test
    fun `a string with nothing to fill in reads as it is`() {
        assertEquals("PAUSED", text[Res.string.pause_title])
        assertEquals("PAUSED", text.format(Res.string.pause_title, 7))
    }

    @Test
    fun `placeholders are filled by their numbers in whatever order the words put them`() {
        assertEquals("Wave: 3/5", text.format(Res.string.hud_wave, 3, 5))
        assertEquals("10% dealt, -1 taken", text.format(Res.string.power_up_counterweight_description, 1, 10))
    }

    /** As Compose reads its own strings, so a language can put a percent sign wherever it goes. */
    @Test
    fun `a percent sign on its own is a percent sign`() {
        assertEquals("+4% critical chance", text.format(Res.string.power_up_sharpshooter_description, 4))
    }

    @Test
    fun `a string it was not given is a mistake and not a blank`() {
        val empty = GameText(emptyMap())
        assertFailsWith<IllegalStateException> { empty[Res.string.pause_title] }
    }
}
