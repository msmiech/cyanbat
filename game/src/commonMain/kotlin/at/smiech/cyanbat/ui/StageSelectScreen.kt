package at.smiech.cyanbat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.stage1_preview
import at.smiech.cyanbat.resources.stage2_preview
import at.smiech.cyanbat.resources.stage3_preview
import at.smiech.cyanbat.resources.stage4_preview
import at.smiech.cyanbat.resources.stage5_preview
import at.smiech.cyanbat.resources.stage_1_description
import at.smiech.cyanbat.resources.stage_1_name
import at.smiech.cyanbat.resources.stage_2_description
import at.smiech.cyanbat.resources.stage_2_name
import at.smiech.cyanbat.resources.stage_3_description
import at.smiech.cyanbat.resources.stage_3_name
import at.smiech.cyanbat.resources.stage_4_description
import at.smiech.cyanbat.resources.stage_4_name
import at.smiech.cyanbat.resources.stage_5_description
import at.smiech.cyanbat.resources.stage_5_name
import at.smiech.cyanbat.resources.stage_highscore
import at.smiech.cyanbat.resources.stage_locked
import at.smiech.cyanbat.resources.stage_locked_badge
import at.smiech.cyanbat.resources.stage_not_cleared
import at.smiech.cyanbat.resources.stage_select_title
import at.smiech.cyanbat.stageOpen
import at.smiech.cyanbat.util.ENDLESS_STAGE_ID
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

/** The widest the cards are laid out across; see [StageSelectContent]. */
private val GRID_MAX_WIDTH = 1000.dp

/** The room between two cards, and between the title and the first row. */
private val CARD_GAP = 16.dp

/** Every stage's card, in order. */
private val STAGES = listOf(
    StageEntry(
        1,
        Res.string.stage_1_name,
        Res.string.stage_1_description,
        Res.drawable.stage1_preview
    ),
    StageEntry(
        2,
        Res.string.stage_2_name,
        Res.string.stage_2_description,
        Res.drawable.stage2_preview
    ),
    StageEntry(
        3,
        Res.string.stage_3_name,
        Res.string.stage_3_description,
        Res.drawable.stage3_preview
    ),
    StageEntry(
        4,
        Res.string.stage_4_name,
        Res.string.stage_4_description,
        Res.drawable.stage4_preview
    ),
)

/**
 * The endless stage's card, under the stages' row and as wide as it: not a stage to clear but a run
 * to see how far it goes, opened by clearing the last stage.
 */
private val ENDLESS = StageEntry(
    ENDLESS_STAGE_ID,
    Res.string.stage_5_name,
    Res.string.stage_5_description,
    Res.drawable.stage5_preview,
)

/** The name of the stage with [id], as its card and its run head it; the first's for an unknown id. */
internal fun stageNameOf(id: Int): StringResource =
    ((STAGES + ENDLESS).firstOrNull { it.id == id } ?: STAGES.first()).name

/**
 * The stage select, reached from Start Game once the player has cleared a stage. Until then there
 * is nothing to choose, so Start Game goes straight into stage 1. The endless stage's card sits under
 * the stages', locked until the last of them is cleared.
 *
 * @param onStartStage starts the stage with this id, once the player has said how hard; see
 *   [DifficultyDialog].
 */
@Composable
fun StageSelectScreen(
    viewModel: MainMenuViewModel,
    onStartStage: (Int) -> Unit,
) {
    val highestUnlocked by viewModel.highestUnlocked.collectAsState()
    val highscores by viewModel.highscores.collectAsState()
    val clears by viewModel.clears.collectAsState()
    StageSelectContent(
        highestUnlocked = highestUnlocked,
        highscores = highscores,
        clears = clears,
        onStartStage = onStartStage,
    )
}

/**
 * The stage select's layout: the stages' cards in rows of as many as the screen has room for (see
 * [columnsFor]), the endless stage's under them as wide as a row, the cursor starting on the
 * furthest open.
 */
@Composable
private fun StageSelectContent(
    highestUnlocked: Int,
    highscores: Map<Int, Int>,
    clears: Map<Int, Difficulty>,
    onStartStage: (Int) -> Unit,
) {
    val cursor = remember { (STAGES + ENDLESS).associate { it.id to FocusRequester() } }
    val endlessOpen = stageOpen(ENDLESS.id, highestUnlocked, clears)

    Surface {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(SCREEN_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.stage_select_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(CARD_GAP))
            // Capped wide enough that each card's preview strip is still a picture of the stage
            // rather than a sliver of it.
            BoxWithConstraints(Modifier.widthIn(max = GRID_MAX_WIDTH).fillMaxWidth()) {
                // On the furthest stage open: the one the player has just won their way into, and
                // the one they are most likely here for. Asked for in here, where the cards are,
                // since what is in here is only composed once it is measured.
                HomeCursor(
                    cursor.getValue(
                        if (endlessOpen) ENDLESS.id else highestUnlocked.coerceIn(1, STAGES.size)
                    )
                )
                val columns = columnsFor(maxWidth)
                Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                    for (row in STAGES.chunked(columns)) {
                        // Every card in a row as tall as the tallest, a locked one's included,
                        // whose description is a line where an open one's runs to four.
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                        ) {
                            for (stage in row) {
                                StageCard(
                                    stage = stage,
                                    unlocked = stageOpen(stage.id, highestUnlocked, clears),
                                    highscore = highscores[stage.id] ?: 0,
                                    cleared = clears[stage.id],
                                    onClick = { onStartStage(stage.id) },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        .focusRequester(cursor.getValue(stage.id)),
                                )
                            }
                        }
                    }
                    StageCard(
                        stage = ENDLESS,
                        unlocked = endlessOpen,
                        highscore = highscores[ENDLESS.id] ?: 0,
                        // A run that only ends one way is never cleared, so it has no stars to earn.
                        cleared = null,
                        stars = false,
                        onClick = { onStartStage(ENDLESS.id) },
                        modifier = Modifier.fillMaxWidth().focusRequester(cursor.getValue(ENDLESS.id)),
                    )
                }
            }
        }
    }
}

/**
 * How many of the stages' cards sit side by side across [width]: all four where each still has
 * room for its name and a few words a line, as on a phone held landscape; two on one held upright
 * or a narrow window; and one where even two would be columns of single words.
 */
private fun columnsFor(width: Dp): Int = when {
    width >= 600.dp -> 4
    width >= 340.dp -> 2
    else -> 1
}

/**
 * One stage: its preview, its name and, once it is open, its highscore and its stars. The score
 * sits straight under the name rather than after the description, so it lines up across cards whose
 * descriptions run to different lengths.
 *
 * @param cleared the hardest difficulty the stage has been cleared on, which earns it as many stars
 *   as it is worth; null for none yet.
 * @param stars whether the card shows its stars at all: not for the endless stage, which is never
 *   cleared.
 */
@Composable
private fun StageCard(
    stage: StageEntry,
    unlocked: Boolean,
    highscore: Int,
    cleared: Difficulty?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stars: Boolean = true,
) {
    val cursor = rememberCursorMark()
    Card(
        onClick = onClick,
        enabled = unlocked,
        modifier = modifier.then(cursor.modifier),
        border = cursor.border(),
    ) {
        Box {
            Image(
                bitmap = imageResource(stage.preview),
                contentDescription = stringResource(stage.name),
                // The previews are pixel art at the game's own 640x360; scaled without filtering
                // they stay crisp instead of going soft.
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                // A strip rather than the whole 16:9 frame, so the name and description still fit
                // above the fold on a landscape phone, which is only about 400dp tall.
                modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT)
                    .alpha(if (unlocked) 1f else 0.35f),
            )
            if (!unlocked) {
                // On one line, shrunk to fit a narrow card rather than broken in two: ZABLOKOWANY
                // is wider than a phone's four cards leave it.
                Text(
                    text = stringResource(Res.string.stage_locked_badge),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 12.sp,
                        maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
                    ),
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                )
            } else if (stars) {
                // In a corner of the preview, on a dark backing that keeps them readable over any
                // stage's art. Every open stage shows all three, the ones still to earn as
                // outlines, so a stage never cleared says as much.
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Stars(
                        earned = cleared?.stars ?: 0,
                        emptyColor = Color.White.copy(alpha = 0.7f),
                        description = stringResource(
                            cleared?.cleared ?: Res.string.stage_not_cleared
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
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
