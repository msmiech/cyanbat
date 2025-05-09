package at.smiech.cyanbat

import android.content.Context
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.compose.rememberNavController
import at.smiech.cyanbat.ui.MainNavGraph

internal val PREFS_KEY_MUSIC = booleanPreferencesKey("music_enabled")
internal val PREFS_KEY_SOUNDS = booleanPreferencesKey("sounds_enabled")
internal val PREFS_KEY_HIGH_SCORE = intPreferencesKey("highscore")
internal val Context.dataStore by preferencesDataStore(name = "cyanbat")

/**
 * MainActivity represents the menu and is used to navigate to other activities.
 * It contains buttons for the navigation.
 *
 * @author msmiech, KittysCode
 */
class MainActivity : ComponentActivity() {
    /**
     * Sets the contentView and initialises buttons.
     *
     * @param savedInstanceState State of activity - not considered at the moment
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContent {
            MaterialTheme {
                MainNavGraph(navController = rememberNavController())
            }

        }
    }
}
