package at.smiech.cyanbat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.smiech.cyanbat.Achievement
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.achievement_locked_badge
import at.smiech.cyanbat.resources.achievements_progress
import at.smiech.cyanbat.resources.achievements_title
import at.smiech.cyanbat.resources.button_ok
import at.smiech.cyanbat.resources.stage1_preview
import at.smiech.cyanbat.resources.stage2_preview
import at.smiech.cyanbat.resources.stage3_preview
import at.smiech.cyanbat.resources.stage4_preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource

/**
 * The widest the list is laid out: a card a line, its words still read at a glance. A wider screen
 * keeps it in the middle.
 */
private val LIST_MAX_WIDTH = 720.dp

/** The room between two cards, and between the heading and the first. */
private val CARD_GAP = 12.dp

/** How wide a card's picture is. Its height follows from the frame's 16:9. */
private val PICTURE_WIDTH = 168.dp

/** The game's frame, which every picture is of. */
private const val FRAME_ASPECT = 16f / 9f

/**
 * Every achievement, earned or not, in a single column that scrolls up and down: each with the
 * picture of the moment it was earned, which opens full size, and what earns it. Reached from the
 * main screen.
 */
@Composable
fun AchievementsScreen(viewModel: MainMenuViewModel) {
    val earned by viewModel.achievements.collectAsState()
    // Read and decoded once each, off the main thread, as they are earned; null for one with none.
    val pictures = remember { mutableStateMapOf<Achievement, ImageBitmap?>() }
    LaunchedEffect(earned) {
        for (achievement in Achievement.entries) {
            if (achievement !in earned || achievement in pictures) continue
            pictures[achievement] = withContext(Dispatchers.Default) {
                viewModel.screenshot(achievement)
                    ?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
            }
        }
    }
    // The achievement whose picture is open full size, if any.
    var viewing by rememberSaveable { mutableStateOf<Achievement?>(null) }
    AchievementsContent(earned, pictures, onView = { viewing = it })
    viewing?.let { achievement ->
        pictures[achievement]?.let {
            PictureDialog(
                achievement,
                it,
                onDismiss = { viewing = null })
        }
    }
}

/**
 * The achievements' layout: a heading with how many are earned, and every achievement's card under
 * it, a card a row, the cursor starting on the first.
 */
@Composable
private fun AchievementsContent(
    earned: Set<Achievement>,
    pictures: Map<Achievement, ImageBitmap?>,
    onView: (Achievement) -> Unit,
) {
    val first = remember { FocusRequester() }
    HomeCursor(first)
    Surface {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(SCREEN_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.achievements_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(
                    Res.string.achievements_progress,
                    earned.size,
                    Achievement.entries.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(CARD_GAP))
            Column(
                modifier = Modifier.widthIn(max = LIST_MAX_WIDTH).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
            ) {
                for (achievement in Achievement.entries) {
                    val picture = pictures[achievement]
                    AchievementCard(
                        achievement = achievement,
                        earned = achievement in earned,
                        picture = picture,
                        onView = if (picture != null) ({ onView(achievement) }) else null,
                        modifier = if (achievement == Achievement.entries.first()) {
                            Modifier.focusRequester(first)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

/**
 * One achievement: its picture at the left, and its name and what earns it beside it. A card with a
 * picture opens it full size through [onView], null for one without. Every card takes the cursor,
 * so a player on the keys can scroll down to the last.
 */
@Composable
private fun AchievementCard(
    achievement: Achievement,
    earned: Boolean,
    picture: ImageBitmap?,
    onView: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cursor = rememberCursorMark()
    val content: @Composable () -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min)) {
            AchievementPicture(achievement, earned, picture)
            Column(
                modifier = Modifier.weight(1f).padding(12.dp)
                    .alpha(if (earned) 1f else 0.6f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(achievement.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(achievement.description),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    // One card, whether or not it opens anything: a picture is read in after the card is first
    // drawn, and a card made over again then took the cursor with it.
    Card(
        onClick = { onView?.invoke() },
        modifier = modifier.fillMaxWidth().then(cursor.modifier),
        border = cursor.border(),
    ) {
        content()
    }
}

/**
 * The picture at a card's left, the frame's shape: the moment it was earned; the stage's own
 * preview for a stage cleared before there were achievements, which has none; a gold star for any
 * other without one; and for one not earned yet, the word that it is locked.
 */
@Composable
private fun AchievementPicture(achievement: Achievement, earned: Boolean, picture: ImageBitmap?) {
    val frame = Modifier.width(PICTURE_WIDTH).aspectRatio(FRAME_ASPECT)
    val stage = stagePreviewOf(achievement)
    when {
        picture != null -> FramePicture(picture, stringResource(achievement.title), frame)
        earned && stage != null ->
            FramePicture(imageResource(stage), stringResource(achievement.title), frame)

        earned -> Box(
            frame.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Stars(earned = 1, emptyColor = Color.Transparent, size = 40.dp, count = 1)
        }

        else -> Box(
            frame.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            // On one line, shrunk to fit rather than broken in two, as on a locked stage.
            Text(
                text = stringResource(Res.string.achievement_locked_badge),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 10.sp,
                    maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
                ),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** A picture of the game's frame, scaled without filtering, so its pixel art stays crisp. */
@Composable
private fun FramePicture(picture: ImageBitmap, description: String, modifier: Modifier) {
    Image(
        bitmap = picture,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.None,
        modifier = modifier,
    )
}

/** The preview of the stage [achievement] is for clearing, or null for one that is not. */
private fun stagePreviewOf(achievement: Achievement): DrawableResource? = when (achievement) {
    Achievement.STAGE_1_EASY, Achievement.STAGE_1_NORMAL, Achievement.STAGE_1_HARD ->
        Res.drawable.stage1_preview

    Achievement.STAGE_2 -> Res.drawable.stage2_preview
    Achievement.STAGE_3 -> Res.drawable.stage3_preview
    Achievement.STAGE_4 -> Res.drawable.stage4_preview
    else -> null
}

/**
 * [achievement]'s picture as large as the screen allows, under its name. Worked like the help: OK
 * is the only thing on it, and Escape, Back or a tap outside closes it too.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PictureDialog(achievement: Achievement, picture: ImageBitmap, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        val ok = remember { FocusRequester() }
        val okCursor = rememberCursorMark()
        MenuKeys(onBack = { onDismiss(); true }) {
            HomeCursor(ok)
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(achievement.title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(achievement.description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Shrinks to the height left on a short landscape screen rather than pushing OK
                    // off it, keeping the frame's shape.
                    FramePicture(
                        picture,
                        stringResource(achievement.title),
                        Modifier.weight(1f, fill = false).fillMaxWidth()
                            .aspectRatio(FRAME_ASPECT, matchHeightConstraintsFirst = true),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onDismiss,
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
