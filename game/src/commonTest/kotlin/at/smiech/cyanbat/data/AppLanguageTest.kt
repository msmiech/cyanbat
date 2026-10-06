package at.smiech.cyanbat.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageTest {

    /** A setting stored by a newer build, or mangled, falls back rather than failing to start. */
    @Test
    fun `an unknown stored language reads as following the system`() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromName("GERMAN"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromName("KLINGON"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromName(null))
    }

    /** The system names a language with its region, and the game speaks it in any region. */
    @Test
    fun `a locale picks its language whatever its region`() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTag("de-AT"))
        assertEquals(AppLanguage.POLISH, AppLanguage.fromTag("pl-PL"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en_GB"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("EN"))
    }

    @Test
    fun `a locale the game does not speak follows the system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("ja-JP"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
    }
}
