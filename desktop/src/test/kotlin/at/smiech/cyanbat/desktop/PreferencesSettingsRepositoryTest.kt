package at.smiech.cyanbat.desktop

import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What [PreferencesSettingsRepository] keeps of full screen, and what a new player starts in. */
class PreferencesSettingsRepositoryTest {

    /** A node of the test's own, so it never reads or writes the game's real settings. */
    private val node = Preferences.userRoot().node("at/smiech/cyanbat-test-${UUID.randomUUID()}")

    @AfterTest
    fun removeNode() = node.removeNode()

    /** The settings as a start of the game reads them, on a Steam Deck or not ([isSteamDeck]). */
    private fun settings(onSteamDeck: Boolean) =
        PreferencesSettingsRepository(node, fullscreenUnlessSet = onSteamDeck)

    @Test
    fun `a new player starts in a window, or in full screen on a Steam Deck`() {
        assertFalse(settings(onSteamDeck = false).isFullscreen.value)
        assertTrue(settings(onSteamDeck = true).isFullscreen.value)
    }

    @Test
    fun `full screen is kept from one start to the next`() = runBlocking {
        settings(onSteamDeck = false).setFullscreen(true)

        assertTrue(settings(onSteamDeck = false).isFullscreen.value)
    }

    @Test
    fun `a player on a Steam Deck who chose a window gets one`() = runBlocking {
        settings(onSteamDeck = true).setFullscreen(false)

        assertFalse(settings(onSteamDeck = true).isFullscreen.value)
    }
}
