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

    companion object {
        /** For hosts with no keyboard or pad attached, and for tests that only drive touch. */
        val None: Controls = object : Controls {
            override val moveX = 0f
            override val moveY = 0f
            override fun consumePress(button: GameButton) = false
        }
    }
}
