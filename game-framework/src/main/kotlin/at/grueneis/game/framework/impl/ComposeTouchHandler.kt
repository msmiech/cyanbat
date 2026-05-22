package at.grueneis.game.framework.impl

import androidx.compose.ui.input.pointer.PointerEvent
import at.grueneis.game.framework.Input.TouchEvent

class ComposeTouchHandler : TouchHandler {
    private val isTouched = BooleanArray(20)
    private val touchX = IntArray(20)
    private val touchY = IntArray(20)
    private val touchEventPool: Pool<TouchEvent>
    private val internalTouchEvents: MutableList<TouchEvent> = ArrayList()
    private val touchEventsBuffer: MutableList<TouchEvent> = ArrayList()

    override var pointerCount = 0
        private set

    init {
        val factory = object : Pool.PoolObjectFactory<TouchEvent> {
            override fun createObject(): TouchEvent {
                return TouchEvent()
            }
        }
        touchEventPool = Pool(factory, 100)
    }

    fun onPointerEvent(event: PointerEvent, scaleX: Float, scaleY: Float) {
        synchronized(this) {
            val changes = event.changes
            pointerCount = changes.count { it.pressed }

            changes.forEach { change ->
                // Map the Compose PointerId value to a simple bounded index in the range 0..19.
                // We use standard hashing and absolute values to prevent negative indices.
                val pointerId = (Math.abs(change.id.value) % 20).toInt()
                val x = (change.position.x * scaleX).toInt()
                val y = (change.position.y * scaleY).toInt()

                val wasPressed = change.previousPressed
                val isPressed = change.pressed

                var touchEvent: TouchEvent? = null

                if (isPressed && !wasPressed) {
                    // TOUCH_DOWN
                    isTouched[pointerId] = true
                    touchX[pointerId] = x
                    touchY[pointerId] = y

                    touchEvent = touchEventPool.newObject().apply {
                        type = TouchEvent.TOUCH_DOWN
                        pointer = pointerId
                        this.x = x
                        this.y = y
                    }
                } else if (!isPressed && wasPressed) {
                    // TOUCH_UP
                    isTouched[pointerId] = false
                    touchX[pointerId] = x
                    touchY[pointerId] = y

                    touchEvent = touchEventPool.newObject().apply {
                        type = TouchEvent.TOUCH_UP
                        pointer = pointerId
                        this.x = x
                        this.y = y
                    }
                } else if (isPressed && (touchX[pointerId] != x || touchY[pointerId] != y)) {
                    // TOUCH_DRAGGED
                    touchX[pointerId] = x
                    touchY[pointerId] = y

                    touchEvent = touchEventPool.newObject().apply {
                        type = TouchEvent.TOUCH_DRAGGED
                        pointer = pointerId
                        this.x = x
                        this.y = y
                    }
                }

                if (touchEvent != null) {
                    touchEventsBuffer.add(touchEvent)
                }
            }
        }
    }

    override fun isTouchDown(pointer: Int): Boolean {
        synchronized(this) {
            return if (pointer < 0 || pointer >= 20) false else isTouched[pointer]
        }
    }

    override fun getTouchX(pointer: Int): Int {
        synchronized(this) {
            return if (pointer < 0 || pointer >= 20) 0 else touchX[pointer]
        }
    }

    override fun getTouchY(pointer: Int): Int {
        synchronized(this) {
            return if (pointer < 0 || pointer >= 20) 0 else touchY[pointer]
        }
    }

    override val touchEvents: List<TouchEvent>
        get() = refreshTouchEvents()

    private fun refreshTouchEvents(): List<TouchEvent> {
        synchronized(this) {
            val len = internalTouchEvents.size
            for (i in 0 until len) {
                touchEventPool.free(internalTouchEvents[i])
            }
            internalTouchEvents.clear()
            internalTouchEvents.addAll(touchEventsBuffer)
            touchEventsBuffer.clear()
            return internalTouchEvents
        }
    }
}
