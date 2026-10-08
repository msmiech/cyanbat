package at.smiech.cyanbat.data

/**
 * How the game takes its screen on the desktop: in a window, in full screen, or in a borderless
 * window over the whole screen. Chosen in Settings, or toggled with a key. A phone's game always
 * fills its screen, and Settings offers none of this there.
 */
enum class WindowMode {
    /** A window in the system's frame, which the player can move, resize and maximize. */
    WINDOWED,

    /**
     * The system's own full screen. On Windows it stays above every other window, and switching
     * away minimizes it, as a full-screen game does there.
     */
    FULLSCREEN,

    /**
     * A window with no frame, the size of the screen it is on. It looks like [FULLSCREEN], but
     * other windows can come over it and switching away leaves it where it is, which suits a
     * second screen or an overlay. Not on a Mac, whose own full screen already works this way.
     */
    BORDERLESS;

    companion object {
        /** The mode until the player chooses one, or a host starts them in another. */
        val DEFAULT = WINDOWED

        /**
         * The mode stored as [name], or [DEFAULT] for anything unrecognized, such as a newer
         * build's.
         */
        fun fromName(name: String?): WindowMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
