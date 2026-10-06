package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.util.SPREAD_ANGLE_DEGREES

/**
 * The headings of a volley of [shots], in degrees off straight ahead, positive downward: a fan with
 * [SPREAD_ANGLE_DEGREES] between neighbors, centered on straight ahead so it is balanced at every
 * count. An odd count has a shot down the middle; an even one straddles the line.
 */
internal fun spreadAngles(shots: Int): List<Float> {
    val middle = (shots - 1) / 2f
    return List(shots) { (it - middle) * SPREAD_ANGLE_DEGREES }
}
