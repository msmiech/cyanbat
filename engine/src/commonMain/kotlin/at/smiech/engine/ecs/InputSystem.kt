package at.smiech.engine.ecs

import at.smiech.engine.Controls
import at.smiech.engine.Input
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Steers player-controlled entities from pointer input, or from a keyboard or game controller.
 *
 * The bat is dragged, not nudged: the first pointer down claims it until that pointer lifts, so a
 * second finger cannot take control mid-run. While a pointer owns the bat, the bat moves by exactly
 * what the pointer moved, with no lag between finger and bat.
 *
 * A touch on the bat keeps the grab offset, so the bat does not snap its center to the fingertip.
 * "On the bat" means within [GRAB_PADDING] of the sprite: a fingertip covers far more of the
 * 640x360 frame than the sprite does, and the player cannot aim precisely at a bat their finger
 * hides.
 *
 * A touch away from the bat still steers: the bat flies over at [CATCH_UP_SPEED], and once there it
 * is dragged like any other grab.
 *
 * Held keys and a pushed stick take precedence and drop the drag while they last, since one scheme
 * names a place to be and the other a direction to go. Releasing them hands control back to the
 * next pointer event, so a mouse takes over as soon as it moves again.
 */
class PlayerInputSystem(
    private val frameBufferWidth: Int,
    private val frameBufferHeight: Int
) : GameSystem() {

    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var velocities: ComponentMapper<VelocityComponent>
    private lateinit var playerControls: ComponentMapper<PlayerControlComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        velocities = world.mapper(VelocityComponent::class)
        playerControls = world.mapper(PlayerControlComponent::class)
        healths = world.mapper(HealthComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        // A consuming read, so exactly one per update however many entities are steered.
        val touchEvents = input?.touchEvents
        val controls = input?.controls ?: Controls.None
        val moveX = controls.moveX
        val moveY = controls.moveY
        val steering = moveX != 0f || moveY != 0f

        world.forEach(transforms, velocities, playerControls, healths) { id ->
            val transform = transforms.require(id)
            val velocity = velocities.require(id)
            val control = playerControls.require(id)

            if (!healths.require(id).alive) {
                // A dead bat takes no input and is not moved here; DeathThroesComponent owns its
                // fall. This only releases the finger that was steering.
                control.releaseDrag()
                return@forEach
            }

            if (control.hitCooldown > 0f) {
                control.hitCooldown -= deltaTime
            }

            if (input != null) {
                dropLiftedPointer(input, control)
                if (touchEvents != null) trackPointer(input, touchEvents, transform.rect, control)
            }
            velocity.velocity = if (steering) {
                control.releaseDrag()
                steer(transform.rect, moveX, moveY, deltaTime)
            } else {
                follow(transform.rect, control, deltaTime)
            }
        }
    }

    /**
     * The step a held direction asks for, clamped to the framebuffer.
     *
     * Speed is capped by the combined deflection, not per axis, so a diagonal is not faster. A
     * stick keeps its own magnitude (half pushed is half speed), while two keys at right angles, 1
     * each, are scaled back to one unit between them.
     */
    private fun steer(rect: Rect, moveX: Float, moveY: Float, deltaTime: Float): Vector2 {
        val deflection = sqrt(moveX * moveX + moveY * moveY)
        val scale = DIRECTIONAL_SPEED * deltaTime / max(deflection, 1f)
        return clampToFrame(rect, moveX * scale, moveY * scale)
    }

    /**
     * Releases a pointer that has lifted without this system seeing it lift.
     *
     * The release normally arrives as a TOUCH_UP, but events are a consuming read and this system
     * only gets them while the world ticks. An overlay opening mid-drag (the level-up dialog, the
     * pause screen) reads them instead, and without this the bat stayed bound to a vanished pointer
     * and ignored every new touch, which arrives under a different pointer id.
     */
    private fun dropLiftedPointer(input: Input, control: PlayerControlComponent) {
        val pointer = control.activePointer
        if (pointer == PlayerControlComponent.NO_POINTER || !control.pointerHeld) return
        if (!input.isTouchDown(pointer)) control.releaseDrag()
    }

    /**
     * Folds this update's events into the entity's drag state: which pointer owns the bat, where
     * that pointer now is, and how the bat sits under it.
     */
    private fun trackPointer(
        input: Input,
        touchEvents: List<Input.TouchEvent>,
        rect: Rect,
        control: PlayerControlComponent
    ) {
        for (eventIndex in touchEvents.indices) {
            val event = touchEvents[eventIndex]
            val x = event.x.toFloat()
            val y = event.y.toFloat()

            if (event.type == Input.TouchEvent.TOUCH_UP) {
                if (event.pointer == control.activePointer) control.releaseDrag()
                continue
            }

            // TOUCH_DOWN and TOUCH_DRAGGED both claim a free bat. Claiming on a
            // drag keeps a desktop mouse working: it reports motion without a
            // button, steering without ever going down.
            if (control.activePointer == PlayerControlComponent.NO_POINTER) {
                control.beginDrag(event.pointer, x, y, rect)
                control.pointerHeld = input.isTouchDown(event.pointer)
            }
            if (event.pointer == control.activePointer) {
                control.targetX = x
                control.targetY = y
            }
        }
    }

    /**
     * The step that takes the bat to where its pointer wants it, clamped to the framebuffer.
     *
     * [MovementSystem] applies velocity as a straight per-update offset, so what comes back is a
     * distance in framebuffer pixels for this tick, not pixels per second.
     */
    private fun follow(
        rect: Rect,
        control: PlayerControlComponent,
        deltaTime: Float
    ): Vector2 {
        if (control.activePointer == PlayerControlComponent.NO_POINTER) return Vector2.Zero

        var dx = control.targetX + control.grabOffsetX - rect.centerX
        var dy = control.targetY + control.grabOffsetY - rect.centerY

        // A locked-on drag is deliberately uncapped: whatever the finger did between two ticks is
        // what the player meant, and capping it would leave the bat behind the fingertip exactly
        // when the player swipes hardest. The cost is that a hard flick can cross an obstacle
        // without the per-frame overlap test seeing it.
        if (control.dragging) return clampToFrame(rect, dx, dy)

        // Still travelling to a touch that landed away from the bat: a journey, so it is paced.
        val limit = CATCH_UP_SPEED * deltaTime
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > limit) {
            val scale = limit / distance
            dx *= scale
            dy *= scale
        } else {
            // Arrived: from here the bat is dragged like one grabbed outright.
            control.dragging = true
        }
        return clampToFrame(rect, dx, dy)
    }

    /**
     * Trims a move to what keeps the sprite on screen.
     *
     * Per axis rather than all or nothing, so a bat held against one edge still tracks the pointer
     * along the other.
     */
    private fun clampToFrame(rect: Rect, dx: Float, dy: Float) = Vector2(
        dx.coerceIn(-rect.left, frameBufferWidth - rect.right),
        dy.coerceIn(-rect.top, frameBufferHeight - rect.bottom)
    )

    private fun PlayerControlComponent.beginDrag(pointer: Int, x: Float, y: Float, rect: Rect) {
        activePointer = pointer
        dragging = rect.inflate(GRAB_PADDING).contains(x, y)
        // Only a grab keeps an offset. A tap in open space means "come here", so the bat centers
        // on it.
        grabOffsetX = if (dragging) rect.centerX - x else 0f
        grabOffsetY = if (dragging) rect.centerY - y else 0f
        targetX = x
        targetY = y
    }

    private fun PlayerControlComponent.releaseDrag() {
        activePointer = PlayerControlComponent.NO_POINTER
        pointerHeld = false
        dragging = false
        grabOffsetX = 0f
        grabOffsetY = 0f
    }

    companion object {
        /**
         * How far outside the sprite a touch still counts as grabbing it, in framebuffer pixels:
         * roughly a fingertip's width once the 640x360 frame is scaled up to a phone screen.
         */
        const val GRAB_PADDING = 27f

        /** Speed the bat flies at when a touch lands away from it, in framebuffer pixels/second. */
        const val CATCH_UP_SPEED = 420f

        /**
         * Speed a fully held direction moves the bat, in framebuffer pixels per second.
         *
         * The same as [CATCH_UP_SPEED]: both are the bat flying under its own power, and keyboard
         * and touch players should cross the screen at the same rate.
         */
        const val DIRECTIONAL_SPEED = 420f
    }
}
