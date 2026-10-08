package at.smiech.cyanbat.desktop

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [FullscreenKeys], driven through its press and release rather than through Compose key events,
 * which cannot be built outside a window; see `ComposeKeyAdapterTest`.
 */
class FullscreenKeysTest {

    private var toggles = 0

    private fun keys(isMac: Boolean = false) = FullscreenKeys(isMac) { toggles++ }

    @Test
    fun `F11 toggles, and its release goes no further`() {
        val keys = keys()

        assertTrue(keys.onPress(Key.F11))
        assertTrue(keys.onRelease(Key.F11))

        assertEquals(1, toggles)
    }

    /** A held key repeats its press; it is still one press, and toggles once. */
    @Test
    fun `F11 held down toggles once`() {
        val keys = keys()

        repeat(5) { assertTrue(keys.onPress(Key.F11)) }
        keys.onRelease(Key.F11)
        keys.onPress(Key.F11)

        assertEquals(2, toggles)
    }

    @Test
    fun `Alt and Enter toggle, and Enter alone is left to the menu and the game`() {
        val keys = keys()

        assertFalse(keys.onPress(Key.Enter))
        assertFalse(keys.onRelease(Key.Enter))
        assertEquals(0, toggles)

        assertTrue(keys.onPress(Key.Enter, alt = true))
        // Alt let go of first: Enter's release is still swallowed with its press.
        assertTrue(keys.onRelease(Key.Enter))
        assertEquals(1, toggles)
    }

    @Test
    fun `Ctrl, Cmd and F toggle on a Mac, and only there`() {
        assertTrue(keys(isMac = true).onPress(Key.F, ctrl = true, meta = true))
        assertEquals(1, toggles)

        assertFalse(keys(isMac = false).onPress(Key.F, ctrl = true, meta = true))
        assertFalse(keys(isMac = true).onPress(Key.F))
        assertEquals(1, toggles)
    }

    @Test
    fun `the game's and the menu's own keys pass through`() {
        val keys = keys()

        for (key in listOf(Key.Escape, Key.Q, Key.Spacebar, Key.DirectionUp, Key.W, Key.F)) {
            assertFalse(keys.onPress(key), "$key was taken")
            assertFalse(keys.onRelease(key), "$key's release was taken")
        }
        assertEquals(0, toggles)
    }

    /** The window lost focus with F11 down, so its release went elsewhere. */
    @Test
    fun `a key whose release went elsewhere toggles on its next press`() {
        val keys = keys()

        keys.onPress(Key.F11)
        keys.releaseAll()
        keys.onPress(Key.F11)

        assertEquals(2, toggles)
    }
}
