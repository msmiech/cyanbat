package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.service.EntityFactory
import at.smiech.engine.Input
import at.smiech.engine.ecs.BackgroundComponent
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World

class BackgroundScrollingSystem(
    private val frameBufferWidth: Int,
    private val factory: EntityFactory
) : GameSystem() {
    override fun update(world: World, deltaTime: Float, input: Input?) {
        val backgrounds = world.query(TransformComponent::class, BackgroundComponent::class, SpriteComponent::class)
        
        if (backgrounds.size < 2) {
            backgrounds.forEach { id ->
                val transform = world.getComponent(id, TransformComponent::class)!!
                val sprite = world.getComponent(id, SpriteComponent::class)!!
                
                if (transform.rect.right - 5 < frameBufferWidth) {
                    factory.createBackground(
                        frameBufferWidth.toFloat(),
                        0f,
                        frameBufferWidth.toFloat(),
                        transform.rect.height,
                        sprite.pixmap
                    )
                }
            }
        }
    }
}
