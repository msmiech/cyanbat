package at.smiech.cyanbat.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.R

/**
 * Compose-based screen for displaying settings.
 *
 * @author msmiech
 */
@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    MaterialTheme {
        val musicEnabled by settingsViewModel.isMusicEnabled().collectAsState(true)
        SettingsContent(musicEnabled, settingsViewModel::setMusicEnabled)
    }
}

@Composable
private fun SettingsContent(
    musicEnabled: Boolean?,
    onMusicEnabledChanged: ((Boolean) -> Unit)
) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_music_title)
                )
                Switch(
                    checked = musicEnabled == true,
                    onCheckedChange = onMusicEnabledChanged
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_sound_title)
                )
                Switch(checked = true, onCheckedChange = {})
            }
        }
    }
}

@Preview
@Composable
fun SettingsContentPreview() {
    MaterialTheme {
        SettingsContent(true, {})
    }
}
