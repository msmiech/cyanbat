package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.HighscoreStore
import java.util.prefs.Preferences

/**
 * [HighscoreStore] on top of the JDK preferences API, which resolves to the registry on Windows
 * and a dotfile on macOS/Linux - no path handling or directory creation of our own.
 *
 * Writes are synchronous but sub-millisecond, so unlike the Android store this needs no scope of
 * its own to outlive the screen being disposed.
 */
class PreferencesHighscoreStore : HighscoreStore {
    private val prefs: Preferences = Preferences.userRoot().node("at/smiech/cyanbat")

    override suspend fun read(): Int = prefs.getInt(KEY, 0)

    override fun saveAsync(value: Int) {
        prefs.putInt(KEY, value)
    }

    private companion object {
        const val KEY = "highscore"
    }
}
