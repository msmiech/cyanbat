package at.smiech.cyanbat.scenery

/**
 * The desert's day, from noon at the stage's first second to night on its boss's: what the sky, the
 * sun, the moon and the stars look like at any point in between.
 *
 * The day follows the waves. Noon for the first; the long afternoon through the second and third,
 * the light going gold; the sun going down in the fourth; dusk in the fifth, with the first stars;
 * and night - black and purple, the moon up and every star out - the moment the boss arrives.
 */
object Daylight : Day {

    // --- the ground ------------------------------------------------------------------------------

    /** Noon, the golden hour, sunset and night. */
    val KEYFRAMES = floatArrayOf(0f, 0.46f, 0.66f, 0.9f)

    override val keyframes: FloatArray get() = KEYFRAMES

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

    override fun skyColor(position: Float, height: Float): Int =
        skyBetween(SKY_POSITIONS, SKY, SKY_STOPS, position, height)

    // --- the sun ---------------------------------------------------------------------------------

    /** When the sun has gone below the dunes, and so the last position it is drawn at. */
    const val SUNSET = 0.76f

    /**
     * When the sun sits on the far dunes, half of it down: the picture of the desert's evening,
     * which is where the gameplay footage looks in on it.
     */
    const val SUNDOWN = 0.61f

    override val showcase: Float get() = SUNDOWN

    private const val NOON_X = 416f
    private const val NOON_Y = 48f
    private const val SET_X = 515f
    private const val SET_Y = 318f

    override fun sunUp(position: Float): Boolean = position < SUNSET

    /**
     * The sun's center. It comes down on a slant toward the right of the frame - the way the bat is
     * flying, so the stage flies into its sunset - at a steady rate, the way the real one sets.
     */
    override fun sunX(position: Float): Float = NOON_X + (SET_X - NOON_X) * fraction(position, 0f, SUNSET)
    override fun sunY(position: Float): Float = NOON_Y + (SET_Y - NOON_Y) * fraction(position, 0f, SUNSET)

    /** A little larger the lower it gets, which is how a low sun looks even though it is not. */
    override fun sunRadius(position: Float): Float = 14f + 5f * smoothstep(0.3f, SUNSET, position)

    private val SUN_POSITIONS = floatArrayOf(0f, 0.46f, 0.66f, SUNSET)
    private val SUN_COLORS = colors(0xFFFBE2, 0xFFE896, 0xFF9C4A, 0xF45E3C)

    /** White-hot at noon, gold in the afternoon, and deep orange going red as it touches the dunes. */
    override fun sunColor(position: Float): Int = ramp(SUN_POSITIONS, SUN_COLORS, position)

    /**
     * A pale haze while it is high, strongest as it sinks toward the dunes, and gone with it.
     */
    override fun sunGlow(position: Float): Float =
        0.35f + 0.65f * smoothstep(0.3f, 0.64f, position) - smoothstep(0.66f, SUNSET, position)

    // --- the night -------------------------------------------------------------------------------

    /**
     * Nothing until the sun is down, then the stars come out through dusk, the brightest first,
     * until every one is up at nightfall.
     */
    override fun starlight(position: Float): Float = smoothstep(0.66f, 0.95f, position)

    /** It rises into the dusk behind the first stars. */
    override fun moonlight(position: Float): Float = smoothstep(0.76f, 0.96f, position)

    private const val MOON_X = 188f
    private const val MOON_Y = 40f
    private const val MOON_RISE = 26f

    /** Where it stands, over the far pyramids. */
    override fun moonX(position: Float): Float = MOON_X

    /** It climbs a little as it comes out, rather than fading in on the spot. */
    override fun moonY(position: Float): Float = MOON_Y + MOON_RISE * (1f - moonlight(position))
}
