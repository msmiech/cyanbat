package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One rectangle the system asked for. */
private data class DrawnRect(val x: Int, val y: Int, val width: Int, val height: Int, val color: Int)

private class RectRecordingGraphics : Graphics {
    val rects = mutableListOf<DrawnRect>()

    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        rects += DrawnRect(x, y, width, height, color)
    }

    override fun newPixmap(filename: String, format: Graphics.PixmapFormat) = throw UnsupportedOperationException()
    override fun clear(color: Int) = Unit
    override fun drawPixel(x: Int, y: Int, color: Int) = Unit
    override fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) = Unit
    override fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) = Unit
    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int
    ) = Unit

    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int
    ) = Unit

    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, rotationDegrees: Float
    ) = Unit

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) = Unit
    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) = Unit
    override val width = WORLD_WIDTH
    override val height = WORLD_HEIGHT
}

private const val WORLD_WIDTH = 480
private const val WORLD_HEIGHT = 320

class HealthBarSystemTest {

    private val world = World().apply { addSystem(HealthBarSystem(WORLD_HEIGHT)) }

    /** A 40 wide entity, so a filled width reads straight off as a percentage times 0.4. */
    private fun spawn(
        hitPoints: Int,
        maxHitPoints: Int = 100,
        top: Float = 100f,
        withBar: Boolean = true,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(60f, top, 40f, 20f)))
        world.addComponent(id, HealthComponent(hitPoints, maxHitPoints))
        if (withBar) world.addComponent(id, HealthBarComponent(height = 3f, offsetY = 2f))
        return id
    }

    private fun draw(): List<DrawnRect> = RectRecordingGraphics().also { world.draw(it) }.rects

    @Test
    fun `a full bar is drawn solid red across the whole width`() {
        spawn(hitPoints = 100)

        val rects = draw()
        assertEquals(2, rects.size, "expected a black backing and a red fill")
        assertEquals(DrawnRect(60, 122, 40, 3, EngineColors.BLACK), rects[0])
        assertEquals(DrawnRect(60, 122, 40, 3, EngineColors.RED), rects[1])
    }

    /** Left to right: the fill keeps the bar's left edge and only its width follows the health. */
    @Test
    fun `a part-full bar fills from the left edge`() {
        spawn(hitPoints = 25)

        val fill = draw().last()
        assertEquals(60, fill.x, "the fill moved off the left edge")
        assertEquals(10, fill.width)
        assertEquals(EngineColors.RED, fill.color)
    }

    @Test
    fun `an empty bar is drawn black with nothing filled`() {
        spawn(hitPoints = 0)

        val rects = draw()
        assertEquals(listOf(DrawnRect(60, 122, 40, 3, EngineColors.BLACK)), rects)
    }

    @Test
    fun `an entity without a health bar component gets no bar`() {
        spawn(hitPoints = 50, withBar = false)

        assertTrue(draw().isEmpty(), "drew a bar for an entity that never asked for one")
    }

    /**
     * The player is clamped to the framebuffer, so they spend real time with their sprite flush
     * against the bottom edge - where a bar drawn below it would be off screen.
     */
    @Test
    fun `a bar under an entity at the bottom edge stays on screen`() {
        spawn(hitPoints = 100, top = (WORLD_HEIGHT - 20).toFloat())

        val rects = draw()
        assertEquals(WORLD_HEIGHT - 3, rects[0].y)
        assertTrue(rects.all { it.y + it.height <= WORLD_HEIGHT })
    }

    @Test
    fun `health is read against its own maximum`() {
        spawn(hitPoints = 17, maxHitPoints = 34)

        // Half of 34, so half of the 40px bar.
        assertEquals(20, draw().last().width)
    }
}
