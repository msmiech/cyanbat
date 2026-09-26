package at.smiech.cyanbat.activity

import android.content.Intent
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.MainActivity
import at.smiech.cyanbat.data.DataStoreHighscoreStore
import at.smiech.cyanbat.data.DataStoreStageUnlockStore
import at.smiech.cyanbat.data.DataStoreSettingsRepository
import at.smiech.cyanbat.data.ObservedAudioSettings
import at.smiech.cyanbat.dataStore
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.engine.DisplayMode
import at.smiech.engine.Screen
import at.smiech.engine.impl.AndroidGameActivity
import at.smiech.engine.impl.AndroidHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

/**
 * Android host for the game. Its whole job is to build a [CyanBatEnvironment] out of platform
 * pieces and hand it to the shared [GameScreen]; the desktop entry point does the same with its
 * own pieces.
 */
class CyanBatGameActivity : AndroidGameActivity() {
    override val startScreen: Screen
        get() = GameScreen(this, buildEnvironment(), intent.getIntExtra(EXTRA_STAGE_ID, 1))

    /** Feeds the live audio settings; canceled with the activity. */
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Lazily, because DataStore needs the activity's context, which a field initializer lacks. */
    private val settings by lazy { DataStoreSettingsRepository(dataStore) }

    override val displayModes: Flow<DisplayMode> get() = settings.displayMode

    override val frameBufferWidth: Int get() = 480
    override val frameBufferHeight: Int get() = 320

    private fun buildEnvironment(): CyanBatEnvironment = CyanBatEnvironment(
        assets = loadAssets(),
        haptics = AndroidHaptics(this),
        highscores = DataStoreHighscoreStore(dataStore),
        stageUnlocks = DataStoreStageUnlockStore(dataStore),
        onExitToMenu = {
            // CLEAR_TOP replaces the menu this game was started from instead of stacking a second
            // one on top of it. Without it every run left another menu behind, each one more press
            // of Back between the player and the home screen.
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        },
        audioSettings = ObservedAudioSettings(settings, activityScope),
    )

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun loadAssets(): GameAssets = GameAssets.load(
        graphics ?: error("Graphics not initialized"),
        audio ?: error("Audio not initialized"),
    )

    companion object {
        /** Which stage the run starts on, 1-based; see [GameScreen]. Stage 1 when absent. */
        const val EXTRA_STAGE_ID = "at.smiech.cyanbat.STAGE_ID"
    }
}
