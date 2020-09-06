package at.grueneis.game.framework

interface Input {
    class TouchEvent {
        var type = 0
        var x = 0
        var y = 0
        var pointer = 0

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
}