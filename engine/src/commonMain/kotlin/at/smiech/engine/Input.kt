package at.smiech.engine

/**
 * Pointer and controller input, as a host hands it to the game. Pointer positions
 * are in frame pixels.
 */
interface Input {
    /** One pointer event. Pooled and reused, so read it within the frame that delivers it. */
    class TouchEvent {
        var type = 0
        var x = 0
        var y = 0
        var pointer = 0

        /**
         * Set on a [TOUCH_UP] the system made rather than the finger, as when Android's back
         * gesture or the notification shade takes over a touch that began on the game. Whatever
         * followed the touch lets go, but the event is not a tap.
         */
        var canceled = false

        companion object {
            const val TOUCH_DOWN = 0
            const val TOUCH_UP = 1
            const val TOUCH_DRAGGED = 2
        }
    }

    fun isTouchDown(pointer: Int): Boolean
    fun getTouchX(pointer: Int): Int
    fun getTouchY(pointer: Int): Int

    /** The pointer events since the last read; see [TouchHandler.touchEvents]. */
    val touchEvents: List<TouchEvent>?
    val pointerCount: Int

    /**
     * Keyboard and game controller state. Defaults to [Controls.None], so a host that offers
     * neither, and a test that only drives touch, need not supply it.
     */
    val controls: Controls get() = Controls.None
}
