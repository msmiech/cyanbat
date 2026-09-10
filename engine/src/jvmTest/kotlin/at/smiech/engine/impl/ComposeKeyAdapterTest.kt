package at.smiech.engine.impl

import androidx.compose.ui.input.key.Key
import at.smiech.engine.Direction
import at.smiech.engine.GameButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the desktop key mapping - the one part of the control plumbing no emulator run reaches.
 *
 * Driven through [applyKey] rather than through a Compose `KeyEvent`, because that event is a
 * value class over a type the toolkit fills in from a real window: constructing one by hand
 * compiles and then fails the moment anything reads it. What is left untested is the unpacking of
 * `event.type` and `event.key`, which is four lines and needs a window to exercise honestly.
 */
class ComposeKeyAdapterTest {

    @Test
    fun `WASD steers`() {
        val handler = ControlHandler()

        handler.applyKey(Key.D, pressed = true)
        assertEquals(1f, handler.moveX)
        handler.applyKey(Key.D, pressed = false)
        assertEquals(0f, handler.moveX)

        handler.applyKey(Key.A, pressed = true)
        assertEquals(-1f, handler.moveX)

        handler.applyKey(Key.W, pressed = true)
        assertEquals(-1f, handler.moveY, "W should be up, which is negative y")

        handler.applyKey(Key.W, pressed = false)
        handler.applyKey(Key.S, pressed = true)
        assertEquals(1f, handler.moveY)
    }

    @Test
    fun `the arrow keys steer the same way`() {
        val handler = ControlHandler()

        handler.applyKey(Key.DirectionLeft, pressed = true)
        assertEquals(-1f, handler.moveX)

        handler.applyKey(Key.DirectionUp, pressed = true)
        assertEquals(-1f, handler.moveY)

        handler.applyKey(Key.DirectionLeft, pressed = false)
        handler.applyKey(Key.DirectionRight, pressed = true)
        assertEquals(1f, handler.moveX)

        handler.applyKey(Key.DirectionUp, pressed = false)
        handler.applyKey(Key.DirectionDown, pressed = true)
        assertEquals(1f, handler.moveY)
    }

    @Test
    fun `a letter and its arrow are one direction, not two`() {
        val handler = ControlHandler()
        handler.applyKey(Key.A, pressed = true)
        handler.applyKey(Key.DirectionLeft, pressed = true)

        // They share one flag, so releasing either stops the bat. Worth pinning: the alternative
        // is a bat that keeps flying because the other key of a pair is still notionally held.
        handler.applyKey(Key.A, pressed = false)
        assertEquals(0f, handler.moveX)
    }

    @Test
    fun `escape pauses and Q quits`() {
        val handler = ControlHandler()

        handler.applyKey(Key.Escape, pressed = true)
        assertTrue(handler.consumePress(GameButton.PAUSE))
        assertFalse(handler.consumePress(GameButton.BACK))

        handler.applyKey(Key.Q, pressed = true)
        assertTrue(handler.consumePress(GameButton.BACK))
    }

    @Test
    fun `P also pauses and backspace also quits`() {
        val handler = ControlHandler()

        handler.applyKey(Key.P, pressed = true)
        assertTrue(handler.consumePress(GameButton.PAUSE))

        handler.applyKey(Key.Backspace, pressed = true)
        assertTrue(handler.consumePress(GameButton.BACK))
    }

    @Test
    fun `enter and space confirm`() {
        val handler = ControlHandler()

        handler.applyKey(Key.Enter, pressed = true)
        assertTrue(handler.consumePress(GameButton.CONFIRM))

        // Enter has to come back up first: both keys raise the same button, and a button still
        // held reads a second press as a key repeat and swallows it.
        handler.applyKey(Key.Enter, pressed = false)
        handler.applyKey(Key.Spacebar, pressed = true)
        assertTrue(handler.consumePress(GameButton.CONFIRM))
    }

    @Test
    fun `a key the game does not use is left for the host`() {
        val handler = ControlHandler()
        assertFalse(
            handler.applyKey(Key.F1, pressed = true),
            "an unclaimed key must report false so Compose keeps propagating it"
        )
    }

    @Test
    fun `a claimed key reports true so it stops propagating`() {
        val handler = ControlHandler()
        // The arrow keys are the ones that matter: unclaimed, Compose reads them as focus
        // traversal and they move focus instead of the bat.
        assertTrue(handler.applyKey(Key.DirectionUp, pressed = true))
        assertTrue(handler.applyKey(Key.W, pressed = true))
        assertTrue(handler.applyKey(Key.Escape, pressed = true))
    }

    @Test
    fun `holding escape does not flip pause repeatedly`() {
        val handler = ControlHandler()

        // Hosts repeat the down event while a key is held; pause must not flicker because of it.
        repeat(5) { handler.applyKey(Key.Escape, pressed = true) }

        assertTrue(handler.consumePress(GameButton.PAUSE))
        assertFalse(handler.consumePress(GameButton.PAUSE), "a held key is one press, not five")
    }
}
