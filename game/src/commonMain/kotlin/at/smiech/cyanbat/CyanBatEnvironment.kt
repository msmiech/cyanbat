package at.smiech.cyanbat

import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.GameText
import at.smiech.engine.Haptics

/** Everything the game screens need from their host, injected so the screens stay platform-free. */
class CyanBatEnvironment(
    val assets: GameAssets,
    /**
     * The screens' text in the player's language. Read as they draw, so a host whose language can
     * change mid-run (Android keeps the run through a language change) swaps the new text in here.
     */
    var text: GameText,
    val haptics: Haptics,
    val highscores: HighscoreStore,
    /** Which stages are open; clearing one opens the next. */
    val stageUnlocks: StageUnlockStore,
    /** Leave the game and go back to the menu. */
    val onExitToMenu: () -> Unit,
    /** Consulted each time a track or effect would start, so setting changes apply immediately. */
    val audioSettings: AudioSettings = AudioSettings.AllEnabled,
)
