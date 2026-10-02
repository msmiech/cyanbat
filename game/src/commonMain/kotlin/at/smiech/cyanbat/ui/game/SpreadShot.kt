package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.util.SPREAD_ANGLE_DEGREES

/**
 * The headings of one of the bat's volleys of [shots], in degrees off straight ahead, positive
 * downwards: a fan with [SPREAD_ANGLE_DEGREES] between neighbors, centered on straight ahead.
 *
 * Centered rather than built out from a straight shot, so the fan is balanced at every count. An
 * odd number has a shot down the middle; an even one straddles the line, half a step either side
 * of it, where a straight shot with the extras alternating round it would lean a whole step to one
 * side.
 */
internal fun spreadAngles(shots: Int): List<Float> {
    val middle = (shots - 1) / 2f
    return List(shots) { (it - middle) * SPREAD_ANGLE_DEGREES }
}
