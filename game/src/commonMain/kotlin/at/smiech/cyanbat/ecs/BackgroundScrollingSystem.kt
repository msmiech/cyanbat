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
 * Keeps the scrolling cave covering the frame, by laying the next tile down the moment the last
 * one's trailing edge comes into view.
 *
 * Tiles are placed edge to edge - the new one starts exactly where the old one ends - so the join
 * is the strip meeting itself. `background.png` is generated to be periodic across its width, which
 * makes that join invisible; the two halves are one feature and neither works alone.
 *
 * The old version spaced tiles by the *framebuffer* width while the renderer drew them at the
 * *pixmap* width. With an 838-wide image in a 480-wide frame that put a hard vertical seam through
 * the cave every 240 ticks: at the join, one copy's column 480 sat against the next one's column 0,
 * and no amount of making the artwork tileable would have helped, because 480 was never where the
 * picture repeated.
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
        // Only the rightmost tile matters: it is the one whose trailing edge decides whether the
        // strip still reaches the right of the frame. Counting tiles, as this used to, answers a
        // different question - and answers it wrongly the moment a tile is wider than the frame.
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
         * Tiles are laid one column *before* the last one's trailing edge, not flush against it.
         *
         * The blit paints one column short of its destination box - the `- 1` in
         * `DesktopGraphics.drawPixmap`, which is deliberate and mirrors Android exactly - so a tile
         * whose box runs to `left + width` only ever colors up to `left + width - 1`. Laid flush,
         * that leaves a one pixel hole at every join: a black hairline scrolling down the middle of
         * the cave, which is precisely the artifact this whole change is meant to remove.
         *
         * Starting one column early covers the hole with the strip's first column. On a strip built
         * to be periodic that is the column which belongs there anyway, so the overlap costs a
         * single duplicated column out of 1440 and nothing visible.
         */
        const val TILE_OVERLAP = 1f
    }
}
