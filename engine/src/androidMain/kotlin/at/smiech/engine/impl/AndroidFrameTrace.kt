package at.smiech.engine.impl

import androidx.tracing.Trace
import at.smiech.engine.FrameTrace

/**
 * [FrameTrace] as sections of the system trace, which Perfetto and Android Studio's profiler record
 * alongside the platform's own rendering. Close to free while nothing is recording.
 */
object AndroidFrameTrace : FrameTrace {
    override fun begin(name: String) = Trace.beginSection(name)

    override fun end() = Trace.endSection()
}
