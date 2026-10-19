package at.smiech.engine.impl

import at.smiech.engine.GameButton
import at.smiech.engine.InputMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How [PadRouter] turns a polled pad into a run's controls and a menu's keys. */
class PadRouterTest {

    private val controls = ControlHandler(InputMethod.KEYBOARD_MOUSE)
    private val keys = mutableListOf<Pair<PadMenuKey, Boolean>>()
    private val router = PadRouter(controls) { key, pressed -> keys += key to pressed }
    private val pad = PadState()

    /** Polls [pad] at [now] in a run or the menu, the first poll on a side settling it in. */
    private fun poll(inGame: Boolean, now: Long = 0L) = router.update(pad, inGame, now)

    @Test
    fun `in a run the bottom face button confirms and the right one goes back`() {
        poll(inGame = true)
        pad.south = true
        poll(inGame = true)
        assertTrue(controls.consumePress(GameButton.CONFIRM))
        pad.south = false
        pad.east = true
        poll(inGame = true)
        assertTrue(controls.consumePress(GameButton.BACK))
        pad.east = false
        pad.start = true
        poll(inGame = true)
        assertTrue(controls.consumePress(GameButton.PAUSE))
    }

    @Test
    fun `in a run the d-pad flies the bat ahead of the stick`() {
        poll(inGame = true)
        pad.stickX = 0.8f
        poll(inGame = true)
        assertTrue(controls.moveX > 0.5f)
        pad.dpadLeft = true
        poll(inGame = true)
        assertEquals(-1f, controls.moveX)
        pad.clear()
        poll(inGame = true)
        assertEquals(0f, controls.moveX)
    }

    @Test
    fun `using the pad makes it the input method by its kind`() {
        pad.method = InputMethod.PLAYSTATION_PAD
        poll(inGame = true)
        assertEquals(InputMethod.KEYBOARD_MOUSE, controls.inputMethod)
        pad.south = true
        poll(inGame = true)
        assertEquals(InputMethod.PLAYSTATION_PAD, controls.inputMethod)
    }

    @Test
    fun `a stick resting a little off center does not take the prompts`() {
        poll(inGame = true)
        pad.stickX = 0.3f
        poll(inGame = true)
        assertEquals(InputMethod.KEYBOARD_MOUSE, controls.inputMethod)
    }

    @Test
    fun `in the menu the face buttons are Enter and Escape`() {
        poll(inGame = false)
        pad.south = true
        poll(inGame = false)
        pad.south = false
        poll(inGame = false)
        pad.east = true
        poll(inGame = false)
        assertEquals(
            listOf(
                PadMenuKey.CONFIRM to true,
                PadMenuKey.CONFIRM to false,
                PadMenuKey.BACK to true,
            ),
            keys,
        )
    }

    @Test
    fun `a direction held in the menu repeats as a key does`() {
        poll(inGame = false, now = 0L)
        pad.dpadDown = true
        poll(inGame = false, now = 0L)
        poll(inGame = false, now = PadRouter.MENU_REPEAT_DELAY_MILLIS - 1)
        poll(inGame = false, now = PadRouter.MENU_REPEAT_DELAY_MILLIS)
        poll(inGame = false, now = PadRouter.MENU_REPEAT_DELAY_MILLIS + 1)
        pad.dpadDown = false
        poll(inGame = false, now = 1_000L)
        assertEquals(
            listOf(
                PadMenuKey.DOWN to true,
                PadMenuKey.DOWN to true,
                PadMenuKey.DOWN to false,
            ),
            keys,
        )
    }

    @Test
    fun `the stick moves the menu's cursor along the axis it leans more`() {
        poll(inGame = false)
        pad.stickX = -0.9f
        pad.stickY = 0.6f
        poll(inGame = false)
        assertEquals(listOf(PadMenuKey.LEFT to true), keys)
    }

    @Test
    fun `a button held as a run starts is not pressed in it`() {
        poll(inGame = false)
        pad.south = true
        poll(inGame = false)
        // The A whose press started the run, still held as it begins.
        poll(inGame = true)
        poll(inGame = true)
        assertFalse(controls.consumePress(GameButton.CONFIRM))
        // Let go in the menu's name rather than left hanging there.
        assertEquals(PadMenuKey.CONFIRM to false, keys.last())
    }

    @Test
    fun `a button held as a run ends is not pressed in the menu`() {
        poll(inGame = true)
        pad.east = true
        poll(inGame = true)
        assertTrue(controls.consumePress(GameButton.BACK))
        poll(inGame = false)
        poll(inGame = false)
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `no pad lets go of everything it held`() {
        poll(inGame = true)
        pad.dpadRight = true
        poll(inGame = true)
        assertEquals(1f, controls.moveX)
        router.update(null, inGame = true, nowMillis = 0L)
        assertEquals(0f, controls.moveX)
    }

    @Test
    fun `a pad is named by its maker or its name`() {
        assertEquals(InputMethod.PLAYSTATION_PAD, InputMethod.padOf("PS5 Controller"))
        assertEquals(InputMethod.PLAYSTATION_PAD, InputMethod.padOf("DualSense Wireless Controller"))
        assertEquals(
            InputMethod.PLAYSTATION_PAD,
            InputMethod.padOf("Wireless Controller", InputMethod.SONY_VENDOR_ID),
        )
        assertEquals(
            InputMethod.PLAYSTATION_PAD,
            InputMethod.padOf("054c-0ce6-Wireless Controller"),
        )
        assertEquals(InputMethod.XBOX_PAD, InputMethod.padOf("Xbox Series X Controller"))
        assertEquals(InputMethod.XBOX_PAD, InputMethod.padOf("8BitDo Pro 2"))
        assertEquals(InputMethod.XBOX_PAD, InputMethod.padOf(null))
    }
}
