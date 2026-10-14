package at.smiech.cyanbat.data

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.stick_sensitivity_high
import at.smiech.cyanbat.resources.stick_sensitivity_low
import at.smiech.cyanbat.resources.stick_sensitivity_medium
import org.jetbrains.compose.resources.StringResource

/**
 * How far a thumb has to push the stick on the screen for the bat to fly at full speed: the
 * further, the less sensitive, and the finer the control a short push gives. Chosen in Settings,
 * where the stick is on.
 *
 * Radii in frame pixels, which on a phone held sideways are about a fifth of a millimeter each.
 * High is how the stick first played, which was too twitchy to be the default.
 */
enum class StickSensitivity(
    val label: StringResource,
    /** The stick's radius, which the thumb travels most of for full speed. */
    val radius: Float,
) {
    LOW(Res.string.stick_sensitivity_low, 80f),
    MEDIUM(Res.string.stick_sensitivity_medium, 64f),
    HIGH(Res.string.stick_sensitivity_high, 48f);

    companion object {
        /** The sensitivity until the player chooses one. */
        val DEFAULT = MEDIUM

        /**
         * The sensitivity stored as [name], or [DEFAULT] for anything unrecognized, such as a
         * newer build's.
         */
        fun fromName(name: String?): StickSensitivity =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
