package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.service.EntityFactory
import at.smiech.engine.Input
import at.smiech.engine.ecs.BackgroundComponent
import at.smiech.engine.ecs.ComponentMapper
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World

class BackgroundScrollingSystem(
    private val frameBufferWidth: Int,
    private val factory: EntityFactory
) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var backgrounds: ComponentMapper<BackgroundComponent>
    private lateinit var sprites: ComponentMapper<SpriteComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        backgrounds = world.mapper(BackgroundComponent::class)
        sprites = world.mapper(SpriteComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        if (world.count(transforms, backgrounds, sprites) >= 2) return

        world.forEach(transforms, backgrounds, sprites) { id ->
            val transform = transforms.require(id)

            if (transform.rect.right - 5 < frameBufferWidth) {
                factory.createBackground(
                    frameBufferWidth.toFloat(),
                    0f,
                    frameBufferWidth.toFloat(),
                    transform.rect.height,
                    sprites.require(id).pixmap
                )
            }
        }
    }
}
