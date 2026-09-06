package at.smiech.engine

interface Screen {
    val game: Game
    fun update(deltaTime: Float)
    fun present(deltaTime: Float)
    fun pause() {
        // empty default implementation
    }

    fun resume() {
        // empty default implementation
    }

    /**
     * Called once when the screen is replaced or the game shuts down. Release anything the screen
     * started here - background work in particular. The screen is not used again afterwards.
     */
    fun dispose() {
        // empty default implementation
    }
}
