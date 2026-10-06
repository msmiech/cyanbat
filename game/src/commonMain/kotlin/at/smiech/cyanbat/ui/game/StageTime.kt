package at.smiech.cyanbat.ui.game

/**
 * [seconds] of stage time as the timer shows them: "mm:ss".
 *
 * Rounded down, as the waves count, so the timer turns over to 01:00 on the tick the wave readout
 * moves to wave 2. Minutes are padded too, so the centered readout keeps its width all run.
 */
internal fun formatStageTime(seconds: Float): String {
    val whole = seconds.toInt()
    return "${twoDigits(whole / 60)}:${twoDigits(whole % 60)}"
}

/** [value] padded to two digits. */
private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
