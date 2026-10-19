package at.smiech.engine

/**
 * A button the game reacts to, named for its meaning rather than for the key or pad
 * button behind it.
 *
 * Hosts own the mapping, so a keyboard, a game controller and a system gesture can all raise the
 * same [PAUSE] without the screens knowing which one did.
 */
enum class GameButton {
    /** Suspend or resume play. Escape on a keyboard, Start on a pad. */
    PAUSE,

    /** Step back out: pause a running game, leave an already paused one. Android's back gesture. */
    BACK,

    /** Accept whatever is on screen. Enter or Space, A on a pad. */
    CONFIRM,

    /**
     * Pick the first, second or third of whatever the screen offers. The number keys, and a pad's
     * face buttons.
     *
     * Numbered rather than named because the meaning belongs to the screen showing the choice, not
     * to the key. [CONFIRM] cannot stand in: it accepts one thing, and a choice needs three.
     */
    CHOICE_1,
    CHOICE_2,
    CHOICE_3,
    ;

    companion object {
        /** The choice buttons in order, so a screen can index them by the card it is drawing. */
        val CHOICES = listOf(CHOICE_1, CHOICE_2, CHOICE_3)
    }
}

/**
 * What the player last played with, which decides the button prompts the game shows: a tap, a key
 * or a click, or a controller's own buttons.
 *
 * Controllers are told apart by their maker, since that is what their buttons are labeled by: a
 * PlayStation pad's face buttons are shapes, an Xbox pad's are letters, and any other pad is taken
 * for an Xbox one, as the controller libraries lay them all out.
 */
enum class InputMethod {
    TOUCH,
    KEYBOARD_MOUSE,
    XBOX_PAD,
    PLAYSTATION_PAD,
    ;

    val isPad: Boolean get() = this == XBOX_PAD || this == PLAYSTATION_PAD

    companion object {
        /**
         * The pad a controller's name or maker says it is: Sony's are PlayStation pads, the rest
         * Xbox ones.
         *
         * @param vendorId the USB vendor id, where the platform reports one.
         */
        fun padOf(name: String?, vendorId: Int? = null): InputMethod {
            if (vendorId == SONY_VENDOR_ID) return PLAYSTATION_PAD
            val lower = name.orEmpty().lowercase()
            val sony = PLAYSTATION_NAMES.any { it in lower } || "054c" in lower
            return if (sony) PLAYSTATION_PAD else XBOX_PAD
        }

        /** Sony's USB vendor id. */
        const val SONY_VENDOR_ID = 0x054C

        /**
         * What a Sony pad's name has in it, as SDL ("PS5 Controller"), the browsers ("DualSense
         * Wireless Controller") and Android report it.
         */
        private val PLAYSTATION_NAMES =
            listOf("dualsense", "dualshock", "playstation", "ps3", "ps4", "ps5", "sony")
    }
}

/** A digital direction, as a key or a d-pad reports it. Sticks report [Controls.moveX] directly. */
enum class Direction { LEFT, RIGHT, UP, DOWN }

/**
 * Keyboard and game controller state, as the game logic sees it.
 *
 * An *intent* rather than a key map: movement arrives as two axes and actions as named buttons, so
 * a thumbstick needs no special case and a new input device is a host-side mapping only.
 */
interface Controls {
    /** -1 hard left to +1 hard right. Analog from a stick, snapped to the ends by a key. */
    val moveX: Float

    /** -1 up to +1 down, matching the framebuffer's y axis. */
    val moveY: Float

    /**
     * True exactly once per press of [button], for whichever update reads it first.
     *
     * Edge-triggered because every button toggles something: a held Escape must not flip pause on
     * and off for as long as it is held.
     */
    fun consumePress(button: GameButton): Boolean

    /** What the player last played with, so the screens can show its buttons. */
    val inputMethod: InputMethod

    companion object {
        /** For hosts with no keyboard or pad attached, and for tests that only drive touch. */
        val None: Controls = object : Controls {
            override val moveX = 0f
            override val moveY = 0f
            override fun consumePress(button: GameButton) = false
            override val inputMethod = InputMethod.TOUCH
        }
    }
}
