package at.smiech.engine.impl

import at.smiech.engine.Controls
import at.smiech.engine.Input
import at.smiech.engine.Input.TouchEvent

/**
 * [Input] for Android. Pointer state comes from the shared [PointerTouchHandler], fed by the
 * activity's Compose canvas; keys, game controllers and Back arrive through [controls].
 */
class AndroidInput(
    val touchHandler: PointerTouchHandler,
    override val controls: Controls = Controls.None,
) : Input {

    override fun isTouchDown(pointer: Int): Boolean {
        return touchHandler.isTouchDown(pointer)
    }

    override fun getTouchX(pointer: Int): Int {
        return touchHandler.getTouchX(pointer)
    }

    override fun getTouchY(pointer: Int): Int {
        return touchHandler.getTouchY(pointer)
    }

    override val touchEvents: List<TouchEvent>
        get() = touchHandler.touchEvents
    override val pointerCount: Int
        get() = touchHandler.pointerCount
}
