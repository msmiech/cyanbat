package at.smiech.cyanbat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.button_credits
import at.smiech.cyanbat.resources.button_exit
import at.smiech.cyanbat.resources.button_help
import at.smiech.cyanbat.resources.button_settings
import at.smiech.cyanbat.resources.button_start_game
import at.smiech.cyanbat.resources.dialog_help_text
import at.smiech.cyanbat.resources.dialog_help_title
import at.smiech.cyanbat.resources.menu_background
import at.smiech.cyanbat.resources.title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainMenuScreen(
    viewModel: MainMenuViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToCredits: () -> Unit,
    onStartGame: () -> Unit,
    onExit: () -> Unit,
) {
    val isMusicEnabled by viewModel.isMusicEnabled.collectAsState()
    LaunchedEffect(isMusicEnabled) {
        if (isMusicEnabled) viewModel.startMusic() else viewModel.stopMusic()
    }

    var showHelpDialog by remember { mutableStateOf(false) }
    if (showHelpDialog) {
        HelpDialog { showHelpDialog = false }
    }

    MainMenuContent(
        onStartGameClicked = {
            viewModel.stopMusic()
            onStartGame()
        },
        onHelpClicked = { showHelpDialog = true },
        onSettingsClicked = onNavigateToSettings,
        onCreditsClicked = onNavigateToCredits,
        onExitClicked = {
            viewModel.stopMusic()
            onExit()
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HelpDialog(dismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = dismiss) {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(Res.string.dialog_help_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(text = stringResource(Res.string.dialog_help_text))
                Spacer(Modifier.height(12.dp))
                TextButton(modifier = Modifier.align(Alignment.End), onClick = dismiss) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
private fun MainMenuContent(
    onStartGameClicked: () -> Unit,
    onHelpClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onCreditsClicked: () -> Unit,
    onExitClicked: () -> Unit,
) {
    Surface {
        Box(Modifier.fillMaxSize()) {
            Image(
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds,
                painter = painterResource(Res.drawable.menu_background),
                contentDescription = "Main menu background image"
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Image(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                painter = painterResource(Res.drawable.title),
                contentDescription = "Game title image"
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onStartGameClicked) {
                        Text(stringResource(Res.string.button_start_game))
                    }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onSettingsClicked) {
                        Text(stringResource(Res.string.button_settings))
                    }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onHelpClicked) {
                        Text(stringResource(Res.string.button_help))
                    }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onCreditsClicked) {
                        Text(stringResource(Res.string.button_credits))
                    }
                }
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Button(onClick = onExitClicked) {
                        Text(stringResource(Res.string.button_exit))
                    }
                }
            }
        }
    }
}
