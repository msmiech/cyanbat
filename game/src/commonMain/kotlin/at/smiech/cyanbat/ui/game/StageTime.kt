package at.smiech.cyanbat.ui.game

/**
 * [seconds] of stage time as the timer shows them: minutes and seconds, "mm:ss".
 *
 * Rounded down, the way a clock counts, which is also the way the waves count: the timer turns
 * over to 01:00 on the same tick the wave readout moves on to wave 2. Rounded to nearest, every new
 * minute would show on the timer half a second before it reached the cave.
 *
 * The minutes are padded as well as the seconds, so the readout is the same five characters for
 * the whole of any run. The timer is centered by that width, and one that gained a digit partway
 * through would jump sideways.
 */
internal fun formatStageTime(seconds: Float): String {
    val whole = seconds.toInt()
    return "${twoDigits(whole / 60)}:${twoDigits(whole % 60)}"
}

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
