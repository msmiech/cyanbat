package at.smiech.cyanbat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import at.smiech.cyanbat.data.TouchSteering
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.button_back
import at.smiech.cyanbat.resources.steering_hint
import at.smiech.cyanbat.resources.steering_title
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource

/**
 * The previews' shape, as `generate_steering_previews.py` draws them: the bottom of a frame of the
 * game, where the hands are, wider than the whole frame's 16:9 so that the two choices fit side by
 * side above the fold on a landscape phone.
 */
private const val PREVIEW_ASPECT = 2f

/**
 * Asks how a finger is to fly the bat - dragging it, or a stick drawn at the bottom left - before
 * the first run on a touch screen. Each choice is a picture of a hand flying the bat that way, with
 * what it means under it. The cursor starts on [home].
 *
 * Worked like the difficulty's: the arrows move between the choices, Enter takes one, and Escape or
 * Back, a tap outside or the back button closes it without starting anything. Until the player has
 * chosen, it is asked again before the next run.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SteeringDialog(
    home: TouchSteering,
    onChoose: (TouchSteering) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        // As wide as a dialog goes, rather than the platform's own width for one, which is too
        // narrow on the desktop and some phones to set the two pictures side by side.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val cursor = remember { TouchSteering.entries.associateWith { FocusRequester() } }
        MenuKeys(onBack = { onDismiss(); true }) {
            HomeCursor(cursor.getValue(home))
            Surface(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.steering_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.steering_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Scrolls rather than pushing the way back off a short landscape screen.
                    BoxWithConstraints(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    ) {
                        val choices = @Composable { modifier: Modifier ->
                            for (steering in TouchSteering.entries) {
                                SteeringChoice(
                                    steering,
                                    onClick = { onChoose(steering) },
                                    modifier = modifier
                                        .focusRequester(cursor.getValue(steering)),
                                )
                            }
                        }
                        // Side by side, where the pictures are best compared; one above the other
                        // on a screen too narrow for two, a phone's browser held upright.
                        if (maxWidth >= SIDE_BY_SIDE_WIDTH) {
                            Row(
                                Modifier.height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                choices(Modifier.weight(1f).fillMaxHeight())
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                choices(Modifier.fillMaxWidth())
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val backCursor = rememberCursorMark()
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End).then(backCursor.modifier),
                        border = backCursor.border(),
                    ) {
                        Text(stringResource(Res.string.button_back))
                    }
                }
            }
        }
    }
}

/** The narrowest the dialog's choices are set side by side at. */
private val SIDE_BY_SIDE_WIDTH = 400.dp

/** One way to steer: its picture, its name, and what it means. */
@Composable
private fun SteeringChoice(
    steering: TouchSteering,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cursor = rememberCursorMark()
    Card(
        onClick = onClick,
        modifier = modifier.then(cursor.modifier),
        border = cursor.border(),
    ) {
        Image(
            bitmap = imageResource(steering.preview),
            // The name under it says what it shows.
            contentDescription = null,
            // Pixel art, scaled without filtering, as the stage select's previews are.
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxWidth().aspectRatio(PREVIEW_ASPECT),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(steering.title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(steering.description),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
