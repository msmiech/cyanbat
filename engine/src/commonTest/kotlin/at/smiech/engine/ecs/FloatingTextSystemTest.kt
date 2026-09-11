package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One string the system asked for, in the order it asked. */
private data class DrawnString(val text: String, val x: Int, val y: Int, val fontSize: Int, val color: Int)

private class StringRecordingGraphics : Graphics {
    val strings = mutableListOf<DrawnString>()

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) {
        strings += DrawnString(s ?: "", x, y, fontSize, col)
    }

    override fun newPixmap(filename: String, format: Graphics.PixmapFormat) = throw UnsupportedOperationException()
    override fun clear(color: Int) = Unit
    override fun drawPixel(x: Int, y: Int, color: Int) = Unit
    override fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) = Unit
    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) = Unit
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
    override val width = 480
    override val height = 320
}

private fun alphaOf(color: Int) = (color shr 24) and 0xFF

class FloatingTextSystemTest {

    private val world = World().apply {
        addSystem(MovementSystem())
        addSystem(FloatingTextSystem())
    }

    private fun spawn(text: String = "34", risesPerTick: Float = 1f, duration: Float = 0.6f): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(100f, 200f, 0f, 0f)))
        world.addComponent(id, VelocityComponent(Vector2(0f, -risesPerTick)))
        world.addComponent(id, FloatingTextComponent(text, duration = duration))
        return id
    }

    private fun draw(): List<DrawnString> = StringRecordingGraphics().also { world.draw(it) }.strings

    /** Eight offsets in the outline colour, then the fill, so the number reads over any background. */
    @Test
    fun `text is drawn with a black outline around the fill`() {
        spawn()

        val strings = draw()
        assertEquals(9, strings.size)
        assertTrue(strings.dropLast(1).all { it.color and 0xFFFFFF == EngineColors.BLACK and 0xFFFFFF })
        assertEquals(EngineColors.WHITE, strings.last().color)
        assertTrue(strings.all { it.text == "34" })

        // The outline rings the fill: every offset is within a pixel of it, and none sits on it.
        val fill = strings.last()
        assertTrue(strings.dropLast(1).all { it.x - fill.x in -1..1 && it.y - fill.y in -1..1 })
        assertTrue(strings.dropLast(1).none { it.x == fill.x && it.y == fill.y })
    }

    @Test
    fun `text rises on its velocity`() {
        spawn(risesPerTick = 1f)
        world.update(0.019f, null)
        world.update(0.019f, null)

        assertEquals(198, draw().last().y)
    }

    @Test
    fun `text fades as its time runs out`() {
        spawn(duration = 1f)
        val full = alphaOf(draw().last().color)

        repeat(25) { world.update(0.02f, null) }
        val halfway = alphaOf(draw().last().color)

        assertEquals(255, full)
        assertTrue(halfway in 100..160, "expected roughly half alpha halfway through, got $halfway")
    }

    @Test
    fun `the outline fades with the fill`() {
        spawn(duration = 1f)
        repeat(25) { world.update(0.02f, null) }

        val strings = draw()
        assertEquals(alphaOf(strings.last().color), alphaOf(strings.first().color))
    }

    @Test
    fun `text is gone once its duration is up`() {
        spawn(duration = 0.2f)

        repeat(11) { world.update(0.02f, null) }

        assertTrue(draw().isEmpty(), "the number outlived its duration")
    }

    @Test
    fun `entities without floating text are left alone`() {
        val bystander = world.createEntity()
        world.addComponent(bystander, TransformComponent(Rect.fromLTWH(0f, 0f, 10f, 10f)))

        world.update(0.019f, null)

        assertTrue(draw().isEmpty())
    }
}
