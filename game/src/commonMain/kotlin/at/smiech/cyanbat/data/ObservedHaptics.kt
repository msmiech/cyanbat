package at.smiech.cyanbat.data

import at.smiech.engine.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * [Haptics] that pass a vibration on to [haptics] only while the player has vibration switched on,
 * kept current by collecting a [SettingsRepository] as [ObservedAudioSettings] does for sound.
 *
 * The switch lives here, in what the host hands the game, so the screens go on calling
 * [vibrate] and need not know a setting exists.
 *
 * On until the first value arrives, matching the repository's own default.
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
