package at.smiech.cyanbat.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {

    @Test
    fun `the system theme follows the system either way`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = false))
    }

    @Test
    fun `a chosen theme holds whatever the system is set to`() {
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = true))
    }

    /** A setting stored by a newer build, or mangled, falls back rather than failing to start. */
    @Test
    fun `an unknown stored theme reads as following the system`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("SEPIA"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
    }
}
