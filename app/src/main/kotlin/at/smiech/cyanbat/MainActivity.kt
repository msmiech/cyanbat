package at.smiech.cyanbat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.data.DataStoreStageUnlockStore
import at.smiech.cyanbat.data.DataStoreSettingsRepository
import at.smiech.cyanbat.ui.CyanBatMenu
import at.smiech.cyanbat.ui.MenuHost
import at.smiech.cyanbat.ui.rememberMenuBackStack
import at.smiech.engine.Audio
import at.smiech.engine.Music
import at.smiech.engine.impl.AndroidAudio

internal val PREFS_KEY_MUSIC = booleanPreferencesKey("music_enabled")
internal val PREFS_KEY_SOUNDS = booleanPreferencesKey("sounds_enabled")
/** Stored by name, so reordering or adding modes cannot turn one player's choice into another. */
internal val PREFS_KEY_DISPLAY_MODE = stringPreferencesKey("display_mode")
internal val PREFS_KEY_HIGH_SCORE = intPreferencesKey("highscore")
internal val PREFS_KEY_HIGHEST_STAGE = intPreferencesKey("highest_stage_unlocked")
internal val Context.dataStore by preferencesDataStore(name = "cyanbat")

/**
 * Android host for the shared menu. Its job is to supply the platform pieces - DataStore-backed
 * settings, the menu track, and how to start the game - and let [CyanBatMenu] do the rest.
 *
 * @author msmiech, KittysCode
 */
class MainActivity : ComponentActivity() {

    private lateinit var audio: Audio

    /**
     * Created once per activity rather than inside [setContent]. That lambda recomposes whenever
     * the back stack changes, and a track built there prepared a fresh MediaPlayer on every trip
     * into Settings or Credits - none of which the menu ever played.
     */
    private lateinit var menuMusic: Music

    /** Whether [onStop] silenced the menu track, so [onStart] knows to bring it back. */
    private var resumeMusicOnStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        audio = AndroidAudio(this)
        menuMusic = audio.newMusic("menu_theme.mp3")
        val settings = DataStoreSettingsRepository(dataStore)
        val stageUnlocks = DataStoreStageUnlockStore(dataStore)

        setContent {
            // The shared back stack is host-owned so the Android back gesture can drive it;
            // Navigation3 used to provide this before the menu became multiplatform.
            val backStack = rememberMenuBackStack()
            BackHandler(enabled = backStack.canGoBack) { backStack.back() }

            CyanBatMenu(
                backStack = backStack,
                host = MenuHost(
                    settings = settings,
                    menuMusic = menuMusic,
                    stageUnlocks = stageUnlocks,
                    onStartGame = { stageId ->
                        startActivity(
                            Intent(this, CyanBatGameActivity::class.java)
                                .putExtra(CyanBatGameActivity.EXTRA_STAGE_ID, stageId)
                        )
                    },
                    onExit = { finishAffinity() },
                )
            )
        }
    }

    // Nothing else stops the menu track when the app leaves the screen - the menu only silences
    // it on the way into a game - so without these it played on behind the home screen.
    override fun onStart() {
        super.onStart()
        if (resumeMusicOnStart) {
            resumeMusicOnStart = false
            menuMusic.play()
        }
    }

    override fun onStop() {
        super.onStop()
        resumeMusicOnStart = menuMusic.isPlaying
        if (resumeMusicOnStart) menuMusic.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.dispose()
    }
}
