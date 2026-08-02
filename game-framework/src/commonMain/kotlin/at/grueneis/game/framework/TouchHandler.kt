package at.grueneis.game.framework

interface TouchHandler {
    fun isTouchDown(pointer: Int): Boolean
    fun getTouchX(pointer: Int): Int
    fun getTouchY(pointer: Int): Int
    val touchEvents: List<Input.TouchEvent>
    val pointerCount: Int
}
