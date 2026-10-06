package at.smiech.engine

/**
 * Drives the current screen from a host's frame callback. Shared by every platform so the timing
 * rules, in particular the delta clamp, hold everywhere.
 *
 * Feed it the host's frame timestamp: `withFrameNanos` on Compose, a timer on Swing.
 */
class GameLoop(
    private val game: Game,
    private val maxDeltaSeconds: Float = MAX_FRAME_DELTA_SECONDS,
    /** Marks each frame's update and present for a profiler; see [FrameTrace]. */
    private val trace: FrameTrace = FrameTrace.None,
) {
    private var lastFrameNanos = 0L

    /** Updates and presents the current screen for the frame at [frameTimeNanos]. */
    fun frame(frameTimeNanos: Long) {
        // The first frame has no predecessor to measure against, so seed and skip it.
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameTimeNanos
            return
        }

        val elapsed = (frameTimeNanos - lastFrameNanos) / 1e9f
        lastFrameNanos = frameTimeNanos

        // A paused host stops delivering frames, so the first frame after a resume carries the
        // whole pause in its delta. Screens step fixed ticks in a loop, so an unclamped delta would
        // replay all of it at once and the player would take damage they never saw. Time past the
        // cap is dropped, which briefly slows the game instead.
        val deltaTime = elapsed.coerceIn(0f, maxDeltaSeconds)

        // Traced separately because they cost different things: update is the game logic, present
        // records the frame the host draws afterwards.
        traced(UPDATE_SECTION) { game.currentScreen?.update(deltaTime) }
        traced(PRESENT_SECTION) { game.currentScreen?.present(deltaTime) }
    }

    /** Call when the host resumes, so the next frame is treated as a fresh start. */
    fun reset() {
        lastFrameNanos = 0L
    }

    private inline fun traced(section: String, block: () -> Unit) {
        trace.begin(section)
        try {
            block()
        } finally {
            trace.end()
        }
    }

    companion object {
        /** The trace section for the screen's update. */
        const val UPDATE_SECTION = "Screen.update"

        /**
         * The trace section for the screen's present, which records the frame for the host to draw.
         */
        const val PRESENT_SECTION = "Screen.present"

        /**
         * Upper bound on the delta handed to a screen, in seconds: about three frames at 60 Hz.
         * Loose enough to absorb frame jitter, tight enough that a resume costs a couple of ticks
         * rather than the whole time spent in the background.
         */
        const val MAX_FRAME_DELTA_SECONDS = 0.05f
    }
}
