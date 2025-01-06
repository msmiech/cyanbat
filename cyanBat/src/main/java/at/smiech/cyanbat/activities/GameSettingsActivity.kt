package at.smiech.cyanbat.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import at.smiech.cyanbat.screen.SettingsScreen
import at.smiech.cyanbat.screen.SettingsViewModel

/**
 * GameOptionsActivity which sets the GameOptionsScreen as the content.
 *
 * @author msmiech
 */
class GameSettingsActivity : ComponentActivity() {
    private val viewModel by viewModels<SettingsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(viewModel)
        }
    }
}
