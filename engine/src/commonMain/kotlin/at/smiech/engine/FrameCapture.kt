package at.smiech.engine

/**
 * A frame [Graphics.capture] has drawn into a picture of its own. Taking one is quick; turning it
 * into a file is not, so that is left to [encodePng], which a host may call off the game's thread.
 */
interface FrameCapture {
    /** The picture's size in pixels, the frame's own. */
    val width: Int
    val height: Int

    /** The picture as a PNG file. Slow next to a frame: tens of milliseconds on a phone. */
    fun encodePng(): ByteArray
}
