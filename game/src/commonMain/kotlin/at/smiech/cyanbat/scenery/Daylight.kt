package at.smiech.cyanbat.scenery

import at.smiech.cyanbat.scenery.Daylight.SKY
import at.smiech.cyanbat.scenery.Daylight.SKY_POSITIONS
import at.smiech.cyanbat.scenery.Daylight.fraction
import at.smiech.engine.EngineColors

/**
 * The desert's day, from noon at the stage's first second to night on its boss's: what the sky, the
 * sun, the moon and the stars look like at any point in between.
 *
 * Everything here is a pure function of one number, how far through its day the stage is - see
 * [position]. That is what lets the whole sunset be tested without drawing a pixel of it, and what
 * ties it to the stage clock: a paused game is a paused sunset, and a stage flown slowly gets no
 * darker for it than one flown fast.
 *
 * The day follows the waves. Noon for the first; the long afternoon through the second and third,
 * the light going gold; the sun going down in the fourth; dusk in the fifth, with the first stars;
 * and night - black and purple, the moon up and every star out - the moment the boss arrives.
 */
object Daylight {

    /** How far through its day a stage is, as 0..1: noon at its first second, night on its boss's. */
    fun position(elapsedSeconds: Float, bossTimeSeconds: Float): Float =
        if (bossTimeSeconds <= 0f) 1f else (elapsedSeconds / bossTimeSeconds).coerceIn(0f, 1f)

    // --- the ground ------------------------------------------------------------------------------

    /**
     * Where each of the ground's palettes is at full strength: noon, the golden hour, sunset and
     * night. Every piece of scenery a day-lit stage scrolls past is drawn once in each, a row apiece
     * down its sheet, in this order - the generators in `tools/` write them that way.
     *
     * Four rather than a palette computed per frame, because the art is pixel art: each of these was
     * picked by eye, shadows and all, and the hours between them are crossfades of two pictures
     * that already look right, where a computed ramp would be a guess at every pixel.
     */
    val KEYFRAMES = floatArrayOf(0f, 0.46f, 0.66f, 0.9f)

    /** The keyframe the ground is leaving at [position]: the row drawn underneath. */
    fun keyframeBelow(position: Float): Int {
        var index = 0
        while (index < KEYFRAMES.lastIndex && position >= KEYFRAMES[index + 1]) index++
        return index
    }

    /** The keyframe after [keyframeBelow], which is the one fading in over it; the last is its own. */
    fun keyframeAbove(position: Float): Int = (keyframeBelow(position) + 1).coerceAtMost(KEYFRAMES.lastIndex)

    /** How far the ground has faded from [keyframeBelow] toward [keyframeAbove], as 0..1. */
    fun keyframeBlend(position: Float): Float {
        val below = keyframeBelow(position)
        if (below == KEYFRAMES.lastIndex) return 0f
        return fraction(position, KEYFRAMES[below], KEYFRAMES[below + 1])
    }

    // --- the sky ---------------------------------------------------------------------------------

    /** How far down the sky each of a keyframe's colors sits, as 0 at the top of the frame to 1 at the horizon. */
    private val SKY_STOPS = floatArrayOf(0f, 0.4f, 0.75f, 1f)

    /** Where each [SKY] keyframe is at full strength. More of them than the ground has: the sky is where the evening happens. */
    private val SKY_POSITIONS = floatArrayOf(0f, 0.33f, 0.52f, 0.66f, 0.79f, 0.9f, 1f)

    /**
     * The sky at each of [SKY_POSITIONS], zenith to horizon.
     *
     * Bright and yellow while the sun is high: a desert noon is bleached rather than blue, and a
     * pale, warm sky is also what keeps the bat's cyan the coolest thing on screen. The golden
     * hour deepens it, sunset puts a purple zenith over a burning horizon, and night comes down
     * from the top - black overhead while the last of the glow is still purple along the dunes.
     */
    private val SKY = arrayOf(
        colors(0xF0D696, 0xF8E4AE, 0xFCEEC4, 0xFFF6DA), // noon
        colors(0xECC47C, 0xF6D492, 0xFCE2A8, 0xFFECBE), // afternoon
        colors(0xD49A6C, 0xEEAA66, 0xFAC070, 0xFFD686), // golden hour
        colors(0x844A6E, 0xD6685C, 0xF68C4C, 0xFFB254), // sunset
        colors(0x361E4C, 0x703060, 0xB84C60, 0xE87658), // dusk
        colors(0x140C28, 0x2C1640, 0x542454, 0x8C3C60), // twilight
        colors(0x06040E, 0x0C081A, 0x1A0E2C, 0x341846), // night
    )

    /**
     * The sky's color at [position], [height] of the way from the top of the frame down to the
     * horizon: blended between the two keyframes either side of the hour, then between the two
     * stops either side of the height.
     */
    fun skyColor(position: Float, height: Float): Int {
        var index = 0
        while (index < SKY_POSITIONS.lastIndex - 1 && position >= SKY_POSITIONS[index + 1]) index++
        val t = fraction(position, SKY_POSITIONS[index], SKY_POSITIONS[index + 1])
        val from = SKY[index]
        val to = SKY[index + 1]

        val h = height.coerceIn(0f, 1f)
        var stop = 0
        while (stop < SKY_STOPS.lastIndex - 1 && h >= SKY_STOPS[stop + 1]) stop++
        val s = fraction(h, SKY_STOPS[stop], SKY_STOPS[stop + 1])
        return EngineColors.lerp(
            EngineColors.lerp(from[stop], to[stop], t),
            EngineColors.lerp(from[stop + 1], to[stop + 1], t),
            s,
        )
    }

    // --- the sun ---------------------------------------------------------------------------------

    /** When the sun has gone below the dunes, and so the last position it is drawn at. */
    const val SUNSET = 0.76f

    /**
     * When the sun sits on the far dunes, half of it down: the picture of the desert's evening,
     * which is where the gameplay footage looks in on it.
     */
    const val SUNDOWN = 0.61f

    private const val NOON_X = 416f
    private const val NOON_Y = 48f
    private const val SET_X = 515f
    private const val SET_Y = 318f

    /**
     * The sun's center. It comes down on a slant toward the right of the frame - the way the bat is
     * flying, so the stage flies into its sunset - at a steady rate, the way the real one sets.
     */
    fun sunX(position: Float): Float = NOON_X + (SET_X - NOON_X) * fraction(position, 0f, SUNSET)
    fun sunY(position: Float): Float = NOON_Y + (SET_Y - NOON_Y) * fraction(position, 0f, SUNSET)

    /** A little larger the lower it gets, which is how a low sun looks even though it is not. */
    fun sunRadius(position: Float): Float = 14f + 5f * smoothstep(0.3f, SUNSET, position)

    private val SUN_POSITIONS = floatArrayOf(0f, 0.46f, 0.66f, SUNSET)
    private val SUN_COLORS = colors(0xFFFBE2, 0xFFE896, 0xFF9C4A, 0xF45E3C)

    /** White-hot at noon, gold in the afternoon, and deep orange going red as it touches the dunes. */
    fun sunColor(position: Float): Int = ramp(SUN_POSITIONS, SUN_COLORS, position)

    /**
     * How strongly the sun's halo shows, as 0..1: a pale haze while it is high, strongest as it
     * sinks toward the dunes, and gone with it.
     */
    fun sunGlow(position: Float): Float =
        0.35f + 0.65f * smoothstep(0.3f, 0.64f, position) - smoothstep(0.66f, SUNSET, position)

    // --- the night -------------------------------------------------------------------------------

    /**
     * How much of the night sky shows, as 0..1: nothing until the sun is down, then the stars come
     * out through dusk, the brightest first, until every one is up at nightfall.
     */
    fun starlight(position: Float): Float = smoothstep(0.66f, 0.95f, position)

    /** How far the moon has come up, as 0..1. It rises into the dusk behind the first stars. */
    fun moonlight(position: Float): Float = smoothstep(0.76f, 0.96f, position)

    // --- helpers ---------------------------------------------------------------------------------

    private fun colors(vararg rgb: Int) = IntArray(rgb.size) { rgb[it] or OPAQUE }

    private fun ramp(positions: FloatArray, colors: IntArray, position: Float): Int {
        if (position <= positions.first()) return colors.first()
        for (i in 0 until positions.lastIndex) {
            if (position < positions[i + 1]) {
                return EngineColors.lerp(colors[i], colors[i + 1], fraction(position, positions[i], positions[i + 1]))
            }
        }
        return colors.last()
    }

    /** Where [value] sits between [from] and [to], as 0..1. */
    private fun fraction(value: Float, from: Float, to: Float): Float =
        if (to <= from) 1f else ((value - from) / (to - from)).coerceIn(0f, 1f)

    /** [fraction] eased in and out, so a change starts and finishes gently instead of on a corner. */
    fun smoothstep(from: Float, to: Float, value: Float): Float {
        val t = fraction(value, from, to)
        return t * t * (3f - 2f * t)
    }

    private const val OPAQUE = 0xFF000000.toInt()
}
