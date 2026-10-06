package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.service.EntityFactory
import at.smiech.engine.Input
import at.smiech.engine.ecs.BackgroundComponent
import at.smiech.engine.ecs.ComponentMapper
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World

/**
 * Keeps a scrolling background strip covering the frame by laying the next tile down as soon as the
 * last one's trailing edge comes into view.
 *
 * Tiles are placed edge to edge (see [TILE_OVERLAP]), so each join is the strip meeting itself; the
 * strip images are generated periodic across their width, which makes the join invisible.
 */
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
        // Only the rightmost tile matters: its trailing edge decides whether the strip still
        // reaches the right of the frame.
        var trailingEdge = Float.NEGATIVE_INFINITY
        var last = -1
        world.forEach(transforms, backgrounds, sprites) { id ->
            val right = transforms.require(id).rect.right
            if (right > trailingEdge) {
                trailingEdge = right
                last = id
            }
        }
        if (last < 0 || trailingEdge >= frameBufferWidth) return

        factory.createBackground(trailingEdge - TILE_OVERLAP, sprites.require(last).pixmap)
    }

    private companion object {
        /**
         * Tiles are laid one column before the last one's trailing edge, not flush against it.
         *
         * The blit paints one column short of its box (the deliberate `- 1` in
         * `ComposeGraphics.drawPixmap`), so a flush tile would leave a one-pixel black seam at
         * every join. Starting a column early covers it with the strip's first column, which on a
         * periodic strip is the column that belongs there anyway.
         */
        const val TILE_OVERLAP = 1f
    }
}
