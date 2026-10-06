package at.smiech.engine.impl

import at.smiech.engine.Input
import at.smiech.engine.TouchHandler
import kotlin.math.abs

/**
 * Turns raw pointer state into the [Input.TouchEvent] stream the game reads.
 *
 * Platform-neutral: hosts feed it through [onPointer], via the Compose adapter on both platforms.
 *
 * Deliberately not synchronized: on every host the pointer callbacks and the game loop run on the
 * Compose UI thread. A host that drove the loop from another thread would need locking, and in a
 * JVM-only source set, since common code has no `synchronized`.
 */
class PointerTouchHandler(
    /**
     * When true, pointer motion counts as a drag even with no button held.
     *
     * Touch screens only report a position while a finger is down, so the game steers on
     * TOUCH_DRAGGED. A mouse reports motion continuously, and holding a button to fly is not the
     * desktop idiom, so desktop hosts turn this on and the bat follows the cursor.
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
     * Records one pointer's state and queues the event it amounts to, if any.
     *
     * @param rawId host pointer id; folded into a bounded slot, so ids need not be small.
     * @param x the pointer in framebuffer pixels, already mapped from the host's view; see
     *   [FrameFit]. A position outside the framebuffer, over a bar beside it, is passed on as is.
     * @param y as [x].
     * @param canceled the system took the pointer away rather than the finger lifting; see
     *   [Input.TouchEvent.canceled].
     */
    fun onPointer(
        rawId: Long,
        x: Int,
        y: Int,
        pressed: Boolean,
        previouslyPressed: Boolean,
        canceled: Boolean = false,
    ) {
        val pointer = (abs(rawId) % MAX_POINTERS).toInt()

        val type = when {
            pressed && !previouslyPressed -> Input.TouchEvent.TOUCH_DOWN
            !pressed && previouslyPressed -> Input.TouchEvent.TOUCH_UP
            (pressed || treatMotionAsDrag) &&
                    (touchX[pointer] != x || touchY[pointer] != y) ->
                Input.TouchEvent.TOUCH_DRAGGED

            else -> return
        }

        if (type != Input.TouchEvent.TOUCH_DRAGGED) {
            isTouched[pointer] = type == Input.TouchEvent.TOUCH_DOWN
        }
        touchX[pointer] = x
        touchY[pointer] = y

        touchEventsBuffer.add(
            touchEventPool.newObject().apply {
                this.type = type
                this.pointer = pointer
                this.x = x
                this.y = y
                // Set on every event, pooled ones included, so a recycled cancel does not live on.
                this.canceled = canceled && type == Input.TouchEvent.TOUCH_UP
            }
        )
    }

    override fun isTouchDown(pointer: Int) =
        pointer in 0..<MAX_POINTERS && isTouched[pointer]

    override fun getTouchX(pointer: Int) = if (pointer !in 0..<MAX_POINTERS) 0 else touchX[pointer]

    override fun getTouchY(pointer: Int) = if (pointer !in 0..<MAX_POINTERS) 0 else touchY[pointer]

    /**
     * Consuming read: each event is returned exactly once and recycled on the next call, so a
     * second read in the same frame returns an empty list.
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
