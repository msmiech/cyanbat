package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * [SettingsRepository] on the JDK preferences API - the registry on Windows, a dotfile
 * elsewhere. Avoids putting DataStore and okio on the desktop classpath for two booleans.
 *
 * The flows are in-memory mirrors: this process is the only writer, so they cannot go stale.
 */
class PreferencesSettingsRepository : SettingsRepository {
    private val prefs: Preferences = Preferences.userRoot().node("at/smiech/cyanbat")

    private val music = MutableStateFlow(prefs.getBoolean(KEY_MUSIC, true))
    private val sound = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))

    override val isMusicEnabled: Flow<Boolean> = music.asStateFlow()
    override val isSoundEnabled: Flow<Boolean> = sound.asStateFlow()

    override suspend fun setMusicEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_MUSIC, enabled)
        music.value = enabled
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_SOUND, enabled)
        sound.value = enabled
    }

    private companion object {
        const val KEY_MUSIC = "music_enabled"
        const val KEY_SOUND = "sound_enabled"
    }
}
