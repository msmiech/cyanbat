package at.smiech.cyanbat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.stage1_preview
import at.smiech.cyanbat.resources.stage2_preview
import at.smiech.cyanbat.resources.stage_1_description
import at.smiech.cyanbat.resources.stage_1_name
import at.smiech.cyanbat.resources.stage_2_description
import at.smiech.cyanbat.resources.stage_2_name
import at.smiech.cyanbat.resources.stage_highscore
import at.smiech.cyanbat.resources.stage_locked
import at.smiech.cyanbat.resources.stage_select_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource

/** One card on the stage select: which stage it starts, and how it is shown. */
private data class StageEntry(
    val id: Int,
    val name: StringResource,
    val description: StringResource,
    val preview: DrawableResource,
)

/** How tall a card's preview strip is. */
private val PREVIEW_HEIGHT = 130.dp

private val STAGES = listOf(
    StageEntry(1, Res.string.stage_1_name, Res.string.stage_1_description, Res.drawable.stage1_preview),
    StageEntry(2, Res.string.stage_2_name, Res.string.stage_2_description, Res.drawable.stage2_preview),
)

/**
 * The stage select, reached from Start Game once the player has cleared a stage.
 *
 * Before that there is nothing to choose between, so Start Game goes straight into stage 1 and
 * this screen is never shown - a menu with one live option and one padlock is a menu asking the
 * player to read it for no reason.
 */
@Composable
fun StageSelectScreen(
    viewModel: MainMenuViewModel,
    onStartStage: (Int) -> Unit,
) {
    val highestUnlocked by viewModel.highestUnlocked.collectAsState()
    val highscores by viewModel.highscores.collectAsState()
    StageSelectContent(
        highestUnlocked = highestUnlocked,
        highscores = highscores,
        onStartStage = { id ->
            viewModel.stopMusic()
            onStartStage(id)
        },
    )
}

@Composable
private fun StageSelectContent(
    highestUnlocked: Int,
    highscores: Map<Int, Int>,
    onStartStage: (Int) -> Unit,
) {
    Surface {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.stage_select_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (stage in STAGES) {
                    StageCard(
                        stage = stage,
                        unlocked = stage.id <= highestUnlocked,
                        highscore = highscores[stage.id] ?: 0,
                        onClick = { onStartStage(stage.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One stage: its preview, its name and, once it is open, its highscore. The score sits straight
 * under the name rather than after the description, so it lines up across cards whose
 * descriptions run to different lengths.
 */
@Composable
private fun StageCard(
    stage: StageEntry,
    unlocked: Boolean,
    highscore: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, enabled = unlocked, modifier = modifier) {
        Box {
            Image(
                bitmap = imageResource(stage.preview),
                contentDescription = stringResource(stage.name),
                // The previews are pixel art at the game's own 480x320; scaled without filtering
                // they stay crisp instead of going soft.
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                // A strip rather than the whole 3:2 frame, so the name and description still fit
                // above the fold on a landscape phone, which is only about 400dp tall.
                modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT).alpha(if (unlocked) 1f else 0.35f),
            )
            if (!unlocked) {
                Text(
                    text = "LOCKED",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(stage.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (unlocked) {
                Text(
                    text = stringResource(Res.string.stage_highscore, highscore),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (unlocked) stage.description else Res.string.stage_locked),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
