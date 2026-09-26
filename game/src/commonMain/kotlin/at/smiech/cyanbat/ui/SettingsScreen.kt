package at.smiech.cyanbat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.settings
import at.smiech.cyanbat.resources.settings_display_ambient
import at.smiech.cyanbat.resources.settings_display_ambient_hint
import at.smiech.cyanbat.resources.settings_display_black_bars
import at.smiech.cyanbat.resources.settings_display_black_bars_hint
import at.smiech.cyanbat.resources.settings_display_stretch
import at.smiech.cyanbat.resources.settings_display_stretch_hint
import at.smiech.cyanbat.resources.settings_display_title
import at.smiech.cyanbat.resources.settings_music_title
import at.smiech.cyanbat.resources.settings_sound_title
import at.smiech.engine.DisplayMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val musicEnabled by viewModel.isMusicEnabled.collectAsState()
    val soundEnabled by viewModel.isSoundEnabled.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    SettingsContent(
        musicEnabled = musicEnabled,
        soundEnabled = soundEnabled,
        displayMode = displayMode,
        onMusicEnabledChanged = viewModel::setMusicEnabled,
        onSoundEnabledChanged = viewModel::setSoundEnabled,
        onDisplayModeChanged = viewModel::setDisplayMode,
    )
}

@Composable
private fun SettingsContent(
    musicEnabled: Boolean,
    soundEnabled: Boolean,
    displayMode: DisplayMode,
    onMusicEnabledChanged: (Boolean) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onDisplayModeChanged: (DisplayMode) -> Unit,
) {
    Surface {
        // Scrolls, because a phone held landscape is only about 400dp tall and the display choices
        // alone take most of that.
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                text = stringResource(Res.string.settings),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingRow(
                stringResource(Res.string.settings_music_title),
                musicEnabled,
                onMusicEnabledChanged
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingRow(
                stringResource(Res.string.settings_sound_title),
                soundEnabled,
                onSoundEnabledChanged
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.settings_display_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                for (mode in DisplayMode.entries) {
                    DisplayModeOption(mode, selected = mode == displayMode, onSelected = onDisplayModeChanged)
                }
            }
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

/** One of the display choices: the whole row picks it, not just the radio button. */
@Composable
private fun DisplayModeOption(mode: DisplayMode, selected: Boolean, onSelected: (DisplayMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = { onSelected(mode) }, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = stringResource(mode.label))
            Text(
                text = stringResource(mode.hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val DisplayMode.label: StringResource
    get() = when (this) {
        DisplayMode.STRETCH -> Res.string.settings_display_stretch
        DisplayMode.BLACK_BARS -> Res.string.settings_display_black_bars
        DisplayMode.AMBIENT -> Res.string.settings_display_ambient
    }

private val DisplayMode.hint: StringResource
    get() = when (this) {
        DisplayMode.STRETCH -> Res.string.settings_display_stretch_hint
        DisplayMode.BLACK_BARS -> Res.string.settings_display_black_bars_hint
        DisplayMode.AMBIENT -> Res.string.settings_display_ambient_hint
    }
