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
import androidx.datastore.preferences.preferencesDataStore
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.data.DataStoreSettingsRepository
import at.smiech.cyanbat.ui.CyanBatMenu
import at.smiech.cyanbat.ui.MenuHost
import at.smiech.cyanbat.ui.rememberMenuBackStack
import at.smiech.engine.Audio
import at.smiech.engine.impl.AndroidAudio

internal val PREFS_KEY_MUSIC = booleanPreferencesKey("music_enabled")
internal val PREFS_KEY_SOUNDS = booleanPreferencesKey("sounds_enabled")
internal val PREFS_KEY_HIGH_SCORE = intPreferencesKey("highscore")
internal val Context.dataStore by preferencesDataStore(name = "cyanbat")

/**
 * Android host for the shared menu. Its job is to supply the platform pieces - DataStore-backed
 * settings, the menu track, and how to start the game - and let [CyanBatMenu] do the rest.
 *
 * @author msmiech, KittysCode
 */
class MainActivity : ComponentActivity() {

    private lateinit var audio: Audio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        audio = AndroidAudio(this)

        setContent {
            // The shared back stack is host-owned so the Android back gesture can drive it;
            // Navigation3 used to provide this before the menu became multiplatform.
            val backStack = rememberMenuBackStack()
            BackHandler(enabled = backStack.canGoBack) { backStack.back() }

            CyanBatMenu(
                backStack = backStack,
                host = MenuHost(
                    settings = DataStoreSettingsRepository(dataStore),
                    menuMusic = audio.newMusic("menu_theme.mp3"),
                    onStartGame = {
                        startActivity(Intent(this, CyanBatGameActivity::class.java))
                    },
                    onExit = { finishAffinity() },
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.dispose()
    }
}
