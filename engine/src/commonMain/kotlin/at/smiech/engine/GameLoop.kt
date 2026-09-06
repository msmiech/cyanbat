package at.smiech.engine

/**
 * Drives the current screen from a host's frame callback. Shared by every platform so the timing
 * rules - in particular the delta clamp below - hold everywhere rather than being re-derived.
 *
 * Feed it the host's frame timestamp: `withFrameNanos` on Compose, a timer on Swing.
 */
class GameLoop(
    private val game: Game,
    private val maxDeltaSeconds: Float = MAX_FRAME_DELTA_SECONDS
) {
    private var lastFrameNanos = 0L

    fun frame(frameTimeNanos: Long) {
        // The first frame has no predecessor to measure against, so seed and skip it.
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameTimeNanos
            return
        }

        val elapsed = (frameTimeNanos - lastFrameNanos) / 1e9f
        lastFrameNanos = frameTimeNanos

        // A paused host stops delivering frame callbacks, so the first frame after a resume
        // carries the whole pause in its delta. Screens step fixed-size ticks in a while-loop, so
        // an unclamped delta replays all of that in a single frame: the player loses lives to a
        // fast-forward they never see. Time beyond the cap is dropped rather than simulated,
        // which briefly slows game time instead of teleporting the world.
        val deltaTime = elapsed.coerceIn(0f, maxDeltaSeconds)

        game.currentScreen?.update(deltaTime)
        game.currentScreen?.present(deltaTime)
    }

    /** Call when the host resumes, so the next frame is treated as a fresh start. */
    fun reset() {
        lastFrameNanos = 0L
    }

    companion object {
        /**
         * Upper bound on the delta handed to a screen, in seconds. Roughly three frames at 60Hz -
         * loose enough to absorb ordinary frame jitter, tight enough that a resume costs a couple
         * of ticks instead of the entire time the app spent in the background.
         */
        const val MAX_FRAME_DELTA_SECONDS = 0.05f
    }
}
