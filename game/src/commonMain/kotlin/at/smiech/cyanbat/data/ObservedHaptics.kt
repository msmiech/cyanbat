package at.smiech.cyanbat.data

import at.smiech.engine.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * [Haptics] that pass vibrations on to [haptics] only while the player has vibration on, kept
 * current by collecting a [SettingsRepository] as [ObservedAudioSettings] does for sound. The
 * screens just call [vibrate] and need not know the setting exists.
 *
 * On until the first value arrives, matching the repository's default.
 *
 * @param scope canceled by the host when the game goes away.
 */
class ObservedHaptics(
    private val haptics: Haptics,
    repository: SettingsRepository,
    scope: CoroutineScope,
) : Haptics {

    @Volatile
    private var enabled: Boolean = true

    init {
        scope.launch { repository.isVibrationEnabled.collect { enabled = it } }
    }

    override fun vibrate(durationMillis: Long) {
        if (enabled) haptics.vibrate(durationMillis)
    }
}
