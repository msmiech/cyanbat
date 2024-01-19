package at.grueneis.game.framework.impl

import android.view.MotionEvent
import android.view.View
import at.grueneis.game.framework.Input.TouchEvent
import java.util.*

class MultiTouchHandler(view: View, scaleX: Float, scaleY: Float) : TouchHandler {
    private val isTouched = BooleanArray(20)
    private val touchX = IntArray(20)
    private val touchY = IntArray(20)
    private val touchEventPool: Pool<TouchEvent>
    private val internalTouchEvents: MutableList<TouchEvent> = ArrayList()
    private val touchEventsBuffer: MutableList<TouchEvent> = ArrayList()
    private val scaleX: Float
    private val scaleY: Float
    override var pointerCount = 0
        private set

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        synchronized(this) {
            val action = event.action and MotionEvent.ACTION_MASK
            var pointerIndex = event.action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
            var pointerId = event.getPointerId(pointerIndex)
            var touchEvent: TouchEvent?
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    touchEvent = touchEventPool.newObject()
                    touchEvent.type = TouchEvent.TOUCH_DOWN
                    touchEvent.pointer = pointerId
                    run {
                        touchX[pointerId] = (event
                                .getX(pointerIndex) * scaleX).toInt()
                        touchEvent!!.x = touchX[pointerId]
                    }
                    run {
                        touchY[pointerId] = (event
                                .getY(pointerIndex) * scaleY).toInt()
                        touchEvent!!.y = touchY[pointerId]
                    }
                    isTouched[pointerId] = true
                    touchEventsBuffer.add(touchEvent)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    touchEvent = touchEventPool.newObject()
                    touchEvent.type = TouchEvent.TOUCH_UP
                    touchEvent.pointer = pointerId
                    run {
                        touchX[pointerId] = (event
                                .getX(pointerIndex) * scaleX).toInt()
                        touchEvent!!.x = touchX[pointerId]
                    }
                    run {
                        touchY[pointerId] = (event
                                .getY(pointerIndex) * scaleY).toInt()
                        touchEvent!!.y = touchY[pointerId]
                    }
                    isTouched[pointerId] = false
                    touchEventsBuffer.add(touchEvent)
                }
                MotionEvent.ACTION_MOVE -> {
                    val pointerCount = event.pointerCount
                    var i = 0
                    while (i < pointerCount) {
                        pointerIndex = i
                        pointerId = event.getPointerId(pointerIndex)
                        touchEvent = touchEventPool.newObject()
                        touchEvent.type = TouchEvent.Companion.TOUCH_DRAGGED
                        touchEvent.pointer = pointerId
                        touchX[pointerId] = (event
                                .getX(pointerIndex) * scaleX).toInt()
                        touchEvent.x = touchX[pointerId]
                        touchY[pointerId] = (event
                                .getY(pointerIndex) * scaleY).toInt()
                        touchEvent.y = touchY[pointerId]
                        touchEventsBuffer.add(touchEvent)
                        i++
                    }
                }
            }
            pointerCount = event.pointerCount
            return true
        }
    }

    override fun isTouchDown(pointer: Int): Boolean {
        synchronized(this) { return if (pointer < 0 || pointer >= 20) false else isTouched[pointer] }
    }

    override fun getTouchX(pointer: Int): Int {
        synchronized(this) { return if (pointer < 0 || pointer >= 20) 0 else touchX[pointer] }
    }

    override fun getTouchY(pointer: Int): Int {
        synchronized(this) { return if (pointer < 0 || pointer >= 20) 0 else touchY[pointer] }
    }

    override val touchEvents: List<TouchEvent>
        get() = refreshTouchEvents()

    private fun refreshTouchEvents(): List<TouchEvent> {
        synchronized(this) {
            val len = internalTouchEvents.size
            for (i in 0 until len) touchEventPool.free(internalTouchEvents[i])
            internalTouchEvents.clear()
            internalTouchEvents.addAll(touchEventsBuffer)
            touchEventsBuffer.clear()
            return internalTouchEvents
        }
    }

    init {
        val factory = object : Pool.PoolObjectFactory<TouchEvent> { // anonymous implementation + override due to lack of SAM support in Kotlin
            override fun createObject(): TouchEvent { return TouchEvent() }
        }
        touchEventPool = Pool(factory, 100)
        view.setOnTouchListener(this)
        this.scaleX = scaleX
        this.scaleY = scaleY
    }
}