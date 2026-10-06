package at.smiech.engine

/** Tracks pointers on the game's frame, in frame pixels, and queues their events for the game. */
interface TouchHandler {
    fun isTouchDown(pointer: Int): Boolean
    fun getTouchX(pointer: Int): Int
    fun getTouchY(pointer: Int): Int

    /** The events queued since the previous read. Each event is returned by one read only. */
    val touchEvents: List<Input.TouchEvent>
    val pointerCount: Int
}
