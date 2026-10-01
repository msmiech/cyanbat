package at.smiech.engine

/**
 * Marks the parts of a frame for a platform's profiler, so a recorded trace shows how long each one
 * took. Platforms without a profiler to mark supply [None].
 *
 * A pair of calls rather than one taking a block, because it runs every frame: a block capturing
 * the frame's delta would be an allocation per frame.
 */
interface FrameTrace {
    /** Opens a section called [name], to be closed by [end] on the same thread. */
    fun begin(name: String)

    /** Closes the section most recently opened by [begin]. */
    fun end()

    companion object {
        val None: FrameTrace = object : FrameTrace {
            override fun begin(name: String) = Unit
            override fun end() = Unit
        }
    }
}
