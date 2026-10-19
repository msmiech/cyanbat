package at.smiech.cyanbat

import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.data.SteeringSettings
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
    /** What the player has achieved, which a run adds to, each with a picture of the moment. */
    val achievements: AchievementStore,
    /** Leave the game and go back to the menu. */
    val onExitToMenu: () -> Unit,
    /** Consulted each time a track or effect would start, so setting changes apply immediately. */
    val audioSettings: AudioSettings = AudioSettings.AllEnabled,
    /** Whether a finger drags the bat or works a stick on the screen; read every tick. */
    val steeringSettings: SteeringSettings = SteeringSettings.Default,
    /**
     * Whether the run draws a pause button, and the pause screen a Quit button: for a platform
     * with no Back for the player to pause and quit with, and no keys they are sure to have (iOS).
     */
    val pauseButton: Boolean = false,
)
