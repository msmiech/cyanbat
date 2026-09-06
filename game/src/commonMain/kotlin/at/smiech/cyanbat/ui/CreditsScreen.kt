package at.smiech.cyanbat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.credit_gameframework_0
import at.smiech.cyanbat.resources.credit_gameframework_1
import at.smiech.cyanbat.resources.credit_gameframework_title
import at.smiech.cyanbat.resources.credit_music_0
import at.smiech.cyanbat.resources.credit_music_1
import at.smiech.cyanbat.resources.credit_music_2
import at.smiech.cyanbat.resources.credit_music_title
import at.smiech.cyanbat.resources.credits_headline
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreditsScreen() {
    Surface {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(Res.string.credits_headline),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.credit_music_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(Res.string.credit_music_0))
            Text(stringResource(Res.string.credit_music_1))
            Text(stringResource(Res.string.credit_music_2))
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.credit_gameframework_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(Res.string.credit_gameframework_0))
            Text(stringResource(Res.string.credit_gameframework_1))
        }
    }
}
