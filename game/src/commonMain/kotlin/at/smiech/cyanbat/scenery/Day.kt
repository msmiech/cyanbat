package at.smiech.cyanbat.scenery

import at.smiech.engine.EngineColors

/**
 * The day a stage is flown through, under a sky the game draws: what the sky, the sun, the moon and
 * the stars look like at any point in it. Drawn by `SkySystem`; see
 * [at.smiech.cyanbat.resource.Backdrop.Sky].
 *
 * Everything here is a pure function of one number, how far through its day the stage is - see
 * [position]. That is what lets a whole sunset or sunrise be tested without drawing a pixel of it,
 * and what ties it to the stage clock: a paused game is a paused sky, and a stage flown slowly gets
 * no darker or lighter for it than one flown fast.
 *
 * Two are flown: the desert's, from noon to night ([Daylight]), and the lagoon's, from night to
 * noon ([Daybreak]).
 */
interface Day {

    // --- the ground ------------------------------------------------------------------------------

    /**
     * Where each of the ground's palettes is at full strength, in the order they are drawn. Every
     * piece of scenery flown past under this sky is drawn once in each, a row apiece down its sheet,
     * in this order - the generators in `tools/` write them that way - and the hours between are
     * crossfades of the two either side.
     *
     * A few palettes rather than one computed per frame, because the art is pixel art: each was
     * picked by eye, shadows and all, and the hours between them are crossfades of two pictures that
     * already look right, where a computed ramp would be a guess at every pixel.
     */
    val keyframes: FloatArray

    /** The keyframe the ground is leaving at [position]: the row drawn underneath. */
    fun keyframeBelow(position: Float): Int {
        var index = 0
        while (index < keyframes.lastIndex && position >= keyframes[index + 1]) index++
        return index
    }

    /** The keyframe after [keyframeBelow], which is the one fading in over it; the last is its own. */
    fun keyframeAbove(position: Float): Int =
        (keyframeBelow(position) + 1).coerceAtMost(keyframes.lastIndex)

    /** How far the ground has faded from [keyframeBelow] toward [keyframeAbove], as 0..1. */
    fun keyframeBlend(position: Float): Float {
        val below = keyframeBelow(position)
        if (below == keyframes.lastIndex) return 0f
        return fraction(position, keyframes[below], keyframes[below + 1])
    }

    // --- the sky ---------------------------------------------------------------------------------

    /**
     * The sky's color at [position], [height] of the way from the top of the frame down to the
     * horizon.
     */
    fun skyColor(position: Float, height: Float): Int

    // --- the sun ---------------------------------------------------------------------------------

    /** Whether the sun is above the horizon at [position], and so drawn. */
    fun sunUp(position: Float): Boolean

    /** The sun's center. */
    fun sunX(position: Float): Float
    fun sunY(position: Float): Float

    fun sunRadius(position: Float): Float
    fun sunColor(position: Float): Int

    /** How strongly the sun's halo shows, as 0..1. */
    fun sunGlow(position: Float): Float

    /**
     * How far the sun's lower half is cut into bands by the haze over the sea, as 0..1: the striped
     * sun of a sunrise, its stripes thickening toward the horizon. Nothing for a sun that is never
     * seen through such a haze, which is the default.
     */
    fun sunBands(position: Float): Float = 0f

    /**
     * The color of the sun's foot, which it shades into from [sunColor] at its crown: a deeper one
     * for a sun low over the sea. Its own color, a disc of one color, by default.
     */
    fun sunLowColor(position: Float): Int = sunColor(position)

    /**
     * How strongly the sun lays a path of glints across the water under it, as 0..1: strongest
     * while it is low. Nothing for a day with no water to lay it on, which is the default.
     */
    fun sunGlitter(position: Float): Float = 0f

    // --- the night -------------------------------------------------------------------------------

    /** How much of the night sky shows, as 0..1: the stars, the brightest first, and the meteors. */
    fun starlight(position: Float): Float

    /** How much of the moon shows, as 0..1. */
    fun moonlight(position: Float): Float

    /** Where the moon's top left corner is, as it rises or sets. */
    fun moonX(position: Float): Float
    fun moonY(position: Float): Float

    // --- the reel --------------------------------------------------------------------------------

    /**
     * The hour the README's reel shows of this day, as a position: the picture of it, which the
     * stage's opening and its boss would otherwise both miss - the desert's sun going down behind
     * the dunes, the lagoon's coming up out of the sea.
     */
    val showcase: Float

    companion object {
        /**
         * How far through its day a stage is, as 0..1: its first second at 0, its boss's arrival at 1,
         * and held there through the boss fight.
         */
        fun position(elapsedSeconds: Float, bossTimeSeconds: Float): Float =
            if (bossTimeSeconds <= 0f) 1f else (elapsedSeconds / bossTimeSeconds).coerceIn(0f, 1f)
    }
}

// --- shared by the days ----------------------------------------------------------------------------

/** Where [value] sits between [from] and [to], as 0..1. */
internal fun fraction(value: Float, from: Float, to: Float): Float =
    if (to <= from) 1f else ((value - from) / (to - from)).coerceIn(0f, 1f)

/** [fraction] eased in and out, so a change starts and finishes gently instead of on a corner. */
internal fun smoothstep(from: Float, to: Float, value: Float): Float {
    val t = fraction(value, from, to)
    return t * t * (3f - 2f * t)
}

/** Opaque colors from plain RGB literals. */
internal fun colors(vararg rgb: Int) = IntArray(rgb.size) { rgb[it] or OPAQUE }

/** The color at [position] along [colors], each at full strength at its own entry of [positions]. */
internal fun ramp(positions: FloatArray, colors: IntArray, position: Float): Int {
    if (position <= positions.first()) return colors.first()
    for (i in 0 until positions.lastIndex) {
        if (position < positions[i + 1]) {
            return EngineColors.lerp(
                colors[i],
                colors[i + 1],
                fraction(position, positions[i], positions[i + 1])
            )
        }
    }
    return colors.last()
}

/**
 * A sky's color from its keyframes: blended between the two keyframes either side of [position],
 * then between the two stops either side of [height]. Each keyframe holds one color per stop.
 */
internal fun skyBetween(
    keyframes: FloatArray,
    sky: Array<IntArray>,
    stops: FloatArray,
    position: Float,
    height: Float,
): Int {
    var index = 0
    while (index < keyframes.lastIndex - 1 && position >= keyframes[index + 1]) index++
    val t = fraction(position, keyframes[index], keyframes[index + 1])
    val from = sky[index]
    val to = sky[index + 1]

    val h = height.coerceIn(0f, 1f)
    var stop = 0
    while (stop < stops.lastIndex - 1 && h >= stops[stop + 1]) stop++
    val s = fraction(h, stops[stop], stops[stop + 1])
    return EngineColors.lerp(
        EngineColors.lerp(from[stop], to[stop], t),
        EngineColors.lerp(from[stop + 1], to[stop + 1], t),
        s,
    )
}

private const val OPAQUE = 0xFF000000.toInt()
