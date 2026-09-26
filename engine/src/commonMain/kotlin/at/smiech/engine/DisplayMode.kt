package at.smiech.engine

/**
 * How the framebuffer is shown on a screen that is not its own shape - which, for a 3:2 framebuffer
 * and phones anywhere from 16:9 to 21:9, is nearly every screen it will ever be shown on.
 *
 * The player's choice, in the menu's settings, because each of these is somebody's favorite: some
 * people want the whole screen used, some want the game the shape it was drawn, and some want the
 * shape without the dead black either side of it.
 */
enum class DisplayMode {
    /** Scaled to fill the screen, each axis on its own: no bars, but the game drawn out of shape. */
    STRETCH,

    /** Scaled evenly to fit and centered, with black bars in whatever space is left over. */
    BLACK_BARS,

    /**
     * As [BLACK_BARS], but the bars are lit with the colors at the frame's edges - a video player's
     * ambient mode, so the cave's glow runs out to the sides of the screen instead of stopping dead.
     */
    AMBIENT;

    companion object {
        /** What a player gets until they choose otherwise: the game in its own shape, and no dead black. */
        val DEFAULT = AMBIENT

        /** The mode stored as [name], or [DEFAULT] for anything unrecognized - a newer build's mode, say. */
        fun fromName(name: String?): DisplayMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
