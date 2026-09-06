package at.smiech.cyanbat.data

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Whether the game may play music and sound effects.
 *
 * Separate from [SettingsRepository] because the game loop needs a plain synchronous read at the
 * moment a sound would start - it cannot suspend on a Flow mid-frame.
 */
interface AudioSettings {
    val musicEnabled: Boolean
    val soundsEnabled: Boolean

    companion object {
        /** For hosts with no settings store of their own. */
        val AllEnabled: AudioSettings = object : AudioSettings {
            override val musicEnabled = true
            override val soundsEnabled = true
        }
    }
}

/**
 * [AudioSettings] kept current by collecting a [SettingsRepository].
 *
 * Collecting rather than reading once keeps the settings read off the caller's thread - on
 * Android the backing store is DataStore, and a blocking first read at activity start would be
 * disk I/O on the main thread.
 *
 * Both fields default to enabled until the first value arrives, matching the repository's own
 * defaults, so a run started before the store responds is not silently muted.
 *
 * @param scope cancelled by the host when the game goes away.
 */
class ObservedAudioSettings(
    repository: SettingsRepository,
    scope: CoroutineScope,
) : AudioSettings {

    @Volatile
    override var musicEnabled: Boolean = true
        private set

    @Volatile
    override var soundsEnabled: Boolean = true
        private set

    init {
        scope.launch { repository.isMusicEnabled.collect { musicEnabled = it } }
        scope.launch { repository.isSoundEnabled.collect { soundsEnabled = it } }
    }
}
