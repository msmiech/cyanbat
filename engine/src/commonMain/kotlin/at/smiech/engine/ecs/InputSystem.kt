package at.smiech.engine.ecs

import at.smiech.engine.Input
import at.smiech.engine.math.Vector2

class PlayerInputSystem(
    private val frameBufferWidth: Int,
    private val frameBufferHeight: Int
) : GameSystem() {

    private val TICK = 0.009f
    private var tickTime = 0f

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
        val touchEvents = input?.touchEvents ?: return

        world.forEach(transforms, velocities, playerControls, healths) { id ->
            val transform = transforms.require(id)
            val velocity = velocities.require(id)
            val health = healths.require(id)
            val control = playerControls.require(id)

            if (health.alive) {
                if (control.hitCooldown > 0f) {
                    control.hitCooldown -= deltaTime
                }

                var vx = 0f
                var vy = 0f

                if (touchEvents.isNotEmpty()) {
                    tickTime += deltaTime
                    // The tick only gates *whether* the touch is read: nothing inside moves the
                    // bat, so every extra pass would derive the same vx/vy from the same rect.
                    // Drain the accumulator, then read the events once.
                    val ticked = tickTime > TICK
                    while (tickTime > TICK) {
                        tickTime -= TICK
                    }
                    if (ticked) {
                        val rect = transform.rect
                        for (eventIndex in touchEvents.indices) {
                            val event = touchEvents[eventIndex]
                            if (event.type == Input.TouchEvent.TOUCH_DRAGGED) {
                                if (event.x > rect.centerX) {
                                    if (rect.right < frameBufferWidth) vx = 3f
                                } else {
                                    if (rect.left > 0) vx = -3f
                                }

                                if (event.y > rect.centerY) {
                                    if (rect.bottom < frameBufferHeight) vy = 3f
                                } else {
                                    if (rect.top > 0) vy = -3f
                                }
                            }
                        }
                    }
                }
                velocity.velocity = Vector2(vx, vy)
            } else {
                velocity.velocity = Vector2(0f, 2f)
            }
        }
    }
}
