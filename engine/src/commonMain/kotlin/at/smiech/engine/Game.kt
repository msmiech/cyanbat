package at.smiech.engine

/**
 * The host application, as the game logic sees it. Deliberately free of platform types so
 * screens can run on any target - the Android activity and the desktop window each implement it.
 */
interface Game {
    val input: Input?
    val graphics: Graphics?
    val audio: Audio?
    fun setScreen(screen: Screen)
    val currentScreen: Screen?
    val startScreen: Screen?
    val frameBufferWidth: Int
    val frameBufferHeight: Int
}
