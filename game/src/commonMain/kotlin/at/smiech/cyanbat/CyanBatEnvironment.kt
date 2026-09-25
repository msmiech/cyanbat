package at.smiech.cyanbat

import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.engine.Haptics

/**
 * Everything the game screens need from their host, injected rather than reached for.
 *
 * This replaces a set of statics on the Android game activity, which tied the screens to one
 * platform and to one live Activity instance.
 */
class CyanBatEnvironment(
    val assets: GameAssets,
    val haptics: Haptics,
    val highscores: HighscoreStore,
    /** Which stages are open; clearing one opens the next. */
    val stageUnlocks: StageUnlockStore,
    /** Leave the game and go back to the menu. */
    val onExitToMenu: () -> Unit,
    /** Consulted each time a track or effect would start, so the settings actually apply. */
    val audioSettings: AudioSettings = AudioSettings.AllEnabled,
)
