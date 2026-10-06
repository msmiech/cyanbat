package at.smiech.engine

/**
 * One screen of the game, such as a run or the game over, driven by the [GameLoop]: [update] steps
 * its logic and [present] records its frame.
 */
interface Screen {
    val game: Game

    /** Advances the screen by [deltaTime] seconds. */
    fun update(deltaTime: Float)

    /** Draws the screen's frame through the game's [Graphics]. */
    fun present(deltaTime: Float)

    /** Called when the host is backgrounded or loses focus. */
    fun pause() = Unit

    /** Called when the host comes back from [pause]. */
    fun resume() = Unit

    /**
     * Called once when the screen is replaced or the game shuts down. Releases anything the screen
     * started, background work in particular. The screen is not used again afterwards.
     */
    fun dispose() = Unit
}
