package at.smiech.cyanbat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.button_achievements
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
import kotlin.math.min
import kotlin.math.sqrt

/** The main screen's buttons, top to bottom and then Exit: the places its cursor can be. */
private enum class MainChoice { START, ACHIEVEMENTS, SETTINGS, HELP, CREDITS, EXIT }

/**
 * The title screen's cursor. Its buttons sit on a dark sky and are filled with the theme's primary,
 * so the cursor is the bat's own cyan, pale enough to stand out from both.
 */
private val CURSOR_ON_SKY = Color(0xFFA6F6FF)

/** How far one press of up or down scrolls the help text. */
private val HELP_SCROLL_STEP = 48.dp

/**
 * The screen the title screen is laid out for: a phone held landscape, about 412dp tall, and
 * narrower than most, which leaves room for the title and the buttons and little else.
 */
private val LAID_OUT_WIDTH = 600.dp
private val LAID_OUT_HEIGHT = 412.dp

/** The most the title screen is drawn larger by, so a big monitor's buttons are not a poster's. */
private const val MOST_SCALE = 1.6f

/** How tall the title is drawn, and so how much of the sky it takes, at the title screen's scale. */
private val TITLE_HEIGHT = 88.dp

/** The narrowest the buttons are: wide enough to read as a column, whatever their words. */
private val BUTTON_WIDTH = 180.dp

/**
 * The title screen: the title over the flowing sky, and the menu's buttons. Plays the menu music.
 */
@Composable
fun MainMenuScreen(
    viewModel: MainMenuViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToCredits: () -> Unit,
    onNavigateToStageSelect: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    /** Starts the stage with this id, once the player has said how hard; see [DifficultyDialog]. */
    onStartStage: (stageId: Int) -> Unit,
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
                onStartStage(1)
            }

            MainChoice.ACHIEVEMENTS -> onNavigateToAchievements()
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

    Surface {
        FlowBackground(Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // In here, with the buttons, which are only composed once this is measured: asked for
            // outside, the cursor found none of them to land on.
            HomeCursor(cursor.getValue(home))
            // Laid out for a phone held landscape, the tightest it is shown on, and drawn larger on
            // a bigger screen, so a tablet or a desktop window does not see a phone's buttons lost
            // in a corner of its sky. By less than the screen is bigger, though, its square root: a
            // tablet is held nearer than a phone, and a monitor's window has more sky to show.
            val fits = min(maxWidth / LAID_OUT_WIDTH, maxHeight / LAID_OUT_HEIGHT)
            val scale = sqrt(fits).coerceIn(1f, MOST_SCALE)
            val upright = maxHeight > maxWidth
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density * scale, density.fontScale)
            ) {
                MainMenuLayout(cursor, upright, canExit, onChoose)
            }
        }
    }
}

/**
 * The title over the buttons, and Exit apart from them at the bottom right. Held landscape, the
 * title tops the screen and the buttons sit at the left of the sky under it; held [upright], where
 * that would leave a tall gap between the two, they are one block in the middle of the screen.
 */
@Composable
private fun MainMenuLayout(
    cursor: Map<MainChoice, FocusRequester>,
    upright: Boolean,
    canExit: Boolean,
    onChoose: (MainChoice) -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = if (upright) Arrangement.Center else Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Sized here rather than by the picture, whose size in dp would follow the screen's
            // density: on a desktop, at a density of one, it came out twice a phone's.
            Image(
                painter = painterResource(Res.drawable.title),
                contentDescription = stringResource(Res.string.title_image_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(TITLE_HEIGHT),
            )
            val buttons = @Composable {
                Column(
                    modifier = Modifier.width(IntrinsicSize.Max),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (choice in listOf(
                        MainChoice.START,
                        MainChoice.ACHIEVEMENTS,
                        MainChoice.SETTINGS,
                        MainChoice.HELP,
                        MainChoice.CREDITS
                    )) {
                        MenuButton(
                            choice,
                            cursor.getValue(choice),
                            onChoose,
                            Modifier.fillMaxWidth().widthIn(min = BUTTON_WIDTH),
                        )
                    }
                }
            }
            if (upright) {
                Spacer(Modifier.height(48.dp))
                buttons()
            } else {
                // In the middle of the sky under the title.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    buttons()
                }
            }
        }
        if (canExit) {
            MenuButton(
                MainChoice.EXIT,
                cursor.getValue(MainChoice.EXIT),
                onChoose,
                Modifier.align(Alignment.BottomEnd),
            )
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
        MainChoice.ACHIEVEMENTS -> Res.string.button_achievements
        MainChoice.SETTINGS -> Res.string.button_settings
        MainChoice.HELP -> Res.string.button_help
        MainChoice.CREDITS -> Res.string.button_credits
        MainChoice.EXIT -> Res.string.button_exit
    }
