package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.combo_blazing
import at.smiech.cyanbat.resources.combo_blue_flame
import at.smiech.cyanbat.resources.combo_cold
import at.smiech.cyanbat.resources.combo_hellfire
import at.smiech.cyanbat.resources.combo_hot
import at.smiech.cyanbat.resources.combo_inferno
import at.smiech.cyanbat.resources.combo_scorching
import at.smiech.cyanbat.resources.combo_supernova
import at.smiech.cyanbat.resources.combo_white_hot
import at.smiech.cyanbat.ui.game.ComboHeat.supernova
import at.smiech.cyanbat.util.COMBO_COUNT_GROWTH
import at.smiech.cyanbat.util.COMBO_COUNT_MAX_SIZE
import at.smiech.cyanbat.util.COMBO_FONT_SIZE
import at.smiech.cyanbat.util.COMBO_MIN_FLAME_STRENGTH
import at.smiech.cyanbat.util.COMBO_SUPERNOVA_CYCLES_PER_SECOND
import at.smiech.engine.EngineColors
import org.jetbrains.compose.resources.StringResource
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * How hot the combo readout burns at a given multiplier: its title, its color, the size of its
 * count and the height of the fire behind it.
 *
 * The multiplier has no ceiling, so neither does this. It climbs a ladder of named rungs, each a
 * hotter fire (yellow, orange, red, crimson, violet, then blue and white), and every step between
 * rungs moves the color part of the way to the next, so each earned step shows. Past the top rung
 * the fire cycles through the ladder's colors, faster as the streak climbs. Topping out in blue and
 * white also means the hottest streak burns in the bat's own cool colors.
 *
 * A pure function of the multiplier (and, past the top rung, of the clock), so the ladder can be
 * tested without drawing it.
 */
internal object ComboHeat {

    /** The three colors a fire is drawn from; see [color], [emberColor] and [coreColor]. */
    private enum class Part { COLOR, EMBER, CORE }

    /**
     * One rung: the multiplier it starts at, the readout's title there, and its colors: [color] for
     * the text and the body of the flames, [ember] for their cooling tips, and [core] for the heart
     * they rise from, yellower than white on the warm rungs as in a real fire.
     */
    private class Rung(
        val from: Int,
        val title: StringResource,
        val color: Int,
        val ember: Int,
        val core: Int
    ) {
        /** This rung's color for [part]. */
        fun of(part: Part): Int = when (part) {
            Part.COLOR -> color
            Part.EMBER -> ember
            Part.CORE -> core
        }
    }

    /**
     * The ladder. The first rung is no fire: at x1 the readout is a plain cyan HUD line. The gaps
     * between rungs widen as they climb, since every step takes the same three kills and the long
     * streaks are the ones worth marking.
     */
    private val RUNGS = arrayOf(
        Rung(1, Res.string.combo_cold, EngineColors.CYAN, EngineColors.CYAN, EngineColors.CYAN),
        Rung(2, Res.string.combo_hot, 0xFFFFE45A.toInt(), 0xFFD04A10.toInt(), 0xFFFFFBE0.toInt()),
        Rung(4, Res.string.combo_blazing, 0xFFFFA02A.toInt(), 0xFFB0200C.toInt(), 0xFFFFF0A0.toInt()),
        Rung(6, Res.string.combo_scorching, 0xFFFF5A2A.toInt(), 0xFF8A0A10.toInt(), 0xFFFFD050.toInt()),
        Rung(9, Res.string.combo_inferno, 0xFFFF3A6E.toInt(), 0xFF7A0838.toInt(), 0xFFFFD27A.toInt()),
        Rung(13, Res.string.combo_hellfire, 0xFFD24BFF.toInt(), 0xFF4A0C8A.toInt(), 0xFFF6D8FF.toInt()),
        Rung(18, Res.string.combo_blue_flame, 0xFF5AA0FF.toInt(), 0xFF1A2A9A.toInt(), 0xFFD8F4FF.toInt()),
        Rung(25, Res.string.combo_white_hot, 0xFFEAF8FF.toInt(), 0xFF3A7AE0.toInt(), EngineColors.WHITE),
    )

    /** Where the named rungs run out, and the colors start to cycle through all of them. */
    const val SUPERNOVA = 35

    /** The title past the ladder's end. */
    private val SUPERNOVA_TITLE = Res.string.combo_supernova

    /** Which rung [multiplier] is on, counting the one past the ladder's end; a change is a new title. */
    fun rung(multiplier: Int): Int {
        if (multiplier >= SUPERNOVA) return RUNGS.size
        var index = 0
        while (index < RUNGS.lastIndex && multiplier >= RUNGS[index + 1].from) index++
        return index
    }

    /** What the readout calls itself at [multiplier], in front of the count. */
    fun title(multiplier: Int): StringResource {
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
     * The count's font size. Grows per doubling rather than per step, so the early steps every run
     * sees visibly swell, and capped so a long streak still fits its corner of the frame.
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
     * Blends from the rung [multiplier] is on toward the next, by how far between them it is. The
     * last named rung blends into [supernova], so the ladder has no seam at its top.
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
     * back, faster with every doubling of the multiplier past [SUPERNOVA].
     *
     * Back and forth, because white straight back to yellow would jump; and the ladder's colors
     * rather than the whole hue wheel, which passes through green, and green fire reads as poison.
     */
    private fun supernova(multiplier: Int, seconds: Float, part: Part): Int {
        val speed =
            COMBO_SUPERNOVA_CYCLES_PER_SECOND * (1f + log2(multiplier.toFloat() / SUPERNOVA))
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

/** log2 of an Int, as a Float. */
private fun log2(value: Int): Float = log2(value.toFloat())

/**
 * [from] toward [to] by [t], opaque. [EngineColors.lerp] also blends alpha but allocates an array
 * per call, and the readout blends colors per glyph, every frame.
 */
internal fun mix(from: Int, to: Int, t: Float): Int {
    val amount = t.coerceIn(0f, 1f)
    val r = mixChannel(from ushr 16, to ushr 16, amount)
    val g = mixChannel(from ushr 8, to ushr 8, amount)
    val b = mixChannel(from, to, amount)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** One 8-bit channel of [mix]. */
private fun mixChannel(from: Int, to: Int, t: Float): Int {
    val a = from and 0xFF
    val b = to and 0xFF
    return (a + (b - a) * t).roundToInt()
}
