package at.grueneis.game.framework.impl

import android.view.MotionEvent
import android.view.View
import at.grueneis.game.framework.Input.TouchEvent
import java.util.*

class SingleTouchHandler(view: View, scaleX: Float, scaleY: Float) : TouchHandler {
    var isTouched = false
    var touchX = 0
    var touchY = 0
    var touchEventPool: Pool<TouchEvent>
    var touchEventBuffer: MutableList<TouchEvent> = ArrayList()
    private val internalTouchEvents: MutableList<TouchEvent> = ArrayList()
    var scaleX: Float
    var scaleY: Float

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        synchronized(this) {
            val touchEvent = touchEventPool.newObject()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchEvent.type = TouchEvent.TOUCH_DOWN
                    isTouched = true
                }
                MotionEvent.ACTION_MOVE -> {
                    touchEvent.type = TouchEvent.TOUCH_DRAGGED
                    isTouched = true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    touchEvent.type = TouchEvent.TOUCH_UP
                    isTouched = false
                }
            }
            touchX = (event.x * scaleX).toInt()
            touchEvent.x = touchX
            touchY = (event.y * scaleY).toInt()
            touchEvent.y = touchY
            touchEventBuffer.add(touchEvent)
            return true
        }
    }

    override fun isTouchDown(pointer: Int): Boolean {
        synchronized(this) {
            return if (pointer == 0) isTouched else false
        }
    }

    override fun getTouchX(pointer: Int): Int {
        synchronized(this) { return touchX }
    }

    override fun getTouchY(pointer: Int): Int {
        synchronized(this) { return touchY }
    }

    override val touchEvents: List<TouchEvent>
        get() = refreshTouchEvents()

    private fun refreshTouchEvents(): List<TouchEvent> {
        synchronized(this) {
            for (k in internalTouchEvents) {
                touchEventPool.free(k)
            }
            internalTouchEvents.clear()
            internalTouchEvents.addAll(touchEventBuffer)
            touchEventBuffer.clear()
            return internalTouchEvents
        }
    }

    override val pointerCount: Int
        get() = 1

    init {
        val factory = object : Pool.PoolObjectFactory<TouchEvent> { // anonymous implementation + override due to lack of SAM support in Kotlin
            override fun createObject(): TouchEvent { return TouchEvent() }
        }
        this.scaleX = scaleX
        this.scaleY = scaleY
        touchEventPool = Pool(factory, 100)
        view.setOnTouchListener(this)
    }
}