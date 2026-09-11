package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class TrailRect(val x: Int, val y: Int, val width: Int, val height: Int, val color: Int)

private class TrailRecordingGraphics : Graphics {
    val rects = mutableListOf<TrailRect>()

    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        rects += TrailRect(x, y, width, height, color)
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

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) = Unit
    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) = Unit
    override val width = 480
    override val height = 320
}

private fun alphaOf(color: Int) = (color shr 24) and 0xFF

class TrailSystemTest {

    private val emitted = mutableListOf<EntityId>()

    private val world = World().apply {
        addSystem(MovementSystem())
        addSystem(TrailSystem { emitted += it })
    }

    private fun emitter(interval: Float = 0.06f, alive: Boolean? = null): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(100f, 100f, 45f, 40f)))
        world.addComponent(id, TrailEmitterComponent(interval))
        if (alive != null) world.addComponent(id, HealthComponent(hitPoints = 1, alive = alive))
        return id
    }

    /** A 20x10 segment, so a scaled draw reads straight off the numbers. */
    private fun segment(duration: Float = 0.5f, minScale: Float = 0.2f, drift: Float = 0f): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(80f, 100f, 20f, 10f)))
        world.addComponent(id, VelocityComponent(Vector2(drift, 0f)))
        world.addComponent(id, TrailComponent(EngineColors.CYAN, duration, minScale))
        return id
    }

    private fun draw(): List<TrailRect> = TrailRecordingGraphics().also { world.draw(it) }.rects

    private fun advance(seconds: Float, step: Float = 0.019f) {
        var elapsed = 0f
        while (elapsed < seconds) {
            world.update(step, null)
            elapsed += step
        }
    }

    @Test
    fun `an emitter sheds a segment once its interval elapses`() {
        val bat = emitter(interval = 0.06f)

        advance(0.05f)
        assertTrue(emitted.isEmpty(), "shed early: ${emitted.size}")

        advance(0.03f)
        assertEquals(listOf(bat), emitted)
    }

    /** The wake's length is the cadence times how long a segment lives, so drift must not lose time. */
    @Test
    fun `an emitter keeps shedding on cadence`() {
        emitter(interval = 0.1f)

        advance(1f, step = 0.03f)

        assertTrue(emitted.size in 9..11, "expected ~10 segments, got ${emitted.size}")
    }

    @Test
    fun `a dead emitter stops shedding`() {
        emitter(interval = 0.06f, alive = false)

        advance(1f)

        assertTrue(emitted.isEmpty(), "a dead bat kept trailing")
    }

    @Test
    fun `a living emitter with health still sheds`() {
        emitter(interval = 0.06f, alive = true)

        advance(0.07f)

        assertEquals(1, emitted.size)
    }

    @Test
    fun `a fresh segment is drawn at full size and full alpha`() {
        segment()

        assertEquals(listOf(TrailRect(80, 100, 20, 10, EngineColors.CYAN)), draw())
    }

    /** Smaller over time, and about its own centre so the wake stays on one line. */
    @Test
    fun `a segment shrinks about its centre as it ages`() {
        segment(duration = 1f, minScale = 0.2f)

        repeat(25) { world.update(0.02f, null) }

        val drawn = draw().single()
        // Halfway through: scale is 0.6, so 20x10 becomes 12x6, still centred on (90, 105).
        assertEquals(12, drawn.width)
        assertEquals(6, drawn.height)
        assertEquals(90, drawn.x + drawn.width / 2)
        assertEquals(105, drawn.y + drawn.height / 2)
    }

    @Test
    fun `a segment fades as it ages`() {
        segment(duration = 1f)
        assertEquals(255, alphaOf(draw().single().color))

        repeat(25) { world.update(0.02f, null) }

        val faded = alphaOf(draw().single().color)
        assertTrue(faded in 100..160, "expected roughly half alpha halfway through, got $faded")
    }

    @Test
    fun `a segment never shrinks below its minimum scale`() {
        segment(duration = 0.2f, minScale = 0.5f)

        val widths = (0 until 9).map {
            world.update(0.02f, null)
            draw().single().width
        }

        // Half of 20, all the way down to the last frame it is drawn on.
        assertTrue(widths.all { it >= 10 }, "shrank past the floor: $widths")
        assertEquals(11, widths.last(), "expected to be at the floor by the end: $widths")
    }

    @Test
    fun `a spent segment is reaped`() {
        segment(duration = 0.2f)

        repeat(11) { world.update(0.02f, null) }

        assertTrue(draw().isEmpty(), "the segment outlived its duration")
    }

    /** Segments hang in the world on their own velocity, which is what puts them behind the bat. */
    @Test
    fun `a segment drifts on its velocity`() {
        segment(drift = -2f)
        val before = draw().single().let { it.x + it.width / 2 }

        world.update(0.019f, null)
        world.update(0.019f, null)

        // Measured centre to centre, because the shrink has taken a little off the width by now.
        val after = draw().single().let { it.x + it.width / 2 }
        assertEquals(4, before - after)
    }
}
