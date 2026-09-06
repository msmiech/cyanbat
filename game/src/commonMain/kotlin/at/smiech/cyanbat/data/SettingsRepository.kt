package at.smiech.cyanbat.data

import kotlinx.coroutines.flow.Flow

/**
 * User settings, as the shared UI sees them.
 *
 * An interface rather than a concrete DataStore wrapper so desktop can back it with the JDK
 * preferences API instead of pulling DataStore and okio onto the desktop classpath.
 */
interface SettingsRepository {
    val isMusicEnabled: Flow<Boolean>
    val isSoundEnabled: Flow<Boolean>

    suspend fun setMusicEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
}
