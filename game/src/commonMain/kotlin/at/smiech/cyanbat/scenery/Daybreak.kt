package at.smiech.cyanbat.scenery

/**
 * The lagoon's day, from night at the stage's first second to noon on its boss's: the desert's
 * [Daylight] run the other way, and in other colors.
 *
 * The day follows the waves. Night for the first, the moon going down over the sea behind the bat and
 * the stars going out one by one; the dawn coming up magenta and pink along the horizon through the
 * second; the sun rising out of the sea ahead in the third, cut into bands by the haze over the water
 * and laying a path of glints across it; the morning in the fourth, the sky turning from violet to
 * turquoise over a pink horizon; and the full light of noon - a cyan sky over pale pink haze - by the
 * time the boss arrives. The colors are a beach town's neon at dawn more than a postcard's.
 *
 * Sky this bright behind the bat is safe: the bat is drawn dark, in navy and deep blue, and reads on
 * pale cyan as it does on the desert's pale yellow.
 */
object Daybreak : Day {

    // --- the ground ------------------------------------------------------------------------------

    /** Night, dawn, sunrise and noon. */
    val KEYFRAMES = floatArrayOf(0f, 0.3f, 0.5f, 0.8f)

    override val keyframes: FloatArray get() = KEYFRAMES

    // --- the sky ---------------------------------------------------------------------------------

    private val SKY_STOPS = floatArrayOf(0f, 0.4f, 0.75f, 1f)

    private val SKY_POSITIONS = floatArrayOf(0f, 0.16f, 0.3f, 0.42f, 0.55f, 0.72f, 0.86f, 1f)

    /**
     * The sky at each of [SKY_POSITIONS], zenith to horizon.
     *
     * The night is indigo with a purple glow low down; the dawn comes up from the horizon in magenta
     * and hot pink under a violet sky; at sunrise the horizon burns peach under pink and purple; and
     * the morning clears from the top down, periwinkle and then turquoise, until noon is a cyan
     * zenith paling to near white and a pink haze along the sea.
     */
    private val SKY = arrayOf(
        colors(0x0A0820, 0x150F38, 0x261650, 0x3A1C62), // night
        colors(0x0E0C2E, 0x1E1650, 0x3A206C, 0x64287A), // before the dawn
        colors(0x1C1854, 0x3A2678, 0x8C3890, 0xE24C8C), // dawn
        colors(0x2E2E86, 0x7244A8, 0xEC5C9C, 0xFF9C6E), // sunrise
        colors(0x3E78CC, 0x9C7CD0, 0xFF88B8, 0xFFC09A), // early morning
        colors(0x3CBEF0, 0x86DEF4, 0xEAC4EC, 0xFFCCDC), // morning
        colors(0x3ECAF8, 0x8EEEFA, 0xE2F2FA, 0xFDD2E8), // late morning
        colors(0x40CEF8, 0x94F2FC, 0xE6F4FA, 0xFDCCE6), // noon
    )

    override fun skyColor(position: Float, height: Float): Int =
        skyBetween(SKY_POSITIONS, SKY, SKY_STOPS, position, height)

    // --- the sun ---------------------------------------------------------------------------------

    /** When the sun's crown first shows over the sea. */
    const val SUNRISE = 0.36f

    /**
     * When the sun stands half out of the sea, banded by its haze, with its path of glints the
     * longest it gets: the picture of the lagoon's morning, which is where the reel looks in on it.
     */
    const val SUNUP = 0.43f

    override val showcase: Float get() = SUNUP

    /** Its course, from rising out of the sea on the right - the way the bat flies - to high at noon. */
    private const val RISE_X = 520f
    private const val RISE_Y = 254f
    private const val NOON_X = 404f
    private const val NOON_Y = 54f
    private const val HIGH = 0.96f

    override fun sunUp(position: Float): Boolean = position >= SUNRISE

    override fun sunX(position: Float): Float = RISE_X + (NOON_X - RISE_X) * fraction(position, SUNRISE, HIGH)
    override fun sunY(position: Float): Float = RISE_Y + (NOON_Y - RISE_Y) * fraction(position, SUNRISE, HIGH)

    /** Swollen while it is low, shrinking as it climbs. */
    override fun sunRadius(position: Float): Float = 23f - 8f * smoothstep(0.42f, 0.88f, position)

    private val SUN_POSITIONS = floatArrayOf(SUNRISE, 0.46f, 0.6f, 0.86f)
    private val SUN_COLORS = colors(0xFF5E8E, 0xFF8A68, 0xFFC680, 0xFFF8E0)
    private val SUN_FOOT_COLORS = colors(0xD8287A, 0xFF4E8C, 0xFFA878, 0xFFF8E0)

    /** Hot pink as it breaks the water, coral and then gold as it climbs, white-hot by noon. */
    override fun sunColor(position: Float): Int = ramp(SUN_POSITIONS, SUN_COLORS, position)

    /** Shading down into magenta at its foot while it is low; one color by the late morning. */
    override fun sunLowColor(position: Float): Int = ramp(SUN_POSITIONS, SUN_FOOT_COLORS, position)

    /** A halo that sets the dawn alight while it is low, and settles to a pale haze as it climbs. */
    override fun sunGlow(position: Float): Float = 0.35f + 0.65f * (1f - smoothstep(0.46f, 0.8f, position))

    /** Banded through the haze over the sea as it rises, and clear of it by the middle of the morning. */
    override fun sunBands(position: Float): Float = 1f - smoothstep(0.46f, 0.62f, position)

    /** Its path on the water, from the moment it is up until it is too high to lay one. */
    override fun sunGlitter(position: Float): Float =
        smoothstep(SUNRISE, 0.41f, position) * (1f - smoothstep(0.6f, 0.82f, position))

    // --- the night -------------------------------------------------------------------------------

    /** Every star out at the start, going out through the dawn, the faintest first. */
    override fun starlight(position: Float): Float = 1f - smoothstep(0.12f, 0.36f, position)

    /** Up through the night, and gone into the brightening sky before the sun is. */
    override fun moonlight(position: Float): Float = 1f - smoothstep(0.2f, 0.36f, position)

    /** Going down behind the bat, low over the sea on the left, as the dawn comes up ahead. */
    private const val MOON_START_X = 128f
    private const val MOON_START_Y = 46f
    private const val MOON_END_X = 92f
    private const val MOON_END_Y = 132f

    override fun moonX(position: Float): Float =
        MOON_START_X + (MOON_END_X - MOON_START_X) * fraction(position, 0f, 0.36f)

    override fun moonY(position: Float): Float =
        MOON_START_Y + (MOON_END_Y - MOON_START_Y) * fraction(position, 0f, 0.36f)
}
