package at.smiech.cyanbat.ui

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
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.settings
import at.smiech.cyanbat.resources.settings_music_title
import at.smiech.cyanbat.resources.settings_sound_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val musicEnabled by viewModel.isMusicEnabled.collectAsState()
    val soundEnabled by viewModel.isSoundEnabled.collectAsState()
    SettingsContent(
        musicEnabled = musicEnabled,
        soundEnabled = soundEnabled,
        onMusicEnabledChanged = viewModel::setMusicEnabled,
        onSoundEnabledChanged = viewModel::setSoundEnabled,
    )
}

@Composable
private fun SettingsContent(
    musicEnabled: Boolean,
    soundEnabled: Boolean,
    onMusicEnabledChanged: (Boolean) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
) {
    Surface {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(Res.string.settings),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingRow(stringResource(Res.string.settings_music_title), musicEnabled, onMusicEnabledChanged)
            Spacer(modifier = Modifier.height(16.dp))
            SettingRow(stringResource(Res.string.settings_sound_title), soundEnabled, onSoundEnabledChanged)
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
