package at.smiech.engine

/**
 * How the 16:9 framebuffer is fitted to a screen of another shape: phones run to 21:9, while
 * tablets and some monitors are squarer. The player picks one in Settings.
 */
enum class DisplayMode {
    /** Scaled to fill the screen, each axis on its own: no bars, but the picture is distorted. */
    STRETCH,

    /** Scaled evenly to fit and centered, with black bars in the space left over. */
    BLACK_BARS,

    /**
     * As [BLACK_BARS], but the bars are lit with the colors at the frame's edges, like a video
     * player's ambient mode.
     */
    AMBIENT;

    companion object {
        /** The mode until the player chooses another: undistorted, and without dead black bars. */
        val DEFAULT = AMBIENT

        /**
         * The mode stored as [name], or [DEFAULT] for anything unrecognized, such
         * as a newer build's.
         */
        fun fromName(name: String?): DisplayMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
