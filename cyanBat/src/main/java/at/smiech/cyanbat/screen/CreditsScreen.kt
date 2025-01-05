package at.smiech.cyanbat.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.R

/**
 * Compose-based screen for displaying credits.
 *
 * @author msmiech
 */
@Composable
fun CreditsScreen() {
    MaterialTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.credits_headline),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.credit_music_title),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.credit_music_0))
                Text(stringResource(R.string.credit_music_1))
                Text(stringResource(R.string.credit_music_2))
                Text(
                    text = stringResource(R.string.credit_gameframework_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.credit_gameframework_0))
                Text(stringResource(R.string.credit_gameframework_1))
            }
        }
    }
}

@Preview
@Composable
fun CreditsScreenPreview() {
    CreditsScreen()
}
