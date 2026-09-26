package at.smiech.cyanbat.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2 * PI).toFloat()

/** Sky, top to bottom: deep teal fading down to near black, darkest where the buttons sit. */
private val SKY = listOf(Color(0xFF0D4B5A), Color(0xFF07313D), Color(0xFF03141B))

/** A soft pool of light behind the wave, so it seems to glow rather than lie on the sky. */
private val WAVE_LIGHT = Color(0x382FC4D8)

private val STRAND = Color(0xFFE4FCFF)
private val SHEET_BODY = Color(0xFF9FEFFF)
private val SPARKLE_TINT = Color(0xFFBFF6FF)

/** Line segments per strand across the screen. The swells are long; more buys nothing visible. */
private const val SAMPLES = 64

/** How far a strand weaves off its place in the sheet, as a fraction of the height. */
private const val RIPPLE = 0.012f

private const val SPARKLE_COUNT = 48

/** Fixed, so the sparkles scatter the same way every time the menu opens. */
private const val SPARKLE_SEED = 0x5EED

/** A sparkle's halo and glints, in multiples of its radius. */
private const val HALO = 4f
private const val GLINT = 5f

private val SPARKLE_HALO = Brush.radialGradient(
    0f to Color.White,
    0.2f to SPARKLE_TINT.copy(alpha = 0.55f),
    1f to Color.Transparent,
    center = Offset.Zero,
    radius = HALO,
)
private val GLINT_ACROSS = Brush.horizontalGradient(
    listOf(Color.Transparent, Color.White, Color.Transparent), startX = -GLINT, endX = GLINT,
)
private val GLINT_DOWN = Brush.verticalGradient(
    listOf(Color.Transparent, Color.White, Color.Transparent), startY = -GLINT, endY = GLINT,
)

/**
 * One ribbon of the wave: a sheet of fine strands that swells, and twists over on itself as its
 * breadth passes through zero. Where it twists the strands bunch together and brighten, which is
 * most of what makes it read as a folding sheet of light rather than as a bundle of lines.
 *
 * Lengths are fractions of the screen's height, so the wave keeps its shape on any aspect ratio.
 */
private class Sheet(
    /** Where the sheet's middle runs at the left and right edges of the screen. */
    val left: Float,
    val right: Float,
    /** How far the middle swells above and below that line. */
    val swell: Float,
    /** Half the sheet's breadth where it is widest. */
    val breadth: Float,
    val strands: Int,
    /** Radians per second the swells roll at. */
    val speed: Float,
    /** Keeps this sheet's swells out of step with the other's. */
    val phase: Float,
    /** Scales every strand's alpha: the sheet behind is the fainter one. */
    val brightness: Float,
) {
    /** The sheet's middle at [u] across the screen (0 to 1), [t] seconds in. */
    fun middle(u: Float, t: Float): Float =
        left + (right - left) * u + swell * (
            0.65f * sin(TAU * 0.75f * u - speed * t + phase) +
                0.35f * sin(TAU * 1.6f * u + 0.6f * speed * t + 1.7f * phase)
            )

    /** Signed half-breadth: through zero is where the sheet twists over. */
    fun span(u: Float, t: Float): Float =
        breadth * sin(TAU * 0.5f * u - 0.7f * speed * t + 2.3f * phase)

    /** How far the strand at [f] (-1 to 1 across the sheet) has weaved off its place. */
    fun ripple(u: Float, t: Float, f: Float): Float =
        RIPPLE * sin(TAU * 1.3f * u - 1.2f * speed * t + 2.6f * f + phase)
}

private val SHEETS = listOf(
    Sheet(left = 0.50f, right = 0.74f, swell = 0.10f, breadth = 0.11f, strands = 16, speed = 0.22f, phase = 0f, brightness = 0.6f),
    Sheet(left = 0.60f, right = 0.62f, swell = 0.12f, breadth = 0.08f, strands = 22, speed = 0.30f, phase = 2.4f, brightness = 1f),
)

/** The sheet the sparkles drift along. */
private val FRONT = SHEETS.last()

/** A sparkle's fixed character; where it is at any moment is worked out from these and the clock. */
private class Sparkle(
    /** Where it starts across the screen, 0 to 1. */
    val start: Float,
    /** Screen widths per second it drifts rightward at. */
    val drift: Float,
    /** Above (-) or below (+) the front sheet's middle, as a fraction of the height. */
    val offset: Float,
    /** Radians per second of its slow bob up and down. */
    val bob: Float,
    /** Radians per second it twinkles at. */
    val twinkle: Float,
    val phase: Float,
    val radius: Float,
    /** Whether it flares into a four-pointed star when it peaks, or only glows. */
    val glints: Boolean,
)

private fun scatterSparkles(random: Random): List<Sparkle> = List(SPARKLE_COUNT) {
    Sparkle(
        start = random.nextFloat(),
        drift = 0.008f + 0.02f * random.nextFloat(),
        // The difference of two uniforms: most of them hug the wave, a few stray well off it.
        offset = 0.4f * (random.nextFloat() - random.nextFloat()),
        bob = 0.3f + 0.5f * random.nextFloat(),
        twinkle = 0.8f + 1.6f * random.nextFloat(),
        phase = TAU * random.nextFloat(),
        radius = 0.7f + 1.5f * random.nextFloat(),
        glints = random.nextFloat() < 0.35f,
    )
}

/**
 * The main menu's backdrop: a ribbon of light rolling slowly across a deep teal sky, after the
 * "flow" behind the PlayStation 3's XMB, with sparkles drifting along it.
 *
 * Nothing here is stepped. Every strand and every sparkle is a function of the seconds since the
 * menu appeared, so there is no particle state to update or keep - a sparkle is wherever its seed
 * and the clock put it.
 */
@Composable
fun FlowBackground(modifier: Modifier = Modifier) {
    val seconds = rememberSeconds()
    val sparkles = remember { scatterSparkles(Random(SPARKLE_SEED)) }
    Spacer(
        modifier
            // Its own layer, so repainting the sky every frame does not repaint the menu over it.
            .graphicsLayer()
            .drawWithCache {
                val sky = Brush.verticalGradient(SKY)
                val light = Brush.radialGradient(
                    listOf(WAVE_LIGHT, Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.62f),
                    radius = size.maxDimension * 0.6f,
                )
                val hairline = Stroke(width = 1.dp.toPx())
                val edgeLine = Stroke(width = 1.5.dp.toPx())
                val edgeHalo = Stroke(width = 6.dp.toPx())
                val path = Path()
                onDrawBehind {
                    // Read here and only here, so a new frame redraws without recomposing.
                    val t = seconds.floatValue
                    drawRect(sky)
                    drawRect(light)
                    for (sheet in SHEETS) drawSheet(sheet, t, path, hairline, edgeLine, edgeHalo)
                    for (sparkle in sparkles) drawSparkle(sparkle, t)
                }
            }
    )
}

/** Seconds since this first composed, ticking once per frame. */
@Composable
private fun rememberSeconds(): FloatState {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { seconds.floatValue = (it - start) / 1_000_000_000f }
        }
    }
    return seconds
}

private fun DrawScope.drawSheet(
    sheet: Sheet,
    t: Float,
    path: Path,
    hairline: Stroke,
    edgeLine: Stroke,
    edgeHalo: Stroke,
) {
    // A faint body between the outermost strands, so the sheet has substance between its lines.
    path.reset()
    traceStrand(path, sheet, t, -1f)
    for (i in SAMPLES downTo 0) {
        val u = i / SAMPLES.toFloat()
        path.lineTo(u * size.width, strandY(sheet, u, t, 1f))
    }
    path.close()
    drawPath(path, SHEET_BODY, alpha = 0.05f * sheet.brightness)

    for (s in 0 until sheet.strands) {
        val f = -1f + 2f * s / (sheet.strands - 1)
        // The outermost strands carry the sheet's edges and are far brighter than its inside -
        // the look of a glassy surface catching the light along its rims.
        val f2 = f * f
        val edge = f2 * f2 * f2 * f2
        path.reset()
        traceStrand(path, sheet, t, f)
        if (edge > 0.5f) {
            drawPath(path, STRAND, alpha = 0.07f * sheet.brightness, style = edgeHalo)
            drawPath(path, STRAND, alpha = (0.07f + 0.43f * edge) * sheet.brightness, style = edgeLine)
        } else {
            drawPath(path, STRAND, alpha = (0.07f + 0.43f * edge) * sheet.brightness, style = hairline)
        }
    }
}

private fun DrawScope.traceStrand(path: Path, sheet: Sheet, t: Float, f: Float) {
    path.moveTo(0f, strandY(sheet, 0f, t, f))
    for (i in 1..SAMPLES) {
        val u = i / SAMPLES.toFloat()
        path.lineTo(u * size.width, strandY(sheet, u, t, f))
    }
}

private fun DrawScope.strandY(sheet: Sheet, u: Float, t: Float, f: Float): Float =
    size.height * (sheet.middle(u, t) + f * sheet.span(u, t) + sheet.ripple(u, t, f))

private fun DrawScope.drawSparkle(sparkle: Sparkle, t: Float) {
    val u = (sparkle.start + sparkle.drift * t) % 1f
    // Fade in and out at the screen's edges, so wrapping around from right to left is never seen.
    val edgeFade = minOf(1f, u / 0.06f, (1f - u) / 0.06f)
    val glow = 0.5f + 0.5f * sin(sparkle.twinkle * t + sparkle.phase)
    // Squared: most of the time a sparkle is dim, and it peaks only briefly - a twinkle, not a pulse.
    val alpha = glow * glow * edgeFade
    if (alpha < 0.01f) return

    val x = u * size.width
    val y = size.height * (
        FRONT.middle(u, t) + sparkle.offset + 0.03f * sin(sparkle.bob * t + sparkle.phase)
        )
    val radius = sparkle.radius.dp.toPx() * (0.6f + 0.4f * glow)
    // Drawn in units of the sparkle's radius, so the brushes above serve every sparkle unchanged.
    withTransform({
        translate(x, y)
        scale(radius, radius, Offset.Zero)
    }) {
        drawCircle(SPARKLE_HALO, radius = HALO, center = Offset.Zero, alpha = alpha)
        if (sparkle.glints) {
            val glint = alpha * glow
            drawLine(GLINT_ACROSS, Offset(-GLINT, 0f), Offset(GLINT, 0f), strokeWidth = 0.35f, alpha = glint)
            drawLine(GLINT_DOWN, Offset(0f, -GLINT), Offset(0f, GLINT), strokeWidth = 0.35f, alpha = glint)
        }
    }
}
