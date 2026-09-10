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
 * The bat is *dragged*, not nudged: the first pointer to go down claims it and keeps it until that
 * pointer lifts, so a second finger landing mid-run cannot wrestle control away. While a pointer
 * owns the bat the sprite is pinned under it - the system moves the bat by exactly what the pointer
 * moved, so there is no lag between finger and bat to fight.
 *
 * A touch that lands on the bat keeps the grab offset, so the bat does not snap its centre to the
 * fingertip that just caught it. "On the bat" means within [GRAB_PADDING] of the sprite: a
 * fingertip covers far more of a 480x320 framebuffer than a 45x40 sprite does, and without the
 * padding the player would have to aim precisely at a bat their own finger is hiding.
 *
 * A touch that lands away from the bat still steers - that is how the game played before it was
 * draggable, and it is the only thing a tap on the far side of the screen can mean. The bat flies
 * over at [CATCH_UP_SPEED], and once it arrives it is dragged like any other grab.
 *
 * Held keys and a pushed stick outrank any of that, and drop the drag while they last. The two
 * schemes cannot share a frame - one names a place to be, the other a direction to go - and a
 * player reaching for the keyboard has stopped meaning whatever the mouse last said. Letting go
 * hands control back to the next pointer event, so a mouse takes over the moment it moves again.
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
        // Consuming read, so exactly one per update however many entities are steered - a second
        // read this frame would come back empty.
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
                control.releaseDrag()
                velocity.velocity = DEATH_DRIFT
                return@forEach
            }

            if (control.hitCooldown > 0f) {
                control.hitCooldown -= deltaTime
            }

            if (touchEvents != null) {
                trackPointer(touchEvents, transform.rect, control)
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
     * Speed is capped by the *combined* deflection, not per axis, so a diagonal is not the
     * fastest way across the screen. A stick reports its own magnitude and keeps it - pushing it
     * halfway is a request to go half speed - while two keys at right angles read 1 each and are
     * scaled back to a single unit between them.
     */
    private fun steer(rect: Rect, moveX: Float, moveY: Float, deltaTime: Float): Vector2 {
        val deflection = sqrt(moveX * moveX + moveY * moveY)
        val scale = DIRECTIONAL_SPEED * deltaTime / max(deflection, 1f)
        return clampToFrame(rect, moveX * scale, moveY * scale)
    }

    /**
     * Folds this update's events into the entity's drag state: which pointer owns the bat, where
     * that pointer now is, and how the bat sits under it.
     */
    private fun trackPointer(
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

            // TOUCH_DOWN and TOUCH_DRAGGED both claim a free bat. Claiming on a drag is what keeps
            // a desktop mouse working: hosts there report motion without a button, so a pointer
            // can steer without ever having gone down.
            if (control.activePointer == PlayerControlComponent.NO_POINTER) {
                control.beginDrag(event.pointer, x, y, rect)
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

        // A locked-on drag is deliberately uncapped. A finger is a physical object, so whatever it
        // did between two ticks is what the player meant; capping it would rubber-band the bat
        // away from the fingertip exactly when the player swipes hardest, which is the lag this
        // control exists to remove. The cost is that a hard flick can cross an obstacle without
        // the frame-by-frame overlap test ever seeing it - cheap next to the bat lagging a swipe.
        if (control.dragging) return clampToFrame(rect, dx, dy)

        // Still travelling to a touch that landed away from the bat, which is a journey rather
        // than a drag, so it is paced.
        val limit = CATCH_UP_SPEED * deltaTime
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > limit) {
            val scale = limit / distance
            dx *= scale
            dy *= scale
        } else {
            // Arrived. The bat is under the pointer now, so from here it is dragged like one that
            // was grabbed outright.
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
        // Only a grab keeps an offset. A tap in open space means "come here", and centring the bat
        // on it is the least surprising reading of that.
        grabOffsetX = if (dragging) rect.centerX - x else 0f
        grabOffsetY = if (dragging) rect.centerY - y else 0f
        targetX = x
        targetY = y
    }

    private fun PlayerControlComponent.releaseDrag() {
        activePointer = PlayerControlComponent.NO_POINTER
        dragging = false
        grabOffsetX = 0f
        grabOffsetY = 0f
    }

    companion object {
        /**
         * How far outside the sprite a touch still counts as grabbing it, in framebuffer pixels.
         *
         * Roughly a fingertip's width once the 480px framebuffer is stretched over a phone screen,
         * which is what makes the bat catchable without looking away from the game.
         */
        const val GRAB_PADDING = 24f

        /** Speed the bat flies at when a touch lands away from it, in framebuffer pixels/second. */
        const val CATCH_UP_SPEED = 420f

        /**
         * Speed a fully held direction moves the bat, in framebuffer pixels per second.
         *
         * Deliberately the same number as [CATCH_UP_SPEED]: both are the bat travelling under its
         * own power rather than pinned to a finger, and a keyboard player and a touch player
         * crossing the screen at different rates would be two different games.
         */
        const val DIRECTIONAL_SPEED = 420f

        /** A dead bat drops out of the frame under its own weight; input no longer reaches it. */
        private val DEATH_DRIFT = Vector2(0f, 2f)
    }
}
