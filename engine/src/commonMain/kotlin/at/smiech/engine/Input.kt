package at.smiech.engine

interface Input {
    class TouchEvent {
        var type = 0
        var x = 0
        var y = 0
        var pointer = 0

        /**
         * Set on a [TOUCH_UP] the system made rather than the finger: Android's back gesture, or
         * the notification shade, taking over a touch that began on the game. The touch is over all
         * the same, so whatever was following it lets go - but nobody lifted a finger on anything,
         * so it is no tap.
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
    val accelX: Float
    val accelY: Float
    val accelZ: Float
    val touchEvents: List<TouchEvent>?
    val pointerCount: Int

    /**
     * Keyboard and game controller state. Defaults to [Controls.None], so a host that offers
     * neither - and a test that only drives touch - needs no boilerplate.
     */
    val controls: Controls get() = Controls.None
}
