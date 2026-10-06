package at.smiech.engine

/**
 * The host application, as the game logic sees it. Free of platform types so screens run on any
 * target; the Android activity and the desktop window each implement it.
 */
interface Game {
    val input: Input?
    val graphics: Graphics?
    val audio: Audio?

    /** Disposes the current screen and shows [screen] in its place. */
    fun setScreen(screen: Screen)
    val currentScreen: Screen?

    /** The frame's size in pixels, which every screen draws at and every touch is mapped to. */
    val frameBufferWidth: Int
    val frameBufferHeight: Int
}
