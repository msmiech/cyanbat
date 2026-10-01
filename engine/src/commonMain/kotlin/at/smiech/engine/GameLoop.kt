package at.smiech.engine

/**
 * Drives the current screen from a host's frame callback. Shared by every platform so the timing
 * rules - in particular the delta clamp below - hold everywhere rather than being re-derived.
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
        // an unclamped delta replays all of that in a single frame: the player loses health to a
        // fast-forward they never see. Time beyond the cap is dropped rather than simulated,
        // which briefly slows game time instead of teleporting the world.
        val deltaTime = elapsed.coerceIn(0f, maxDeltaSeconds)

        // Marked apart, because they cost different things: update is the game's logic, present
        // is the rasterizing of the whole frame into the framebuffer.
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
        /** The screen's update, as a trace names it. */
        const val UPDATE_SECTION = "Screen.update"

        /** The screen's present, as a trace names it: the frame drawn into the framebuffer. */
        const val PRESENT_SECTION = "Screen.present"

        /**
         * Upper bound on the delta handed to a screen, in seconds. Roughly three frames at 60Hz -
         * loose enough to absorb ordinary frame jitter, tight enough that a resume costs a couple
         * of ticks instead of the entire time the app spent in the background.
         */
        const val MAX_FRAME_DELTA_SECONDS = 0.05f
    }
}
