package at.smiech.cyanbat.activity

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.MainActivity
import at.smiech.cyanbat.data.AppLocale
import at.smiech.cyanbat.data.DataStoreHighscoreStore
import at.smiech.cyanbat.data.DataStoreSettingsRepository
import at.smiech.cyanbat.data.DataStoreStageUnlockStore
import at.smiech.cyanbat.data.ObservedAudioSettings
import at.smiech.cyanbat.data.ObservedHaptics
import at.smiech.cyanbat.dataStore
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.GameText
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.engine.DisplayMode
import at.smiech.engine.Screen
import at.smiech.engine.impl.AndroidGameActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * Android host for the game. Its whole job is to build a [CyanBatEnvironment] out of platform
 * pieces and hand it to the shared [GameScreen]; the desktop entry point does the same with its
 * own pieces.
 */
class CyanBatGameActivity : AndroidGameActivity() {
    override val startScreen: Screen
        get() = GameScreen(this, environment, intent.getIntExtra(EXTRA_STAGE_ID, 1))

    /** Lazily, for the same reason as [settings]; and kept, to swap a new language's text into. */
    private val environment by lazy { buildEnvironment() }

    /** The language the run's text was last read in; see [onConfigurationChanged]. */
    private var textLocale: Locale? = null

    /** Feeds the live audio and vibration settings; canceled with the activity. */
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Lazily, because DataStore needs the activity's context, which a field initializer lacks. */
    private val settings by lazy { DataStoreSettingsRepository(dataStore) }

    override val displayModes: Flow<DisplayMode> get() = settings.displayMode

    override val frameBufferWidth: Int get() = FRAME_BUFFER_WIDTH
    override val frameBufferHeight: Int get() = FRAME_BUFFER_HEIGHT

    private fun buildEnvironment(): CyanBatEnvironment = CyanBatEnvironment(
        assets = loadAssets(),
        text = loadText(),
        haptics = ObservedHaptics(haptics, settings, activityScope),
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

    /**
     * The run takes a change of language in place, as the manifest says, so its text has to be read
     * again in the new one: the player who picked another language for the game has to see it in
     * the run they come back to. Any other change only puts the game's language back on.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocale.reapply()
        if (Locale.getDefault() != textLocale) environment.text = loadText()
    }

    /**
     * The run's text in the game's language. Blocking, as Compose's own string lookups are on
     * Android: there is no run to show without it, and it is one small file, read once a language.
     */
    private fun loadText(): GameText {
        textLocale = Locale.getDefault()
        return runBlocking { GameText.load() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ahead of the run, which reads its text as the activity creates it.
        AppLocale.start(this)
        super.onCreate(savedInstanceState)
    }

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
