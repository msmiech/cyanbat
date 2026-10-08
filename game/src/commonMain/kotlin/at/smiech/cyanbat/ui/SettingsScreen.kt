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
import at.smiech.cyanbat.data.AppLanguage
import at.smiech.cyanbat.data.ThemeMode
import at.smiech.cyanbat.data.WindowMode
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.language_english
import at.smiech.cyanbat.resources.language_german
import at.smiech.cyanbat.resources.language_polish
import at.smiech.cyanbat.resources.settings
import at.smiech.cyanbat.resources.settings_display_ambient
import at.smiech.cyanbat.resources.settings_display_ambient_hint
import at.smiech.cyanbat.resources.settings_display_black_bars
import at.smiech.cyanbat.resources.settings_display_black_bars_hint
import at.smiech.cyanbat.resources.settings_display_stretch
import at.smiech.cyanbat.resources.settings_display_stretch_hint
import at.smiech.cyanbat.resources.settings_display_title
import at.smiech.cyanbat.resources.settings_language_system
import at.smiech.cyanbat.resources.settings_language_title
import at.smiech.cyanbat.resources.settings_music_title
import at.smiech.cyanbat.resources.settings_sound_title
import at.smiech.cyanbat.resources.settings_theme_dark
import at.smiech.cyanbat.resources.settings_theme_light
import at.smiech.cyanbat.resources.settings_theme_system
import at.smiech.cyanbat.resources.settings_theme_title
import at.smiech.cyanbat.resources.settings_vibration_title
import at.smiech.cyanbat.resources.settings_window_mode_borderless
import at.smiech.cyanbat.resources.settings_window_mode_fullscreen
import at.smiech.cyanbat.resources.settings_window_mode_title
import at.smiech.cyanbat.resources.settings_window_mode_windowed
import at.smiech.engine.DisplayMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val musicEnabled by viewModel.isMusicEnabled.collectAsState()
    val soundEnabled by viewModel.isSoundEnabled.collectAsState()
    val vibrationEnabled by viewModel.isVibrationEnabled.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val windowMode by viewModel.windowMode.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    SettingsContent(
        musicEnabled = musicEnabled,
        soundEnabled = soundEnabled,
        // Null hides the switch, on a platform with nothing to vibrate.
        vibrationEnabled = vibrationEnabled.takeIf { canVibrate },
        themeMode = themeMode,
        language = language,
        displayMode = displayMode,
        windowMode = windowMode,
        onMusicEnabledChanged = viewModel::setMusicEnabled,
        onSoundEnabledChanged = viewModel::setSoundEnabled,
        onVibrationEnabledChanged = viewModel::setVibrationEnabled,
        onThemeModeChanged = viewModel::setThemeMode,
        onLanguageChanged = viewModel::setLanguage,
        onDisplayModeChanged = viewModel::setDisplayMode,
        onWindowModeChanged = viewModel::setWindowMode,
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
    language: AppLanguage,
    displayMode: DisplayMode,
    windowMode: WindowMode,
    onMusicEnabledChanged: (Boolean) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onVibrationEnabledChanged: (Boolean) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit,
    onDisplayModeChanged: (DisplayMode) -> Unit,
    onWindowModeChanged: (WindowMode) -> Unit,
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
            ChoiceRow(
                stringResource(Res.string.settings_theme_title),
                ThemeMode.entries,
                themeMode,
                onThemeModeChanged,
            ) { stringResource(it.label) }
            Spacer(modifier = Modifier.height(8.dp))
            ChoiceRow(
                stringResource(Res.string.settings_language_title),
                AppLanguage.entries,
                language,
                onLanguageChanged,
            ) { stringResource(it.label) }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.settings_display_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = ROW_INSET),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // None on a platform whose game always fills the screen.
            if (windowModes.isNotEmpty()) {
                ChoiceRow(
                    stringResource(Res.string.settings_window_mode_title),
                    windowModes,
                    windowMode,
                    onWindowModeChanged,
                ) { stringResource(it.label) }
                Spacer(modifier = Modifier.height(8.dp))
            }
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
 * A choice of a few short words - the menu's theme, the game's language, the desktop's window - as
 * one row of segments beside its label, in line with the switches: short words need no hints, and
 * radio rows like the display's would push those below the fold on a landscape phone. Each segment
 * takes the cursor on its own, and the arrows walk along them.
 */
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_INSET),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, option ->
                val cursor = rememberCursorMark()
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = cursor.modifier,
                    // The cursor's ring in place of the segment's outline, which is the Material
                    // default the rest of the time.
                    border = cursor.border()
                        ?: SegmentedButtonDefaults.borderStroke(MaterialTheme.colorScheme.outline),
                ) {
                    Text(optionLabel(option))
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

private val WindowMode.label: StringResource
    get() = when (this) {
        WindowMode.WINDOWED -> Res.string.settings_window_mode_windowed
        WindowMode.FULLSCREEN -> Res.string.settings_window_mode_fullscreen
        WindowMode.BORDERLESS -> Res.string.settings_window_mode_borderless
    }

/**
 * What a language is called in Settings: each in its own words, as a player looking for theirs in a
 * language they cannot read would look for it, and only "System" in the language the menu is in.
 */
private val AppLanguage.label: StringResource
    get() = when (this) {
        AppLanguage.SYSTEM -> Res.string.settings_language_system
        AppLanguage.ENGLISH -> Res.string.language_english
        AppLanguage.GERMAN -> Res.string.language_german
        AppLanguage.POLISH -> Res.string.language_polish
    }

private val DisplayMode.hint: StringResource
    get() = when (this) {
        DisplayMode.STRETCH -> Res.string.settings_display_stretch_hint
        DisplayMode.BLACK_BARS -> Res.string.settings_display_black_bars_hint
        DisplayMode.AMBIENT -> Res.string.settings_display_ambient_hint
    }
