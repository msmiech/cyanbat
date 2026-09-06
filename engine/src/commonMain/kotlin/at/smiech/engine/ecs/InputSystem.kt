package at.smiech.engine.ecs

import at.smiech.engine.Input
import at.smiech.engine.math.Vector2

class PlayerInputSystem(
    private val frameBufferWidth: Int,
    private val frameBufferHeight: Int
) : GameSystem() {
    
    private val TICK = 0.009f
    private var tickTime = 0f

    override fun update(world: World, deltaTime: Float, input: Input?) {
        val playerEntities = world.query(
            TransformComponent::class, 
            VelocityComponent::class, 
            PlayerControlComponent::class,
            HealthComponent::class
        )
        
        val touchEvents = input?.touchEvents ?: return
        
        playerEntities.forEach { id ->
            val transform = world.getComponent(id, TransformComponent::class)!!
            val velocity = world.getComponent(id, VelocityComponent::class)!!
            val health = world.getComponent(id, HealthComponent::class)!!
            val control = world.getComponent(id, PlayerControlComponent::class)!!
            
            if (health.alive) {
                if (control.hitCooldown > 0f) {
                    control.hitCooldown -= deltaTime
                }
                
                var vx = 0f
                var vy = 0f
                
                if (touchEvents.isNotEmpty()) {
                    tickTime += deltaTime
                    while (tickTime > TICK) {
                        tickTime -= TICK
                        touchEvents.forEach { event ->
                            if (event.type == Input.TouchEvent.TOUCH_DRAGGED) {
                                if (event.x > transform.rect.centerX) {
                                    if (transform.rect.right < frameBufferWidth) vx = 3f
                                } else {
                                    if (transform.rect.left > 0) vx = -3f
                                }
                                
                                if (event.y > transform.rect.centerY) {
                                    if (transform.rect.bottom < frameBufferHeight) vy = 3f
                                } else {
                                    if (transform.rect.top > 0) vy = -3f
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
