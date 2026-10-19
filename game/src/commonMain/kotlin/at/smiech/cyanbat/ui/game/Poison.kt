package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.util.POISON_BREATH
import at.smiech.cyanbat.util.POISON_BREATHS_PER_SECOND
import at.smiech.cyanbat.util.POISON_CHOP_PER_SECOND
import at.smiech.cyanbat.util.POISON_CHOP_ROWS
import at.smiech.cyanbat.util.POISON_CHOP_SHARE
import at.smiech.cyanbat.util.POISON_CURE_PER_SECOND
import at.smiech.cyanbat.util.POISON_EDGE
import at.smiech.cyanbat.util.POISON_EDGE_BAND
import at.smiech.cyanbat.util.POISON_EDGE_BANDS
import at.smiech.cyanbat.util.POISON_GREEN
import at.smiech.cyanbat.util.POISON_ONSET
import at.smiech.cyanbat.util.POISON_REST
import at.smiech.cyanbat.util.POISON_RIPPLE_PER_SECOND
import at.smiech.cyanbat.util.POISON_RIPPLE_PIXELS
import at.smiech.cyanbat.util.POISON_RIPPLE_ROWS
import at.smiech.cyanbat.util.POISON_SETTLE_PER_SECOND
import at.smiech.cyanbat.util.POISON_SURGE
import at.smiech.cyanbat.util.POISON_WASH
import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Sky Viper's venom in the bat, and how it shows: the run's frame rippling sideways, a row at a
 * time, and washed green - a little for as long as the bat is poisoned, and more for a moment as the
 * bite goes in and at every blow after it.
 *
 * It costs the bat no health. What it takes is a clear view: a fight read through a swimming,
 * sickly picture. Only the run is drawn through it; the HUD over it stays clear.
 *
 * Everything here runs on the run's ticks, so a pause holds it.
 */
class Poison {
    /** How strongly it shows, 0..1. */
    var strength = 0f
        private set

    /** What it settles back to: [POISON_REST] while the bat is poisoned, nothing once it is cured. */
    private var rest = 0f

    /** Seconds it has been showing, which its ripple rolls and its wash breathes on. */
    private var clock = 0f

    /** Each row's shift, made once and reused; see [ripple]. */
    private var offsets = IntArray(0)

    /** Whether the bat is poisoned: from the bite until the viper falls. */
    val poisoned: Boolean get() = rest > 0f

    /** Whether there is anything to draw: while poisoned, and while it lifts after. */
    val showing: Boolean get() = strength > 0f

    /** The bite: the venom going in, at its strongest, to settle from there. */
    fun take() {
        rest = POISON_REST
        strength = POISON_ONSET
    }

    /** A blow taken while poisoned, which stirs the venom up for a moment. */
    fun surge() {
        if (poisoned) strength = (strength + POISON_SURGE).coerceAtMost(1f)
    }

    /** The viper is down: the poison lifts, quickly but not at once. */
    fun cure() {
        rest = 0f
    }

    /** One tick: settling toward where it rests, or lifting. */
    fun update(deltaTime: Float) {
        if (!showing) return
        clock += deltaTime
        val rate = if (poisoned) POISON_SETTLE_PER_SECOND else POISON_CURE_PER_SECOND
        strength = (strength - rate * deltaTime).coerceAtLeast(rest)
    }

    /**
     * How far each of the frame's [rows] is shifted now, in whole frame pixels; see
     * [Graphics.drawWarped]. A long wave rolling up the frame with a short one rolling down
     * against it, both as tall as the poison is strong, rounded to the grid: at its rest a pixel
     * either way at most, a few at its strongest.
     */
    fun ripple(rows: Int): IntArray {
        if (offsets.size != rows) offsets = IntArray(rows)
        val reach = strength * POISON_RIPPLE_PIXELS
        val wave = clock * POISON_RIPPLE_PER_SECOND
        val chop = clock * POISON_CHOP_PER_SECOND
        for (y in 0 until rows) {
            val shift = (1f - POISON_CHOP_SHARE) * sin(TWO_PI * (y / POISON_RIPPLE_ROWS + wave)) +
                    POISON_CHOP_SHARE * sin(TWO_PI * (y / POISON_CHOP_ROWS - chop))
            offsets[y] = (reach * shift).roundToInt()
        }
        return offsets
    }

    /**
     * The green over the run: a thin wash over the whole frame, slowly breathing, and more of it
     * gathered at the edges in bands, so the frame closes in a little. Bands rather than a gradient,
     * since everything but text is drawn on the grid.
     */
    fun drawWash(g: Graphics, width: Int, height: Int) {
        val breath = 1f + POISON_BREATH * sin(TWO_PI * clock * POISON_BREATHS_PER_SECOND)
        g.drawRect(
            0,
            0,
            width,
            height,
            EngineColors.withAlpha(POISON_GREEN, strength * POISON_WASH * breath)
        )
        for (band in 0 until POISON_EDGE_BANDS) {
            val share = 1f - band / POISON_EDGE_BANDS.toFloat()
            val color =
                EngineColors.withAlpha(POISON_GREEN, strength * POISON_EDGE * share * breath)
            val inset = band * POISON_EDGE_BAND
            val across = width - 2 * inset
            val down = height - 2 * inset - 2 * POISON_EDGE_BAND
            g.drawRect(inset, inset, across, POISON_EDGE_BAND, color)
            g.drawRect(inset, height - inset - POISON_EDGE_BAND, across, POISON_EDGE_BAND, color)
            g.drawRect(inset, inset + POISON_EDGE_BAND, POISON_EDGE_BAND, down, color)
            g.drawRect(
                width - inset - POISON_EDGE_BAND,
                inset + POISON_EDGE_BAND,
                POISON_EDGE_BAND,
                down,
                color
            )
        }
    }

    private companion object {
        const val TWO_PI = (2.0 * PI).toFloat()
    }
}
