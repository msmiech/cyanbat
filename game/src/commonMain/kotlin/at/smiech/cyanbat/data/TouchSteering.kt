package at.smiech.cyanbat.data

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.steering_drag
import at.smiech.cyanbat.resources.steering_drag_description
import at.smiech.cyanbat.resources.steering_drag_preview
import at.smiech.cyanbat.resources.steering_stick
import at.smiech.cyanbat.resources.steering_stick_description
import at.smiech.cyanbat.resources.steering_stick_preview
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * How a finger on a touch screen steers the bat. Asked once, before the first run on a touch screen
 * (see `SteeringDialog`), and changed in Settings. Keys and game pads steer the same either way.
 */
enum class TouchSteering(
    val title: StringResource,
    val description: StringResource,
    /** A frame of the game with a hand flying the bat this way, for the player to choose by. */
    val preview: DrawableResource,
) {
    /** The bat goes where the finger takes it, anywhere on the screen. */
    DRAG(
        Res.string.steering_drag,
        Res.string.steering_drag_description,
        Res.drawable.steering_drag_preview,
    ),

    /** A stick drawn at the bottom left is pushed the way the bat is to fly. */
    STICK(
        Res.string.steering_stick,
        Res.string.steering_stick_description,
        Res.drawable.steering_stick_preview,
    );

    companion object {
        /** How the bat is steered until the player chooses: as it always was. */
        val DEFAULT = DRAG

        /**
         * The steering stored as [name], or null for none stored - the player has not been asked -
         * or for anything unrecognized, such as a newer build's, which asks again.
         */
        fun fromName(name: String?): TouchSteering? = entries.firstOrNull { it.name == name }
    }
}
