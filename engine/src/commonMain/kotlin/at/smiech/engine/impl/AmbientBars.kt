package at.smiech.engine.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.exp
import kotlin.math.min
import kotlin.time.TimeSource

/**
 * Lights the bars beside the frame with the colors at its edges, like a video player's ambient
 * mode; see [at.smiech.engine.DisplayMode.AMBIENT].
 *
 * A strip along each edge that faces a bar is averaged into [BANDS] colors, so the light follows
 * what is on screen. Each bar is painted in those colors and darkened from the frame's edge
 * outward, so it reads as light spilling from the frame rather than a blurry copy of the game.
 *
 * The frame is drawn straight into the window, leaving nothing to read back, so the strips are
 * drawn a second time into a picture of their own: at the frame's size, on the CPU, and only the
 * strips. Readings are [SAMPLE_SECONDS] apart rather than every frame, and the colors ease toward
 * each one, so a shot or explosion passing an edge warms its bar instead of strobing it.
 *
 * Holds the easing between frames, so one instance belongs to one surface.
 */
class AmbientBars {
    /** The eased colors along each edge, as RGB triples from 0 to 1: left, right, top, bottom. */
    private val edges = Array(4) { FloatArray(BANDS * 3) }

    /** The last reading of each edge, which the eased colors head for. */
    private val readings = Array(4) { FloatArray(BANDS * 3) }
    private var strip = IntArray(0)
    private var lastFrame: TimeSource.Monotonic.ValueTimeMark? = null
    private var lastSample: TimeSource.Monotonic.ValueTimeMark? = null

    /** Where the strips are drawn to be read, made at the frame's size on the first reading. */
    private var picture: ImageBitmap? = null
    private var pictureCanvas: Canvas? = null
    private val strips = Path()

    /** Lights the bars around [fit] with the edges of the frame [graphics] last recorded. */
    fun draw(scope: DrawScope, graphics: ComposeGraphics, fit: FrameFit) {
        val sides = fit.left > 0
        val caps = fit.top > 0
        if (!sides && !caps) return

        val now = TimeSource.Monotonic.markNow()
        val sinceSample = lastSample?.let { (now - it).inWholeMicroseconds / 1_000_000f }
        if (sinceSample == null || sinceSample >= SAMPLE_SECONDS) {
            sample(graphics, sides, caps)
            lastSample = now
        }
        // The first frame takes its reading as is: easing in from black would light the bars a beat
        // after the game appeared, which looks like a glitch.
        val ease = lastFrame?.let { easeFor((now - it).inWholeMicroseconds / 1_000_000f) } ?: 1f
        lastFrame = now
        for (edge in edges.indices) {
            val eased = edges[edge]
            val reading = readings[edge]
            for (i in eased.indices) eased[i] += (reading[i] - eased[i]) * ease
        }

        val right = fit.left + fit.width
        val bottom = fit.top + fit.height
        with(scope) {
            if (sides) {
                bar(
                    LEFT,
                    Offset(0f, 0f),
                    Size(fit.left.toFloat(), size.height),
                    fit,
                    glowTowardsEnd = true
                )
                bar(
                    RIGHT,
                    Offset(right.toFloat(), 0f),
                    Size(size.width - right, size.height),
                    fit,
                    glowTowardsEnd = false
                )
            }
            if (caps) {
                bar(
                    TOP,
                    Offset(0f, 0f),
                    Size(size.width, fit.top.toFloat()),
                    fit,
                    glowTowardsEnd = true
                )
                bar(
                    BOTTOM,
                    Offset(0f, bottom.toFloat()),
                    Size(size.width, size.height - bottom),
                    fit,
                    glowTowardsEnd = false
                )
            }
        }
    }

    /** Draws the strips of the frame that face a bar into [picture], and reads each one. */
    private fun sample(graphics: ComposeGraphics, sides: Boolean, caps: Boolean) {
        val width = graphics.width
        val height = graphics.height
        val image = picture?.takeIf { it.width == width && it.height == height }
            ?: ImageBitmap(width, height).also {
                picture = it
                pictureCanvas = Canvas(it)
            }
        val canvas = pictureCanvas ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        val depth = STRIP.toFloat()
        strips.rewind()
        if (sides) {
            strips.addRect(Rect(0f, 0f, depth, h))
            strips.addRect(Rect(w - depth, 0f, w, h))
        }
        if (caps) {
            strips.addRect(Rect(0f, 0f, w, depth))
            strips.addRect(Rect(0f, h - depth, w, h))
        }
        graphics.drawInto(canvas, width, height, clip = strips)

        if (sides) {
            read(image, LEFT)
            read(image, RIGHT)
        }
        if (caps) {
            read(image, TOP)
            read(image, BOTTOM)
        }
    }

    /** Reads the strip along one edge of [image] into that edge's reading. */
    private fun read(image: ImageBitmap, edge: Int) {
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
        averageBands(strip, width, height, BANDS, alongY = vertical, into = readings[edge])
    }

    /**
     * Paints one bar: its edge's colors laid along it, then darkened from the frame's edge out to
     * the screen's, lightest where it touches the game.
     *
     * [glowTowardsEnd] says where the frame is: at the bar's far end (the left and top bars, which
     * come before the frame) or its near end (the right and bottom ones).
     */
    private fun DrawScope.bar(
        edge: Int,
        topLeft: Offset,
        size: Size,
        fit: FrameFit,
        glowTowardsEnd: Boolean
    ) {
        if (size.width <= 0f || size.height <= 0f) return
        val vertical = edge == LEFT || edge == RIGHT
        val rgb = edges[edge]
        // Each band's color at the middle of the stretch of edge it was read from.
        val stops = Array(BANDS) { i ->
            (i + 0.5f) / BANDS to Color(rgb[i * 3], rgb[i * 3 + 1], rgb[i * 3 + 2])
        }
        val light = if (vertical) {
            Brush.verticalGradient(
                *stops,
                startY = fit.top.toFloat(),
                endY = (fit.top + fit.height).toFloat()
            )
        } else {
            Brush.horizontalGradient(
                *stops,
                startX = fit.left.toFloat(),
                endX = (fit.left + fit.width).toFloat()
            )
        }
        drawRect(light, topLeft, size)

        val outer = Color.Black.copy(alpha = OUTER_SHADE)
        val inner = Color.Black.copy(alpha = INNER_SHADE)
        val (from, to) = if (glowTowardsEnd) outer to inner else inner to outer
        val shade = if (vertical) {
            Brush.horizontalGradient(
                listOf(from, to),
                startX = topLeft.x,
                endX = topLeft.x + size.width
            )
        } else {
            Brush.verticalGradient(
                listOf(from, to),
                startY = topLeft.y,
                endY = topLeft.y + size.height
            )
        }
        drawRect(shade, topLeft, size)
    }

    companion object {
        /** How many colors each edge is read as: few enough that the bars stay soft light. */
        const val BANDS = 10

        /** How deep into the frame an edge is read, in frame pixels. */
        const val STRIP = 12

        /**
         * Seconds between readings. Short against [EASE_SECONDS], so a bar never jumps, and long
         * enough that a reading costs a small slice of about every third frame.
         */
        const val SAMPLE_SECONDS = 0.05f

        /** Seconds for the bars to cover most of the way to a new reading. */
        const val EASE_SECONDS = 0.2f

        /** How dark a bar is where it meets the game, and at the screen's edge. */
        const val INNER_SHADE = 0.25f
        const val OUTER_SHADE = 0.82f

        private const val LEFT = 0
        private const val RIGHT = 1
        private const val TOP = 2
        private const val BOTTOM = 3

        /**
         * How far to ease toward a new reading after [seconds], at the same rate at any frame rate.
         */
        internal fun easeFor(seconds: Float): Float = 1f - exp(-seconds / EASE_SECONDS)
    }
}

/**
 * Averages a [width] x [height] block of ARGB [pixels] into [bands] colors, as RGB triples from 0 to
 * 1 written to [into] - down the block when [alongY], across it otherwise.
 */
internal fun averageBands(
    pixels: IntArray,
    width: Int,
    height: Int,
    bands: Int,
    alongY: Boolean,
    into: FloatArray
) {
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
                val pixel =
                    if (alongY) pixels[along * width + across] else pixels[across * width + along]
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
