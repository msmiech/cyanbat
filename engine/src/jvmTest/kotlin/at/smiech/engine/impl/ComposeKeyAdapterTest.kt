package at.smiech.engine.impl

import androidx.compose.ui.input.key.KeyEvent
import at.smiech.engine.GameButton
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the desktop key mapping, which is the one part of the control plumbing no test on the
 * emulator can reach and no headless run can click at: Compose delivers these to a real window.
 *
 * Compose Desktop's `KeyEvent` is a thin wrapper over AWT's, so a synthetic AWT event drives the
 * adapter exactly as a keypress would.
 */
class ComposeKeyAdapterTest {

    /** Lightweight and peerless, so it needs no display to be the event's source. */
    private val source = JPanel()

    private fun press(keyCode: Int) = key(AwtKeyEvent.KEY_PRESSED, keyCode)
    private fun release(keyCode: Int) = key(AwtKeyEvent.KEY_RELEASED, keyCode)

    private fun key(id: Int, keyCode: Int) = KeyEvent(
        AwtKeyEvent(source, id, 0L, 0, keyCode, AwtKeyEvent.CHAR_UNDEFINED)
    )

    @Test
    fun `WASD steers`() {
        val handler = ControlHandler()

        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_D))
        assertEquals(1f, handler.moveX)
        handler.onComposeKeyEvent(release(AwtKeyEvent.VK_D))
        assertEquals(0f, handler.moveX)

        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_A))
        assertEquals(-1f, handler.moveX)

        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_W))
        assertEquals(-1f, handler.moveY, "W should be up, which is negative y")

        handler.onComposeKeyEvent(release(AwtKeyEvent.VK_W))
        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_S))
        assertEquals(1f, handler.moveY)
    }

    @Test
    fun `the arrow keys steer the same way`() {
        val handler = ControlHandler()

        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_LEFT))
        assertEquals(-1f, handler.moveX)

        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_UP))
        assertEquals(-1f, handler.moveY)

        handler.onComposeKeyEvent(release(AwtKeyEvent.VK_LEFT))
        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_RIGHT))
        assertEquals(1f, handler.moveX)

        handler.onComposeKeyEvent(release(AwtKeyEvent.VK_UP))
        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_DOWN))
        assertEquals(1f, handler.moveY)
    }

    @Test
    fun `WASD and the arrows are the same keys as far as the game is concerned`() {
        val handler = ControlHandler()
        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_A))
        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_LEFT))

        // Releasing one of a pair must not cancel the other's direction.
        handler.onComposeKeyEvent(release(AwtKeyEvent.VK_A))
        assertEquals(0f, handler.moveX, "they share one flag, so either release stops the bat")
    }

    @Test
    fun `escape pauses and Q quits`() {
        val handler = ControlHandler()

        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_ESCAPE))
        assertTrue(handler.consumePress(GameButton.PAUSE))
        assertFalse(handler.consumePress(GameButton.BACK))

        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_Q))
        assertTrue(handler.consumePress(GameButton.BACK))
    }

    @Test
    fun `enter and space confirm`() {
        val handler = ControlHandler()

        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_ENTER))
        assertTrue(handler.consumePress(GameButton.CONFIRM))

        handler.onComposeKeyEvent(press(AwtKeyEvent.VK_SPACE))
        assertTrue(handler.consumePress(GameButton.CONFIRM))
    }

    @Test
    fun `a key the game does not use is left for the host`() {
        val handler = ControlHandler()
        assertFalse(
            handler.onComposeKeyEvent(press(AwtKeyEvent.VK_F1)),
            "an unclaimed key must report false so Compose keeps propagating it"
        )
    }

    @Test
    fun `a claimed key reports true so it stops propagating`() {
        val handler = ControlHandler()
        assertTrue(handler.onComposeKeyEvent(press(AwtKeyEvent.VK_W)))
        assertTrue(handler.onComposeKeyEvent(press(AwtKeyEvent.VK_ESCAPE)))
    }

    @Test
    fun `holding escape does not flip pause repeatedly`() {
        val handler = ControlHandler()

        // AWT repeats KEY_PRESSED while a key is held, exactly as Android repeats ACTION_DOWN.
        repeat(5) { handler.onComposeKeyEvent(press(AwtKeyEvent.VK_ESCAPE)) }

        assertTrue(handler.consumePress(GameButton.PAUSE))
        assertFalse(handler.consumePress(GameButton.PAUSE), "a held key is one press, not five")
    }
}
