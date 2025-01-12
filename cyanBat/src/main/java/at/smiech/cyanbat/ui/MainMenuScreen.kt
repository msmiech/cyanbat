package at.smiech.cyanbat.ui

import android.app.Activity
import android.content.Intent
import android.util.Log
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import at.smiech.cyanbat.R
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.activities.CyanBatGameActivity.Companion.TAG

@Composable
fun MainMenuScreen(
    navController: NavHostController,
    viewModel: MainMenuViewModel = viewModel()
) {
    val isMusicEnabled by viewModel.isMusicEnabled().collectAsState(true)
    LaunchedEffect("music") {
        Log.d(TAG, "MainMenuScreen#launchMusic: $isMusicEnabled")
        if (isMusicEnabled) {
            viewModel.startMusic()
        } else {
            viewModel.stopMusic()
        }
    }

    val showHelpDialog = remember { mutableStateOf(false) }
    MaterialTheme {
        if (showHelpDialog.value) {
            HelpDialog {
                showHelpDialog.value = false
            }
        }

        MainMenuContent(
            onHelpClicked = { showHelpDialog.value = true },
            onSettingsClicked = {
                navController.navigate(Screen.Settings.route)
            },
            onCreditsClicked = {
                navController.navigate(Screen.Credits.route)
            },
            onExit = viewModel::stopMusic
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HelpDialog(dismiss: () -> Unit = {}) {
    BasicAlertDialog(onDismissRequest = dismiss) {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.dialog_help_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.dialog_help_text)
                )
                Spacer(Modifier.height(12.dp))
                TextButton(modifier = Modifier.align(Alignment.End), onClick = dismiss) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
fun MainMenuContent(
    onHelpClicked: () -> Unit = {},
    onSettingsClicked: () -> Unit = {},
    onCreditsClicked: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    val context = LocalContext.current
    Surface {
        Box(Modifier.fillMaxSize()) {
            Image(
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds,
                painter = painterResource(id = R.drawable.menu_background),
                contentDescription = "Main menu background image"
            )
        }
        Column(
            Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                painter = painterResource(id = R.drawable.title),
                contentDescription = "Game title image"
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        context.startActivity(Intent(context, CyanBatGameActivity::class.java))
                    }) {
                        Text(stringResource(R.string.button_start_game))
                    }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onSettingsClicked) {
                        Text(stringResource(R.string.button_settings))
                    }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onHelpClicked) {
                        Text(stringResource(R.string.button_help))
                    }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onCreditsClicked) {
                        Text(stringResource(R.string.button_credits))
                    }
                }
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Button(
                        onClick = {
                            onExit()
                            (context as? Activity)?.finishAffinity()
                        }
                    ) {
                        Text(stringResource(R.string.button_exit))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainMenuContentPreview() {
    MaterialTheme {
        MainMenuContent()
    }
}

@Preview(showBackground = true)
@Composable
private fun HelpDialogPreview() {
    MaterialTheme {
        HelpDialog()
    }
}
