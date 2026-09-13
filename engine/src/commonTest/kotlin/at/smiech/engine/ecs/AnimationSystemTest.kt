package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakePixmap(
    override val width: Int,
    override val height: Int
) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/**
 * `enemies.png` packs three enemy strips side by side, each four frames wide, on an exact stride -
 * so the offsets `EntityFactory.srcXOf` walks are 0, 128 and 256.
 */
private const val FRAME_WIDTH = 32
private const val STRIP_FRAMES = 4
private const val SHEET_WIDTH = FRAME_WIDTH * STRIP_FRAMES * 3
private val STRIP_OFFSETS = listOf(0, 128, 256)

class AnimationSystemTest {

    private fun world(baseSrcX: Int, frameCount: Int = STRIP_FRAMES, looping: Boolean = true): Pair<World, EntityId> {
        val world = World()
        world.addSystem(AnimationSystem())
        val id = world.createEntity()
        world.addComponent(id, SpriteComponent(FakePixmap(SHEET_WIDTH, 29), baseSrcX = baseSrcX, srcWidth = FRAME_WIDTH))
        world.addComponent(id, AnimationComponent(FRAME_WIDTH, 29, frameCount, interval = 0.2f, isLooping = looping))
        return world to id
    }

    private fun srcX(world: World, id: EntityId) = world.getComponent(id, SpriteComponent::class)!!.srcX

    @Test
    fun `sprite starts on the first frame of its own strip`() {
        val (world, id) = world(baseSrcX = 137)
        assertEquals(137, srcX(world, id))
    }

    /**
     * Regression: AnimationSystem used to assign `currentFrame * frameWidth`, throwing the strip
     * offset away on the first tick. Every enemy type then drew frames 0 and 1 of the sheet, so
     * all three looked identical in game.
     */
    @Test
    fun `advancing a frame keeps the strip offset`() {
        for (baseSrcX in STRIP_OFFSETS) {
            val (world, id) = world(baseSrcX)
            world.update(0.25f, null)
            assertEquals(baseSrcX + FRAME_WIDTH, srcX(world, id), "strip at $baseSrcX advanced to the wrong frame")
        }
    }

    @Test
    fun `every frame of every strip stays inside the sheet`() {
        for (baseSrcX in STRIP_OFFSETS) {
            val (world, id) = world(baseSrcX)
            repeat(6) {
                world.update(0.25f, null)
                val x = srcX(world, id)
                assertTrue(x >= baseSrcX, "frame ran left of its strip: $x < $baseSrcX")
                assertTrue(x + FRAME_WIDTH <= SHEET_WIDTH, "frame ran past the sheet: $x + $FRAME_WIDTH > $SHEET_WIDTH")
            }
        }
    }

    @Test
    fun `looping animation wraps back to the strip start`() {
        // Read off the constants rather than written out: this used to assert 67 and 99, which
        // were the old sheet's second strip, and it broke the moment the strips moved. What it is
        // actually claiming is that a strip cycles back to its own start, whatever the layout.
        val baseSrcX = STRIP_OFFSETS[1]
        val (world, id) = world(baseSrcX)

        world.update(0.25f, null)
        assertEquals(baseSrcX + FRAME_WIDTH, srcX(world, id))

        repeat(STRIP_FRAMES - 1) { world.update(0.25f, null) }
        assertEquals(baseSrcX, srcX(world, id))
    }

    @Test
    fun `non looping animation stops on its last frame and reports finished`() {
        val (world, id) = world(baseSrcX = 0, frameCount = 3, looping = false)
        repeat(5) { world.update(0.25f, null) }
        val anim = world.getComponent(id, AnimationComponent::class)!!
        assertTrue(anim.isFinished)
        assertEquals(2, anim.currentFrame)
    }

    @Test
    fun `animation does not advance before its interval elapses`() {
        val (world, id) = world(baseSrcX = 67)
        world.update(0.05f, null)
        world.update(0.05f, null)
        assertEquals(67, srcX(world, id))
        assertFalse(world.getComponent(id, AnimationComponent::class)!!.isFinished)
    }
}
