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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.data.ThemeMode
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
import at.smiech.cyanbat.resources.settings_theme_dark
import at.smiech.cyanbat.resources.settings_theme_light
import at.smiech.cyanbat.resources.settings_theme_system
import at.smiech.cyanbat.resources.settings_theme_title
import at.smiech.cyanbat.resources.settings_vibration_title
import at.smiech.engine.DisplayMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val musicEnabled by viewModel.isMusicEnabled.collectAsState()
    val soundEnabled by viewModel.isSoundEnabled.collectAsState()
    val vibrationEnabled by viewModel.isVibrationEnabled.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    SettingsContent(
        musicEnabled = musicEnabled,
        soundEnabled = soundEnabled,
        // Null hides the switch, on a platform with nothing to vibrate.
        vibrationEnabled = vibrationEnabled.takeIf { canVibrate },
        themeMode = themeMode,
        displayMode = displayMode,
        onMusicEnabledChanged = viewModel::setMusicEnabled,
        onSoundEnabledChanged = viewModel::setSoundEnabled,
        onVibrationEnabledChanged = viewModel::setVibrationEnabled,
        onThemeModeChanged = viewModel::setThemeMode,
        onDisplayModeChanged = viewModel::setDisplayMode,
    )
}

/**
 * How far the rows reach past the text on either side, for the cursor's ring and a touch's ripple
 * to have room around what they are on. The column is inset by that much less, so the text stays
 * in line with the headings.
 */
private val ROW_INSET = 8.dp

private val ROW_SHAPE = RoundedCornerShape(8.dp)

@Composable
private fun SettingsContent(
    musicEnabled: Boolean,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean?,
    themeMode: ThemeMode,
    displayMode: DisplayMode,
    onMusicEnabledChanged: (Boolean) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onVibrationEnabledChanged: (Boolean) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDisplayModeChanged: (DisplayMode) -> Unit,
) {
    val music = remember { FocusRequester() }
    HomeCursor(music)
    Surface {
        // Scrolls, because a phone held landscape is only about 400dp tall and the display choices
        // alone take most of that.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp - ROW_INSET, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(Res.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = ROW_INSET),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingRow(
                stringResource(Res.string.settings_music_title),
                musicEnabled,
                onMusicEnabledChanged,
                Modifier.focusRequester(music),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingRow(
                stringResource(Res.string.settings_sound_title),
                soundEnabled,
                onSoundEnabledChanged
            )
            if (vibrationEnabled != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingRow(
                    stringResource(Res.string.settings_vibration_title),
                    vibrationEnabled,
                    onVibrationEnabledChanged
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ThemeRow(themeMode, onThemeModeChanged)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.settings_display_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = ROW_INSET),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                for (mode in DisplayMode.entries) {
                    DisplayModeOption(
                        mode,
                        selected = mode == displayMode,
                        onSelected = onDisplayModeChanged
                    )
                }
            }
        }
    }
}

/**
 * A setting that is on or off. The whole row is the switch, so a keyboard's or a pad's cursor lands
 * on the row and Enter flips it - and a finger can hit the label as well as the switch.
 */
@Composable
private fun SettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cursor = rememberCursorMark()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(cursor.modifier)
            .clip(ROW_SHAPE)
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = ripple(),
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .cursorRing(cursor.border(), ROW_SHAPE)
            .padding(horizontal = ROW_INSET, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * The menu's theme, as one row of three segments beside its label, in line with the switches: three
 * short words need no hints, and radio rows like the display's would push those below the fold on a
 * landscape phone. Each segment takes the cursor on its own, and the arrows walk along them.
 */
@Composable
private fun ThemeRow(themeMode: ThemeMode, onThemeModeChanged: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_INSET),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stringResource(Res.string.settings_theme_title))
        SingleChoiceSegmentedButtonRow {
            ThemeMode.entries.forEachIndexed { index, mode ->
                val cursor = rememberCursorMark()
                SegmentedButton(
                    selected = mode == themeMode,
                    onClick = { onThemeModeChanged(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    modifier = cursor.modifier,
                    // The cursor's ring in place of the segment's outline, which is the Material
                    // default the rest of the time.
                    border = cursor.border()
                        ?: SegmentedButtonDefaults.borderStroke(MaterialTheme.colorScheme.outline),
                ) {
                    Text(stringResource(mode.label))
                }
            }
        }
    }
}

/** One of the display choices: the whole row picks it, not just the radio button. */
@Composable
private fun DisplayModeOption(
    mode: DisplayMode,
    selected: Boolean,
    onSelected: (DisplayMode) -> Unit
) {
    val cursor = rememberCursorMark()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(cursor.modifier)
            .clip(ROW_SHAPE)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = { onSelected(mode) },
            )
            .cursorRing(cursor.border(), ROW_SHAPE)
            .padding(horizontal = ROW_INSET, vertical = 6.dp),
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

private val ThemeMode.label: StringResource
    get() = when (this) {
        ThemeMode.SYSTEM -> Res.string.settings_theme_system
        ThemeMode.DARK -> Res.string.settings_theme_dark
        ThemeMode.LIGHT -> Res.string.settings_theme_light
    }

private val DisplayMode.hint: StringResource
    get() = when (this) {
        DisplayMode.STRETCH -> Res.string.settings_display_stretch_hint
        DisplayMode.BLACK_BARS -> Res.string.settings_display_black_bars_hint
        DisplayMode.AMBIENT -> Res.string.settings_display_ambient_hint
    }
