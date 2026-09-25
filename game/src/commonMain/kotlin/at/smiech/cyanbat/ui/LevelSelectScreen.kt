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
import at.smiech.cyanbat.resources.level1_preview
import at.smiech.cyanbat.resources.level2_preview
import at.smiech.cyanbat.resources.level_1_description
import at.smiech.cyanbat.resources.level_1_name
import at.smiech.cyanbat.resources.level_2_description
import at.smiech.cyanbat.resources.level_2_name
import at.smiech.cyanbat.resources.level_locked
import at.smiech.cyanbat.resources.level_select_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource

/** One card on the level select: which level it starts, and how it is shown. */
private data class LevelEntry(
    val id: Int,
    val name: StringResource,
    val description: StringResource,
    val preview: DrawableResource,
)

/** How tall a card's preview strip is. */
private val PREVIEW_HEIGHT = 130.dp

private val LEVELS = listOf(
    LevelEntry(1, Res.string.level_1_name, Res.string.level_1_description, Res.drawable.level1_preview),
    LevelEntry(2, Res.string.level_2_name, Res.string.level_2_description, Res.drawable.level2_preview),
)

/**
 * The level select, reached from Start Game once the player has cleared a level.
 *
 * Before that there is nothing to choose between, so Start Game goes straight into level 1 and
 * this screen is never shown - a menu with one live option and one padlock is a menu asking the
 * player to read it for no reason.
 */
@Composable
fun LevelSelectScreen(
    viewModel: MainMenuViewModel,
    onStartLevel: (Int) -> Unit,
) {
    val highestUnlocked by viewModel.highestUnlocked.collectAsState()
    LevelSelectContent(
        highestUnlocked = highestUnlocked,
        onStartLevel = { id ->
            viewModel.stopMusic()
            onStartLevel(id)
        },
    )
}

@Composable
private fun LevelSelectContent(highestUnlocked: Int, onStartLevel: (Int) -> Unit) {
    Surface {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.level_select_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (level in LEVELS) {
                    LevelCard(
                        level = level,
                        unlocked = level.id <= highestUnlocked,
                        onClick = { onStartLevel(level.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelCard(
    level: LevelEntry,
    unlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, enabled = unlocked, modifier = modifier) {
        Box {
            Image(
                bitmap = imageResource(level.preview),
                contentDescription = stringResource(level.name),
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
                text = stringResource(level.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (unlocked) level.description else Res.string.level_locked),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
