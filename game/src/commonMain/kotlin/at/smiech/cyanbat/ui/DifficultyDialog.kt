package at.smiech.cyanbat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.button_back
import at.smiech.cyanbat.resources.difficulty_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The most stars a stage can show: one for each difficulty. */
private val MOST_STARS = Difficulty.entries.maxOf { it.stars }

/** An earned star. Gold whatever the theme, as stars are: they stand out from the cool menu. */
private val STAR_GOLD = Color(0xFFFFC83D)

/** An earned star's rim, which keeps it from washing out on a light card. */
private val STAR_RIM = Color(0xFFB07A00)

/** How far an inner corner of a star is from its middle, against a point. */
private const val STAR_INNER_RADIUS = 0.45f

/**
 * Asks how hard the run on [stage] is to be, as it starts: one choice per [Difficulty], easiest
 * first, each with its stars and what it changes. The cursor starts on [home].
 *
 * Worked like the help: the arrows move between the choices, Enter takes one, and Escape or Back,
 * a tap outside or the back button closes it without starting anything.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DifficultyDialog(
    stage: StringResource,
    home: Difficulty,
    onChoose: (Difficulty) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        val cursor = remember { Difficulty.entries.associateWith { FocusRequester() } }
        MenuKeys(onBack = { onDismiss(); true }) {
            HomeCursor(cursor.getValue(home))
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(stage),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.difficulty_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Scrolls rather than pushing the way back off a short landscape screen.
                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (difficulty in Difficulty.entries) {
                            DifficultyChoice(
                                difficulty,
                                onClick = { onChoose(difficulty) },
                                modifier = Modifier.focusRequester(cursor.getValue(difficulty)),
                            )
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

/** One difficulty to choose: its name and stars, and what it changes under them. */
@Composable
private fun DifficultyChoice(
    difficulty: Difficulty,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cursor = rememberCursorMark()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(cursor.modifier),
        border = cursor.border(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(difficulty.title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Stars(
                    earned = difficulty.stars,
                    emptyColor = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = stringResource(difficulty.description),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * A row of stars, [earned] of them filled and the rest of the [MOST_STARS] drawn as outlines in
 * [emptyColor], read by a screen reader as [description] when there is one.
 *
 * Drawn rather than set as text: not every platform's font has a star in it, the browser's least.
 */
@Composable
internal fun Stars(
    earned: Int,
    emptyColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    description: String? = null,
) {
    val gap = size / 8
    Canvas(
        modifier
            .size(width = size * MOST_STARS + gap * (MOST_STARS - 1), height = size)
            .then(
                if (description != null) {
                    Modifier.clearAndSetSemantics { contentDescription = description }
                } else {
                    Modifier
                }
            )
    ) {
        val side = this.size.height
        val step = side + gap.toPx()
        val rim = Stroke(width = side / 12f, join = StrokeJoin.Round)
        repeat(MOST_STARS) { index ->
            val star = starPath(Offset(index * step + side / 2f, side / 2f), side / 2f)
            if (index < earned) {
                drawPath(star, STAR_GOLD)
                drawPath(star, STAR_RIM, style = rim)
            } else {
                drawPath(star, emptyColor, style = rim)
            }
        }
    }
}

/** A five-pointed star around [center], its points [radius] out, one straight up. */
private fun starPath(center: Offset, radius: Float): Path = Path().apply {
    // Its rim is drawn on the outline, half of it outside, so the points are pulled in to keep
    // the whole star inside its square.
    val outer = radius * 0.92f
    for (corner in 0 until 10) {
        val r = if (corner % 2 == 0) outer else outer * STAR_INNER_RADIUS
        val angle = -PI / 2 + corner * PI / 5
        val x = center.x + r * cos(angle).toFloat()
        val y = center.y + r * sin(angle).toFloat()
        if (corner == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
