package at.smiech.engine.impl

import at.smiech.engine.Input
import at.smiech.engine.TouchHandler
import kotlin.math.abs

/**
 * Turns raw pointer state into the [Input.TouchEvent] stream the game reads.
 *
 * Platform-neutral: hosts feed it through [onPointer], via a Compose adapter on Android or an
 * AWT adapter on desktop.
 *
 * Threading: NOT synchronised, deliberately. On every host the pointer callbacks and the game
 * loop run on the same thread (the Compose UI thread on Android, the same on Compose Desktop).
 * If a host ever drives the loop from a separate thread, locking has to come back - and would
 * then have to live in a JVM-only source set, since `synchronized` is not available in common
 * code.
 */
class PointerTouchHandler(
    /**
     * When true, pointer motion counts as a drag even with no button held.
     *
     * Touch screens only report a position while a finger is down, so the game steers on
     * TOUCH_DRAGGED. A mouse reports motion continuously, and requiring a held button to fly is
     * not the desktop idiom - so desktop hosts turn this on and the bat follows the cursor.
     */
    private val treatMotionAsDrag: Boolean = false,
) : TouchHandler {
    private val isTouched = BooleanArray(MAX_POINTERS)
    private val touchX = IntArray(MAX_POINTERS)
    private val touchY = IntArray(MAX_POINTERS)
    private val touchEventPool = Pool(
        object : Pool.PoolObjectFactory<Input.TouchEvent> {
            override fun createObject() = Input.TouchEvent()
        },
        POOL_SIZE
    )
    private val internalTouchEvents: MutableList<Input.TouchEvent> = ArrayList()
    private val touchEventsBuffer: MutableList<Input.TouchEvent> = ArrayList()

    override var pointerCount = 0

    /**
     * @param rawId host pointer id; folded into a bounded slot, so ids need not be small.
     * @param scaleX/scaleY host pixels -> framebuffer pixels.
     */
    fun onPointer(
        rawId: Long,
        x: Float,
        y: Float,
        pressed: Boolean,
        previouslyPressed: Boolean,
        scaleX: Float,
        scaleY: Float
    ) {
        val pointer = (abs(rawId) % MAX_POINTERS).toInt()
        val scaledX = (x * scaleX).toInt()
        val scaledY = (y * scaleY).toInt()

        val type = when {
            pressed && !previouslyPressed -> Input.TouchEvent.TOUCH_DOWN
            !pressed && previouslyPressed -> Input.TouchEvent.TOUCH_UP
            (pressed || treatMotionAsDrag) &&
                (touchX[pointer] != scaledX || touchY[pointer] != scaledY) ->
                Input.TouchEvent.TOUCH_DRAGGED
            else -> return
        }

        if (type != Input.TouchEvent.TOUCH_DRAGGED) {
            isTouched[pointer] = type == Input.TouchEvent.TOUCH_DOWN
        }
        touchX[pointer] = scaledX
        touchY[pointer] = scaledY

        touchEventsBuffer.add(
            touchEventPool.newObject().apply {
                this.type = type
                this.pointer = pointer
                this.x = scaledX
                this.y = scaledY
            }
        )
    }

    override fun isTouchDown(pointer: Int) =
        if (pointer !in 0..<MAX_POINTERS) false else isTouched[pointer]

    override fun getTouchX(pointer: Int) = if (pointer !in 0..<MAX_POINTERS) 0 else touchX[pointer]

    override fun getTouchY(pointer: Int) = if (pointer !in 0..<MAX_POINTERS) 0 else touchY[pointer]

    /**
     * Consuming read: each event is returned exactly once, then recycled on the next call. A
     * frame that reads this twice sees an empty list the second time.
     */
    override val touchEvents: List<Input.TouchEvent>
        get() {
            internalTouchEvents.forEach { touchEventPool.free(it) }
            internalTouchEvents.clear()
            internalTouchEvents.addAll(touchEventsBuffer)
            touchEventsBuffer.clear()
            return internalTouchEvents
        }

    companion object {
        private const val MAX_POINTERS = 20
        private const val POOL_SIZE = 100
    }
}
