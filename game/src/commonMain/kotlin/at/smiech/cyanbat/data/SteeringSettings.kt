package at.smiech.cyanbat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * How a run steers the bat by touch.
 *
 * Separate from [SettingsRepository], as [AudioSettings] is, because the game loop reads it on
 * every tick and cannot suspend on a Flow mid-frame.
 */
interface SteeringSettings {
    val touchSteering: TouchSteering

    /** How far the stick has to be pushed, when [touchSteering] is the stick. */
    val stickSensitivity: StickSensitivity get() = StickSensitivity.DEFAULT

    companion object {
        /** For hosts with no touch screen, and no settings store of their own. */
        val Default: SteeringSettings = object : SteeringSettings {
            override val touchSteering = TouchSteering.DEFAULT
        }
    }
}

/**
 * [SteeringSettings] kept current by collecting a [SettingsRepository], as [ObservedAudioSettings]
 * keeps the sound's. The defaults until the first values arrive, and [TouchSteering.DEFAULT] while
 * the player has not chosen; a run reads it every tick, so it takes up the stored choice a frame or
 * two in.
 *
 * @param scope canceled by the host when the game goes away.
 */
class ObservedSteeringSettings(
    repository: SettingsRepository,
    scope: CoroutineScope,
) : SteeringSettings {

    @Volatile
    override var touchSteering: TouchSteering = TouchSteering.DEFAULT
        private set

    @Volatile
    override var stickSensitivity: StickSensitivity = StickSensitivity.DEFAULT
        private set

    init {
        scope.launch {
            repository.touchSteering.collect { touchSteering = it ?: TouchSteering.DEFAULT }
        }
        scope.launch { repository.stickSensitivity.collect { stickSensitivity = it } }
    }
}
