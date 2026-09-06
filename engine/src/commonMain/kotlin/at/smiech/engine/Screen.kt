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
}
