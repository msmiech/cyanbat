package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.ui.game.ComboHeat.supernova
import at.smiech.cyanbat.util.COMBO_COUNT_GROWTH
import at.smiech.cyanbat.util.COMBO_COUNT_MAX_SIZE
import at.smiech.cyanbat.util.COMBO_FONT_SIZE
import at.smiech.cyanbat.util.COMBO_MIN_FLAME_STRENGTH
import at.smiech.cyanbat.util.COMBO_SUPERNOVA_CYCLES_PER_SECOND
import at.smiech.engine.EngineColors
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * How hot the combo readout burns at a given multiplier: what it calls itself, the color it is
 * drawn in, how big its count is and how tall the fire behind it stands.
 *
 * The multiplier has no ceiling, so neither does this. It climbs a ladder of named rungs, each a
 * hotter fire than the last - yellow, orange, red, crimson, violet, then blue and white, the way a
 * flame gets hotter - and every step between two rungs moves the color part of the way to the next,
 * so each step the player earns shows. Past the top rung the fire cycles through every color on the
 * ladder, faster the higher the streak climbs, so a streak that is still climbing always looks it.
 *
 * The top of the ladder coming round to blue and white is not only physics: the bat is the cool
 * thing on screen, and its hottest streak burns in its own colors.
 *
 * A pure function of the multiplier, and past the top rung of the clock, so the whole ladder can be
 * tested without drawing any of it.
 */
internal object ComboHeat {

    /** The three colors a fire is drawn from; see [color], [emberColor] and [coreColor]. */
    private enum class Part { COLOR, EMBER, CORE }

    /**
     * One rung: the multiplier it starts at, what the readout calls itself there, and its colors -
     * [color] for the text and the body of the flames, [ember] for their cooling tips, and [core]
     * for the heart they rise out of, which on the warm rungs is yellower than plain white, the way
     * a real fire's is.
     */
    private class Rung(val from: Int, val title: String, val color: Int, val ember: Int, val core: Int) {
        fun of(part: Part): Int = when (part) {
            Part.COLOR -> color
            Part.EMBER -> ember
            Part.CORE -> core
        }
    }

    /**
     * The first rung is no fire at all: at x1 the readout is a plain line of the HUD, cyan like the
     * rest of it, and shouts only once there is something to shout about. The steps between rungs
     * widen as they climb, since each step takes the same three kills and the long streaks are the
     * ones worth marking out.
     */
    private val RUNGS = arrayOf(
        Rung(1, "Combo:", EngineColors.CYAN, EngineColors.CYAN, EngineColors.CYAN),
        Rung(2, "HOT", 0xFFFFE45A.toInt(), 0xFFD04A10.toInt(), 0xFFFFFBE0.toInt()),
        Rung(4, "BLAZING", 0xFFFFA02A.toInt(), 0xFFB0200C.toInt(), 0xFFFFF0A0.toInt()),
        Rung(6, "SCORCHING", 0xFFFF5A2A.toInt(), 0xFF8A0A10.toInt(), 0xFFFFD050.toInt()),
        Rung(9, "INFERNO", 0xFFFF3A6E.toInt(), 0xFF7A0838.toInt(), 0xFFFFD27A.toInt()),
        Rung(13, "HELLFIRE", 0xFFD24BFF.toInt(), 0xFF4A0C8A.toInt(), 0xFFF6D8FF.toInt()),
        Rung(18, "BLUE FLAME", 0xFF5AA0FF.toInt(), 0xFF1A2A9A.toInt(), 0xFFD8F4FF.toInt()),
        Rung(25, "WHITE HOT", 0xFFEAF8FF.toInt(), 0xFF3A7AE0.toInt(), EngineColors.WHITE),
    )

    /** Where the named rungs run out, and the colors start to cycle through all of them. */
    const val SUPERNOVA = 35

    private const val SUPERNOVA_TITLE = "SUPERNOVA"

    /** Which rung [multiplier] is on, counting the one past the ladder's end; a change is a new title. */
    fun rung(multiplier: Int): Int {
        if (multiplier >= SUPERNOVA) return RUNGS.size
        var index = 0
        while (index < RUNGS.lastIndex && multiplier >= RUNGS[index + 1].from) index++
        return index
    }

    /** What the readout calls itself at [multiplier], in front of the count. */
    fun title(multiplier: Int): String {
        val index = rung(multiplier)
        return if (index == RUNGS.size) SUPERNOVA_TITLE else RUNGS[index].title
    }

    /** Whether [multiplier] is worth setting alight at all. */
    fun burning(multiplier: Int): Boolean = multiplier > 1

    /** The text's color, and the body of the flames', at [multiplier]; [seconds] cycles it past the ladder. */
    fun color(multiplier: Int, seconds: Float): Int = blend(multiplier, seconds, Part.COLOR)

    /** The color the flames cool to at their tips. */
    fun emberColor(multiplier: Int, seconds: Float): Int = blend(multiplier, seconds, Part.EMBER)

    /** The color of the hottest part of the fire, where it rises off the letters. */
    fun coreColor(multiplier: Int, seconds: Float): Int = blend(multiplier, seconds, Part.CORE)

    /**
     * The count's font size. Grows by the doubling rather than by the step, so the early steps, which
     * every run sees, are the ones that visibly swell - and capped, so a legendary streak still fits
     * in the corner of the frame it has to share with the cave.
     */
    fun countSize(multiplier: Int): Int {
        val growth = (COMBO_COUNT_GROWTH * log2(multiplier)).roundToInt()
        return (COMBO_FONT_SIZE + growth).coerceAtMost(COMBO_COUNT_MAX_SIZE)
    }

    /**
     * How tall the fire stands, as 0..1: nothing at x1, a lick of flame on the first step, and full
     * height at [SUPERNOVA]. Also by the doubling, for the same reason as [countSize].
     */
    fun flameStrength(multiplier: Int): Float {
        if (!burning(multiplier)) return 0f
        return (log2(multiplier) / log2(SUPERNOVA)).coerceIn(COMBO_MIN_FLAME_STRENGTH, 1f)
    }

    /**
     * Walks from the rung [multiplier] is on toward the next, by how far between the two it is.
     * The last named rung walks into [supernova], so the ladder has no seam at its top.
     */
    private fun blend(multiplier: Int, seconds: Float, part: Part): Int {
        val index = rung(multiplier)
        if (index == RUNGS.size) return supernova(multiplier, seconds, part)
        val rung = RUNGS[index]
        val from = rung.of(part)
        val nextFrom: Int
        val to: Int
        if (index == RUNGS.lastIndex) {
            nextFrom = SUPERNOVA
            to = supernova(SUPERNOVA, seconds, part)
        } else {
            val next = RUNGS[index + 1]
            nextFrom = next.from
            to = next.of(part)
        }
        return mix(from, to, (multiplier - rung.from).toFloat() / (nextFrom - rung.from))
    }

    /**
     * The colors past the ladder: every fire on it in turn, from the first yellow up to white and
     * back down, over and over, faster with every doubling of the multiplier past [SUPERNOVA].
     *
     * Back and forth rather than round, because white straight back to yellow would be a jump; and
     * the ladder's colors rather than the whole wheel, because a hue wheel passes through green,
     * and a green fire reads as poison rather than as heat.
     */
    private fun supernova(multiplier: Int, seconds: Float, part: Part): Int {
        val speed = COMBO_SUPERNOVA_CYCLES_PER_SECOND * (1f + log2(multiplier.toFloat() / SUPERNOVA))
        val cycle = seconds * speed
        val phase = cycle - floor(cycle)
        val segments = RUNGS.size - 2
        val along = (if (phase < 0.5f) phase * 2f else 2f - phase * 2f) * segments
        val index = along.toInt().coerceAtMost(segments - 1)
        val from = RUNGS[1 + index]
        val to = RUNGS[2 + index]
        return mix(from.of(part), to.of(part), along - index)
    }
}

private fun log2(value: Int): Float = log2(value.toFloat())

/**
 * [from] toward [to] by [t], opaque. [EngineColors.lerp] does the same with alpha, but builds an
 * array a call, and the combo readout blends colors a glyph at a time, every frame.
 */
internal fun mix(from: Int, to: Int, t: Float): Int {
    val amount = t.coerceIn(0f, 1f)
    val r = mixChannel(from ushr 16, to ushr 16, amount)
    val g = mixChannel(from ushr 8, to ushr 8, amount)
    val b = mixChannel(from, to, amount)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun mixChannel(from: Int, to: Int, t: Float): Int {
    val a = from and 0xFF
    val b = to and 0xFF
    return (a + (b - a) * t).roundToInt()
}
