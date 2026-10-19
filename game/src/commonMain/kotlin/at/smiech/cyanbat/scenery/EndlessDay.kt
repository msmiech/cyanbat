package at.smiech.cyanbat.scenery

import at.smiech.cyanbat.scenery.EndlessDay.HIGHEST
import at.smiech.cyanbat.scenery.EndlessDay.SKY
import at.smiech.cyanbat.scenery.EndlessDay.ambient
import at.smiech.cyanbat.util.ENDLESS_NIGHT_AMBIENT
import at.smiech.engine.EngineColors
import at.smiech.engine.ecs.LightingSystem
import kotlin.math.PI
import kotlin.math.sin

/**
 * The endless stage's day, which goes round and round: dawn, noon, the golden hour, sunset, a
 * moonlit night, and dawn again, every [SECONDS] of the run. The stages' days run once, from their
 * start to their boss ([Day]); this one never ends, so everything in it is a function of how far
 * round the clock it is, [position], which wraps from 1 back to 0.
 *
 * The sun crosses the sky from the right, where it rises ahead of the bat, to the left, where it
 * sets behind it; the moon follows it across the night. So the land is lit from the right in the
 * morning, from above at noon and from the left toward evening, which the palettes of [TimeOfDay]
 * carry.
 *
 * The ground is drawn in one palette per [TimeOfDay] and crossfaded between them along
 * [KEYFRAMES], as the stages' scenery is; the sky is computed.
 */
object EndlessDay {

    /** How long a whole day takes, in seconds of the run. */
    const val SECONDS = 270f

    /** Where the run starts on the clock: just after sunrise, the sun clear of the horizon. */
    const val START = 0.06f

    /** Where round the clock the run is, [elapsedSeconds] in: 0..1, wrapping. */
    fun position(elapsedSeconds: Float): Float {
        val p = (START + elapsedSeconds / SECONDS) % 1f
        return if (p < 0f) p + 1f else p
    }

    /**
     * The palettes the ground is drawn in, a row of its sheet apiece, in this order: each a light
     * falling from a direction, in a color, over an ambient.
     *
     * @param light the color of the direct light, sun or moon.
     * @param ambient the color of the light from the sky alone, which the shadowed sides get.
     * @param fromLeft how strongly the direct light reaches a side facing left.
     * @param fromRight likewise a side facing right.
     * @param fromAbove likewise a flat top.
     * @param haze how far the distance fades into the horizon's color: thicker with a low sun.
     */
    enum class TimeOfDay(
        val light: Int,
        val ambient: Int,
        val fromLeft: Float,
        val fromRight: Float,
        val fromAbove: Float,
        val haze: Float,
    ) {
        /** The sun just up on the right, pink and low. */
        DAWN(
            0xFFFFB4A0.toInt(),
            0xFF7A6A9A.toInt(),
            fromLeft = 0.1f,
            fromRight = 1f,
            fromAbove = 0.45f,
            haze = 1.15f
        ),

        /** The sun high: tops brightest, both sides lit. */
        NOON(
            0xFFFFFCF0.toInt(),
            0xFF9AA8C0.toInt(),
            fromLeft = 0.55f,
            fromRight = 0.6f,
            fromAbove = 1f,
            haze = 1f
        ),

        /** The sun low on the left, gold. */
        GOLDEN(
            0xFFFFD08A.toInt(),
            0xFF8C88A8.toInt(),
            fromLeft = 1f,
            fromRight = 0.15f,
            fromAbove = 0.5f,
            haze = 1.1f
        ),

        /** The sun going down behind the bat, red, under a purple sky. */
        SUNSET(
            0xFFFF8A60.toInt(),
            0xFF6A5080.toInt(),
            fromLeft = 1f,
            fromRight = 0.05f,
            fromAbove = 0.3f,
            haze = 1.2f
        ),

        /**
         * Moonlight: blue, from above and the left. Brighter than a night really is, since the
         * stage's dark falls over it as well; see [ambient].
         */
        NIGHT(
            0xFFA8BCF0.toInt(),
            0xFF56628C.toInt(),
            fromLeft = 0.6f,
            fromRight = 0.15f,
            fromAbove = 0.6f,
            haze = 0.8f
        ),
    }

    /**
     * Where each palette is at full strength, round the clock, and which: the ground crossfades
     * from each entry to the next, wrapping from the last to the first. A palette named twice in a
     * row is held between its two entries.
     */
    val KEYFRAMES = floatArrayOf(0f, 0.2f, 0.42f, 0.53f, 0.62f, 0.72f, 0.92f)
    val KEYFRAME_TIMES = arrayOf(
        TimeOfDay.DAWN, TimeOfDay.NOON, TimeOfDay.NOON, TimeOfDay.GOLDEN, TimeOfDay.SUNSET,
        TimeOfDay.NIGHT, TimeOfDay.NIGHT,
    )

    /** How many palettes the ground is drawn in, a row of its sheet apiece. */
    val ROWS: Int = TimeOfDay.entries.size

    /** The keyframe entry [position] is past: the one fading out. */
    private fun entryBelow(position: Float): Int {
        var index = KEYFRAMES.lastIndex
        for (i in KEYFRAMES.indices) if (position >= KEYFRAMES[i]) index = i
        return index
    }

    /** The palette row the ground is leaving at [position], drawn underneath. */
    fun rowBelow(position: Float): Int = KEYFRAME_TIMES[entryBelow(position)].ordinal

    /** The palette row fading in over [rowBelow]. */
    fun rowAbove(position: Float): Int =
        KEYFRAME_TIMES[(entryBelow(position) + 1) % KEYFRAMES.size].ordinal

    /** How far the ground has faded from [rowBelow] to [rowAbove], 0..1. */
    fun rowBlend(position: Float): Float {
        val below = entryBelow(position)
        val next = (below + 1) % KEYFRAMES.size
        if (KEYFRAME_TIMES[below] == KEYFRAME_TIMES[next]) return 0f
        val from = KEYFRAMES[below]
        val to = if (next == 0) 1f else KEYFRAMES[next]
        return smoothstep(from, to, position)
    }

    // --- the sky ---------------------------------------------------------------------------------

    private val SKY_STOPS = floatArrayOf(0f, 0.4f, 0.75f, 1f)

    /** Where each of [SKY] is at full strength round the clock; the last wraps into the first. */
    private val SKY_POSITIONS =
        floatArrayOf(0f, 0.08f, 0.2f, 0.42f, 0.52f, 0.6f, 0.66f, 0.74f, 0.9f, 0.96f, 1f)

    /**
     * The sky, zenith to horizon, round the clock: a rose dawn under violet, a morning clearing to
     * blue, a deep noon, gold in the afternoon, a burning sunset under purple, the dusk, a navy
     * night, and the night paling toward the next dawn.
     */
    private val SKY = arrayOf(
        colors(0x2E3A7A, 0x6E58A6, 0xE87AA0, 0xFFB88A), // dawn
        colors(0x4C86D0, 0x8AB0E6, 0xF0C8C8, 0xFFE0B8), // morning
        colors(0x3E9CE8, 0x76C0F2, 0xB4E0F8, 0xDCF2FC), // noon
        colors(0x4A98E0, 0x80BCEC, 0xC8E0F0, 0xF4ECD2), // afternoon
        colors(0x5A86C8, 0x9AA8D0, 0xF0C690, 0xFFD88A), // golden hour
        colors(0x4A3E7E, 0xA05878, 0xF07A50, 0xFFB050), // sunset
        colors(0x1E1E4A, 0x3E2A64, 0x8A3C6A, 0xD06A5A), // dusk
        colors(0x080C20, 0x101634, 0x1C2248, 0x2C3058), // night
        colors(0x080C20, 0x101634, 0x1C2248, 0x2C3058), // the dead of night
        colors(0x141A44, 0x2A2A62, 0x5A3A7C, 0x9A4E7E), // before the dawn
        colors(0x2E3A7A, 0x6E58A6, 0xE87AA0, 0xFFB88A), // dawn again
    )

    /** The sky's color at [position], [height] of the way down from the zenith to the horizon. */
    fun skyColor(position: Float, height: Float): Int =
        skyBetween(SKY_POSITIONS, SKY, SKY_STOPS, position, height)

    /** The color the distance fades into at [position]: the sky's at the horizon. */
    fun hazeColor(position: Float): Int = skyColor(position, 1f)

    // --- the sun ---------------------------------------------------------------------------------

    /** When the sun has set: it is up from 0 to here. */
    const val SUNSET = 0.64f

    private const val RISE_X = 600f
    private const val SET_X = 40f
    private const val HIGHEST = 44f

    /** How far across the sky the sun is, 0 rising to 1 set; past 1 it is down. */
    private fun sunAlong(position: Float): Float = position / SUNSET

    fun sunUp(position: Float): Boolean = position < SUNSET

    fun sunX(position: Float): Float =
        RISE_X + (SET_X - RISE_X) * sunAlong(position).coerceIn(0f, 1f)

    /** On an arc from the [horizonY] up to [HIGHEST] at its noon and down again. */
    fun sunY(position: Float, horizonY: Int): Float {
        val along = sunAlong(position).coerceIn(0f, 1f)
        return horizonY - (horizonY - HIGHEST) * sin(PI.toFloat() * along)
    }

    /** Larger near the horizon. */
    fun sunRadius(position: Float): Float {
        val low = 1f - sin(PI.toFloat() * sunAlong(position).coerceIn(0f, 1f))
        return 13f + 8f * low
    }

    private val SUN_POSITIONS = floatArrayOf(0f, 0.08f, 0.2f, 0.48f, 0.58f, SUNSET)
    private val SUN_COLORS = colors(0xFF6A8A, 0xFFB880, 0xFFFBE8, 0xFFF0B0, 0xFF9C4A, 0xF45E3C)

    fun sunColor(position: Float): Int = ramp(SUN_POSITIONS, SUN_COLORS, position)

    /** A halo strongest while the sun is low. */
    fun sunGlow(position: Float): Float {
        val low = 1f - sin(PI.toFloat() * sunAlong(position).coerceIn(0f, 1f))
        return 0.3f + 0.7f * low
    }

    /** How strongly a low sun lays its path of glints on the water, 0..1. */
    fun sunGlitter(position: Float): Float {
        if (!sunUp(position)) return 0f
        val along = sunAlong(position)
        return 1f - smoothstep(0.04f, 0.2f, along) + smoothstep(0.8f, 0.96f, along)
    }

    // --- the night -------------------------------------------------------------------------------

    /** When the moon rises behind the sunset, and when it has set before the dawn. */
    private const val MOONRISE = 0.63f
    private const val MOONSET = 0.98f

    /** How much of the moon shows, 0..1. */
    fun moonlight(position: Float): Float =
        smoothstep(MOONRISE, MOONRISE + 0.05f, position) * (1f - smoothstep(
            MOONSET - 0.05f,
            MOONSET,
            position
        ))

    private fun moonAlong(position: Float): Float = fraction(position, MOONRISE, MOONSET)

    /** The moon's top left corner, on an arc across the night like the sun's across the day. */
    fun moonX(position: Float): Float = RISE_X - 16f + (SET_X - RISE_X) * moonAlong(position)

    fun moonY(position: Float, horizonY: Int): Float =
        horizonY - 30f - (horizonY - 30f - HIGHEST) * sin(PI.toFloat() * moonAlong(position))

    /** How much of the night sky shows: none by day, every star by the dead of night. */
    fun starlight(position: Float): Float =
        smoothstep(0.63f, 0.74f, position) * (1f - smoothstep(0.93f, 0.99f, position))

    /**
     * How dark it is, 0 by day to 1 at the dead of night: the dusk brings it down, and the hour
     * before the dawn lifts it.
     */
    fun darkness(position: Float): Float =
        smoothstep(0.61f, 0.72f, position) * (1f - smoothstep(0.92f, 0.99f, position))

    /**
     * The light where nothing else reaches, for the stage's dark: daylight by day, the moonlit night
     * at the dead of night.
     */
    fun ambient(position: Float): Int {
        val dark = darkness(position)
        // Daylight exactly, not a hair under it, so the dark costs nothing by day.
        if (dark <= 0.002f) return LightingSystem.DAYLIGHT
        return EngineColors.lerp(LightingSystem.DAYLIGHT, ENDLESS_NIGHT_AMBIENT, dark)
    }
}
