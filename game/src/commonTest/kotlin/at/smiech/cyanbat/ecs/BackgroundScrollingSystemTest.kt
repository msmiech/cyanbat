package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.service.EntityFactory
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.BackgroundComponent
import at.smiech.engine.ecs.LifetimeSystem
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class StripPixmap(override val width: Int, override val height: Int) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/**
 * The scrolling cave, and the one thing it has to get right: the strip must cover the frame at
 * every instant, with its tiles laid edge to edge.
 *
 * This is a regression test before it is anything else. The system used to space tiles by the
 * *framebuffer* width while the renderer drew them at the *pixmap* width, which with an 838-wide
 * image in a 480-wide frame put a hard vertical seam through the cave every 240 ticks. Nothing
 * caught it, because every entity involved was individually fine.
 */
class BackgroundScrollingSystemTest {

    private val frameWidth = 480
    private val strip = StripPixmap(1440, 320)

    private val world = World()
    private val factory = EntityFactory(world)

    init {
        world.addSystem(MovementSystem())
        world.addSystem(BackgroundScrollingSystem(frameWidth, factory))
        world.addSystem(LifetimeSystem(frameWidth))
    }

    private val transforms = world.mapper(TransformComponent::class)
    private val backgrounds = world.mapper(BackgroundComponent::class)

    private fun tiles(): List<ClosedFloatingPointRange<Float>> {
        val out = mutableListOf<ClosedFloatingPointRange<Float>>()
        world.forEach(transforms, backgrounds) { id ->
            val rect = transforms.require(id).rect
            out += rect.left..rect.right
        }
        return out.sortedBy { it.start }
    }

    @Test
    fun `a tile is the size of the picture and not the size of the frame`() {
        factory.createBackground(0f, strip)

        val tile = tiles().single()

        assertEquals(strip.width.toFloat(), tile.endInclusive - tile.start)
    }

    @Test
    fun `nothing is added while the strip still reaches the right of the frame`() {
        factory.createBackground(0f, strip)

        world.update(0.019f, null)

        assertEquals(1, tiles().size, "a second tile was laid before it was needed")
    }

    /**
     * The heart of it. A new tile starts one column inside where the last one ends, which is what
     * covers the column the blit declines to paint - not further, which would hide a column of the
     * cave, and not flush, which would show a black hairline through it.
     */
    @Test
    fun `the next tile is laid one column inside the last one`() {
        // Placed so its trailing edge has already come into view, which is the moment the system
        // is meant to react to.
        factory.createBackground(-(strip.width - frameWidth + 10).toFloat(), strip)
        val first = tiles().single()

        world.update(0.019f, null)

        val laid = tiles()
        assertEquals(2, laid.size)
        // The first tile has moved on by one tick, so compare against where it is now.
        // One column of overlap, on purpose: the blit paints a column short of its box, so a flush
        // join would leave a hairline hole. See BackgroundScrollingSystem.TILE_OVERLAP.
        assertEquals(
            laid[0].endInclusive - 1f,
            laid[1].start,
            "tiles are not laid with the one column overlap"
        )
        assertTrue(first.endInclusive < frameWidth)
    }

    /**
     * Run the thing. Over a stretch long enough to lay several tiles and cull several more, the
     * strip must never leave a gap anywhere over the frame - which is the property a player would
     * notice being broken, and the one the old spacing broke.
     */
    @Test
    fun `the strip covers the frame at every tick of a long scroll`() {
        factory.createBackground(0f, strip)

        repeat(4000) { tick ->
            world.update(0.019f, null)

            val laid = tiles()
            assertTrue(laid.isNotEmpty(), "the cave ran out at tick $tick")

            // Walk the tiles left to right and check they carry coverage across the whole frame.
            var covered = laid.first().start
            assertTrue(covered <= 0f, "the strip starts inside the frame at tick $tick: $covered")
            for (tile in laid) {
                if (tile.start > covered) {
                    throw AssertionError("gap at tick $tick: $covered to ${tile.start} in $laid")
                }
                covered = maxOf(covered, tile.endInclusive)
            }
            assertTrue(
                covered >= frameWidth,
                "the strip stops short of the frame at tick $tick: $covered in $laid",
            )
        }
    }
}
