package at.smiech.cyanbat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.credit_ai_disclosure_content
import at.smiech.cyanbat.resources.credit_ai_disclosure_title
import at.smiech.cyanbat.resources.credit_game_development
import at.smiech.cyanbat.resources.credit_gameframework_0
import at.smiech.cyanbat.resources.credit_gameframework_1
import at.smiech.cyanbat.resources.credit_gameframework_title
import at.smiech.cyanbat.resources.credit_music_0
import at.smiech.cyanbat.resources.credit_music_title
import at.smiech.cyanbat.resources.credits_headline
import org.jetbrains.compose.resources.stringResource

/**
 * The credits: who made the game and its music, the framework it began from, and the AI disclosure.
 */
@Composable
fun CreditsScreen() {
    Surface {
        // Scrolls, since it runs longer than a small phone held landscape is tall; and kept to a
        // column a line of which reads at a glance, in the middle of a screen wider than that.
        // Clear of the system's bars and a display's cutout, as Settings is.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(SCREEN_PADDING)
                .wrapContentWidth()
                .widthIn(max = CONTENT_WIDTH)
        ) {
            Text(
                text = stringResource(Res.string.credits_headline),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.credit_game_development)
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.credit_music_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(Res.string.credit_music_0))
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.credit_gameframework_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(Res.string.credit_gameframework_0))
            Text(stringResource(Res.string.credit_gameframework_1))

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.credit_ai_disclosure_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(Res.string.credit_ai_disclosure_content))
        }
    }
}
