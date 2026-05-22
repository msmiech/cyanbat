package at.grueneis.game.framework.impl

import at.grueneis.game.framework.Input.TouchEvent

interface TouchHandler {
    fun isTouchDown(pointer: Int): Boolean
    fun getTouchX(pointer: Int): Int
    fun getTouchY(pointer: Int): Int
    val touchEvents: List<TouchEvent>
    val pointerCount: Int
}