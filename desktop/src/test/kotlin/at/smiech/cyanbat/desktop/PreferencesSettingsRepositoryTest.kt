package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.data.WindowMode
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** What [PreferencesSettingsRepository] keeps of the window, and what a new player starts in. */
class PreferencesSettingsRepositoryTest {

    /** A node of the test's own, so it never reads or writes the game's real settings. */
    private val node = Preferences.userRoot().node("at/smiech/cyanbat-test-${UUID.randomUUID()}")

    @AfterTest
    fun removeNode() = node.removeNode()

    /** The settings as a start of the game reads them, on a Steam Deck or not ([isSteamDeck]). */
    private fun settings(onSteamDeck: Boolean = false) = PreferencesSettingsRepository(
        node,
        windowModeUnlessSet = if (onSteamDeck) WindowMode.FULLSCREEN else WindowMode.WINDOWED,
    )

    @Test
    fun `a new player starts in a window, or in full screen on a Steam Deck`() {
        assertEquals(WindowMode.WINDOWED, settings(onSteamDeck = false).windowMode.value)
        assertEquals(WindowMode.FULLSCREEN, settings(onSteamDeck = true).windowMode.value)
    }

    @Test
    fun `the mode is kept from one start to the next`() = runBlocking {
        settings().setWindowMode(WindowMode.BORDERLESS)

        assertEquals(WindowMode.BORDERLESS, settings().windowMode.value)
    }

    @Test
    fun `a player on a Steam Deck who chose a window gets one`() = runBlocking {
        settings(onSteamDeck = true).setWindowMode(WindowMode.WINDOWED)

        assertEquals(WindowMode.WINDOWED, settings(onSteamDeck = true).windowMode.value)
    }

    /** What F11 goes back to: the system's full screen, until the player chooses borderless. */
    @Test
    fun `the full screen last chosen is kept through a window`() = runBlocking {
        assertEquals(WindowMode.FULLSCREEN, settings().lastFullScreenMode)

        settings().setWindowMode(WindowMode.BORDERLESS)
        settings().setWindowMode(WindowMode.WINDOWED)

        assertEquals(WindowMode.BORDERLESS, settings().lastFullScreenMode)
    }
}
