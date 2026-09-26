package at.smiech.engine.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.exp
import kotlin.math.min
import kotlin.time.TimeSource

/**
 * Lights the bars beside the framebuffer with whatever is at its edges, the way a video player's
 * ambient mode does - see [at.smiech.engine.DisplayMode.AMBIENT].
 *
 * Every frame it reads a strip along each edge that faces a bar and averages it into [BANDS]
 * colors, so the light follows what is on screen: the pale rock of an obstacle scrolling in lifts
 * the bar on its side, and the forest's greens tint the sides green. Each bar is painted in those
 * colors and graded from the frame's edge out into the dark, so it reads as light spilling out of
 * the frame rather than as a second, blurrier copy of the game competing with the first.
 *
 * The colors ease toward each new reading over a fraction of a second rather than jumping to it,
 * so a shot or an explosion flashing past an edge warms its bar instead of strobing it.
 *
 * Holds that easing between frames, so one instance belongs to one surface.
 */
class AmbientBars {
    /** The eased colors along each edge, as RGB triples from 0 to 1: left, right, top, bottom. */
    private val edges = Array(4) { FloatArray(BANDS * 3) }
    private val reading = FloatArray(BANDS * 3)
    private var strip = IntArray(0)
    private var lastFrame: TimeSource.Monotonic.ValueTimeMark? = null

    fun draw(scope: DrawScope, image: ImageBitmap, fit: FrameFit) {
        val sides = fit.left > 0
        val caps = fit.top > 0
        if (!sides && !caps) return

        val now = TimeSource.Monotonic.markNow()
        // The first frame takes its reading as it is: easing in from black would light the bars up
        // a beat after the game appeared, which reads as a glitch rather than as ambience.
        val ease = lastFrame?.let { easeFor((now - it).inWholeMicroseconds / 1_000_000f) } ?: 1f
        lastFrame = now

        val right = fit.left + fit.width
        val bottom = fit.top + fit.height
        with(scope) {
            if (sides) {
                sample(image, LEFT, ease)
                sample(image, RIGHT, ease)
                bar(LEFT, Offset(0f, 0f), Size(fit.left.toFloat(), size.height), fit, glowTowardsEnd = true)
                bar(RIGHT, Offset(right.toFloat(), 0f), Size(size.width - right, size.height), fit, glowTowardsEnd = false)
            }
            if (caps) {
                sample(image, TOP, ease)
                sample(image, BOTTOM, ease)
                bar(TOP, Offset(0f, 0f), Size(size.width, fit.top.toFloat()), fit, glowTowardsEnd = true)
                bar(BOTTOM, Offset(0f, bottom.toFloat()), Size(size.width, size.height - bottom), fit, glowTowardsEnd = false)
            }
        }
    }

    /** Reads the strip along one edge of [image] and eases that edge's colors toward it. */
    private fun sample(image: ImageBitmap, edge: Int, ease: Float) {
        val vertical = edge == LEFT || edge == RIGHT
        val depth = min(STRIP, if (vertical) image.width else image.height)
        val width = if (vertical) depth else image.width
        val height = if (vertical) image.height else depth
        if (strip.size < width * height) strip = IntArray(width * height)
        image.readPixels(
            strip,
            startX = if (edge == RIGHT) image.width - depth else 0,
            startY = if (edge == BOTTOM) image.height - depth else 0,
            width = width,
            height = height,
        )
        averageBands(strip, width, height, BANDS, alongY = vertical, into = reading)
        val eased = edges[edge]
        for (i in eased.indices) eased[i] += (reading[i] - eased[i]) * ease
    }

    /**
     * Paints one bar: its edge's colors laid along it where the frame is beside it, then darkened
     * from the frame's edge out to the screen's - lightest where it touches the game.
     *
     * [glowTowardsEnd] is which way the frame is: toward the bar's far end (the left and top bars,
     * which sit before the frame) or its near end (the right and bottom ones).
     */
    private fun DrawScope.bar(edge: Int, topLeft: Offset, size: Size, fit: FrameFit, glowTowardsEnd: Boolean) {
        if (size.width <= 0f || size.height <= 0f) return
        val vertical = edge == LEFT || edge == RIGHT
        val rgb = edges[edge]
        // Each band's color at the middle of the stretch of edge it was read from.
        val stops = Array(BANDS) { i ->
            (i + 0.5f) / BANDS to Color(rgb[i * 3], rgb[i * 3 + 1], rgb[i * 3 + 2])
        }
        val light = if (vertical) {
            Brush.verticalGradient(*stops, startY = fit.top.toFloat(), endY = (fit.top + fit.height).toFloat())
        } else {
            Brush.horizontalGradient(*stops, startX = fit.left.toFloat(), endX = (fit.left + fit.width).toFloat())
        }
        drawRect(light, topLeft, size)

        val outer = Color.Black.copy(alpha = OUTER_SHADE)
        val inner = Color.Black.copy(alpha = INNER_SHADE)
        val (from, to) = if (glowTowardsEnd) outer to inner else inner to outer
        val shade = if (vertical) {
            Brush.horizontalGradient(listOf(from, to), startX = topLeft.x, endX = topLeft.x + size.width)
        } else {
            Brush.verticalGradient(listOf(from, to), startY = topLeft.y, endY = topLeft.y + size.height)
        }
        drawRect(shade, topLeft, size)
    }

    companion object {
        /** How many colors each edge is read as. Few enough that the bars stay soft light, not a picture. */
        const val BANDS = 10

        /** How deep into the frame an edge is read, in framebuffer pixels. */
        const val STRIP = 12

        /** Seconds for the bars to cover most of the way to a new reading. */
        const val EASE_SECONDS = 0.2f

        /** How dark a bar is where it meets the game, and at the screen's edge. */
        const val INNER_SHADE = 0.25f
        const val OUTER_SHADE = 0.82f

        private const val LEFT = 0
        private const val RIGHT = 1
        private const val TOP = 2
        private const val BOTTOM = 3

        /** How far to ease toward a new reading after [seconds] - the same rate whatever the frame rate. */
        internal fun easeFor(seconds: Float): Float = 1f - exp(-seconds / EASE_SECONDS)
    }
}

/**
 * Averages a [width] x [height] block of ARGB [pixels] into [bands] colors, as RGB triples from 0 to
 * 1 written to [into] - down the block when [alongY], across it otherwise.
 */
internal fun averageBands(pixels: IntArray, width: Int, height: Int, bands: Int, alongY: Boolean, into: FloatArray) {
    val length = if (alongY) height else width
    for (band in 0 until bands) {
        val from = band * length / bands
        val until = maxOf(from + 1, (band + 1) * length / bands)
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (along in from until until) {
            for (across in 0 until (if (alongY) width else height)) {
                val pixel = if (alongY) pixels[along * width + across] else pixels[across * width + along]
                r += (pixel shr 16) and 0xFF
                g += (pixel shr 8) and 0xFF
                b += pixel and 0xFF
                count++
            }
        }
        into[band * 3] = r / (255f * count)
        into[band * 3 + 1] = g / (255f * count)
        into[band * 3 + 2] = b / (255f * count)
    }
}
