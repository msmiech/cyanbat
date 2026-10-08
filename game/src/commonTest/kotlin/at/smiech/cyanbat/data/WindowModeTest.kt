package at.smiech.cyanbat.data

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowModeTest {

    /** A setting stored by a newer build, or mangled, falls back rather than failing to start. */
    @Test
    fun `an unknown stored mode reads as a window`() {
        assertEquals(WindowMode.BORDERLESS, WindowMode.fromName("BORDERLESS"))
        assertEquals(WindowMode.WINDOWED, WindowMode.fromName("EXCLUSIVE"))
        assertEquals(WindowMode.WINDOWED, WindowMode.fromName(null))
    }
}
