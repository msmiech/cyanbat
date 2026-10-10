package at.smiech.cyanbat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.button_credits
import at.smiech.cyanbat.resources.button_exit
import at.smiech.cyanbat.resources.button_help
import at.smiech.cyanbat.resources.button_ok
import at.smiech.cyanbat.resources.button_settings
import at.smiech.cyanbat.resources.button_start_game
import at.smiech.cyanbat.resources.dialog_help_text
import at.smiech.cyanbat.resources.dialog_help_title
import at.smiech.cyanbat.resources.title
import at.smiech.cyanbat.resources.title_image_description
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The main screen's buttons, top to bottom and then Exit: the places its cursor can be. */
private enum class MainChoice { START, SETTINGS, HELP, CREDITS, EXIT }

/**
 * The title screen's cursor. Its buttons sit on a dark sky and are filled with the theme's primary,
 * so the cursor is the bat's own cyan, pale enough to stand out from both.
 */
private val CURSOR_ON_SKY = Color(0xFFA6F6FF)

/** How far one press of up or down scrolls the help text. */
private val HELP_SCROLL_STEP = 48.dp

/**
 * The title screen: the title over the flowing sky, and the menu's buttons. Plays the menu music.
 */
@Composable
fun MainMenuScreen(
    viewModel: MainMenuViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToCredits: () -> Unit,
    onNavigateToStageSelect: () -> Unit,
    onStartGame: (stageId: Int) -> Unit,
    /** Quits the game; null where it cannot quit, and there is no Exit. */
    onExit: (() -> Unit)?,
) {
    val isMusicEnabled by viewModel.isMusicEnabled.collectAsState()
    LaunchedEffect(isMusicEnabled) {
        if (isMusicEnabled) viewModel.startMusic() else viewModel.stopMusic()
    }
    val highestUnlocked by viewModel.highestUnlocked.collectAsState()

    // The button last taken, which is where the cursor is when the player comes back from the
    // screen it opened. Kept with the screen while another one is up; see CyanBatMenu.
    var cursor by rememberSaveable { mutableIntStateOf(MainChoice.START.ordinal) }

    var showHelpDialog by remember { mutableStateOf(false) }
    if (showHelpDialog) {
        HelpDialog { showHelpDialog = false }
    }

    MainMenuContent(home = MainChoice.entries[cursor], canExit = onExit != null) { choice ->
        cursor = choice.ordinal
        when (choice) {
            // Straight into the jungle until there is a second stage to choose; see StageSelectScreen.
            MainChoice.START -> if (highestUnlocked > 1) {
                onNavigateToStageSelect()
            } else {
                viewModel.stopMusic()
                onStartGame(1)
            }

            MainChoice.SETTINGS -> onNavigateToSettings()
            MainChoice.HELP -> showHelpDialog = true
            MainChoice.CREDITS -> onNavigateToCredits()
            MainChoice.EXIT -> if (onExit != null) {
                viewModel.stopMusic()
                onExit()
            }
        }
    }
}

/**
 * The help text, which runs longer than a phone held landscape is tall. It scrolls under a finger,
 * and under the up and down keys for a player with none on the screen: OK is the only thing on it
 * to choose, so the cursor rests there and the arrows are free to scroll.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HelpDialog(dismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = dismiss) {
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val step = with(LocalDensity.current) { HELP_SCROLL_STEP.toPx() }
        val ok = remember { FocusRequester() }
        val okCursor = rememberCursorMark()
        MenuKeys(
            onBack = { dismiss(); true },
            // Ahead of the cursor's own keys, which would look for something above or below OK to
            // move to, find nothing, and keep the keys.
            modifier = Modifier.onPreviewKeyEvent { event ->
                val meaning = menuKeyOf(event.key)
                if (meaning != MenuKey.UP && meaning != MenuKey.DOWN) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    scope.launch { scroll.animateScrollBy(if (meaning == MenuKey.UP) -step else step) }
                }
                true
            },
        ) {
            HomeCursor(ok)
            Surface {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.dialog_help_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    // Scrolls rather than pushing OK off a short landscape screen.
                    Text(
                        text = stringResource(helpControls) + "\n" + stringResource(Res.string.dialog_help_text),
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(scroll)
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = dismiss,
                        modifier = Modifier.align(Alignment.End).focusRequester(ok)
                            .then(okCursor.modifier),
                        border = okCursor.border(),
                    ) {
                        Text(stringResource(Res.string.button_ok))
                    }
                }
            }
        }
    }
}

/** The title screen's layout, with the cursor starting on [home], and Exit only if [canExit]. */
@Composable
private fun MainMenuContent(
    home: MainChoice,
    canExit: Boolean,
    onChoose: (MainChoice) -> Unit,
) {
    val cursor = remember { MainChoice.entries.associateWith { FocusRequester() } }
    HomeCursor(cursor.getValue(home))

    Surface {
        FlowBackground(Modifier.fillMaxSize())
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Image(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                painter = painterResource(Res.drawable.title),
                contentDescription = stringResource(Res.string.title_image_description)
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    for (choice in listOf(
                        MainChoice.START,
                        MainChoice.SETTINGS,
                        MainChoice.HELP,
                        MainChoice.CREDITS
                    )) {
                        MenuButton(
                            choice,
                            cursor.getValue(choice),
                            onChoose,
                            Modifier.fillMaxWidth()
                        )
                    }
                }
                if (canExit) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        MenuButton(MainChoice.EXIT, cursor.getValue(MainChoice.EXIT), onChoose)
                    }
                }
            }
        }
    }
}

/** The title screen's button for [choice], ringed while the cursor is on it. */
@Composable
private fun MenuButton(
    choice: MainChoice,
    focus: FocusRequester,
    onChoose: (MainChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cursor = rememberCursorMark()
    Button(
        onClick = { onChoose(choice) },
        modifier = modifier.focusRequester(focus).then(cursor.modifier),
        border = cursor.border(CURSOR_ON_SKY),
    ) {
        Text(stringResource(choice.label))
    }
}

/** The button's caption. */
private val MainChoice.label: StringResource
    get() = when (this) {
        MainChoice.START -> Res.string.button_start_game
        MainChoice.SETTINGS -> Res.string.button_settings
        MainChoice.HELP -> Res.string.button_help
        MainChoice.CREDITS -> Res.string.button_credits
        MainChoice.EXIT -> Res.string.button_exit
    }
