package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.resource.Backdrop
import at.smiech.cyanbat.resource.ParallaxLayer
import at.smiech.cyanbat.scenery.Daylight
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.CrossfadeComponent
import at.smiech.engine.ecs.RenderSystem
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class NamedSheet(val name: String, override val width: Int, override val height: Int) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/** One call the system made, reduced to what these tests look at. */
private data class Drawn(val kind: String, val name: String = "", val x: Int = 0, val y: Int = 0, val w: Int = 0, val h: Int = 0, val srcX: Int = 0)

private class CallRecordingGraphics : Graphics {
    val calls = mutableListOf<Drawn>()

    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        calls += Drawn("rect", x = x, y = y, w = width, h = height)
    }

    override fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) {
        calls += Drawn("oval", x = x, y = y, w = width, h = height)
    }

    override fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) {
        calls += Drawn("line", x = xFrom, y = yFrom)
    }

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int) {
        calls += Drawn("pixmap", (pixmap as NamedSheet).name, x, y, srcWidth, srcHeight, srcX)
    }

    override fun drawPixmapFaded(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, alpha: Float) {
        calls += Drawn("faded", (pixmap as NamedSheet).name, x, y, srcWidth, srcHeight, srcX)
    }

    override fun newPixmap(filename: String, format: Graphics.PixmapFormat) = throw UnsupportedOperationException()
    override fun clear(color: Int) = Unit
    override fun drawPixel(x: Int, y: Int, color: Int) = Unit
    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int) = Unit
    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, rotationDegrees: Float) = Unit
    override fun drawPixmapSilhouette(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, color: Int) = Unit
    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) = Unit
    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) = Unit
    override fun measureString(s: String, fontSize: Int) = 0
    override val width = FRAME_BUFFER_WIDTH
    override val height = FRAME_BUFFER_HEIGHT
}

private const val TICK = 0.019f

/** The desert's sky: drawn at the back, in the light of the stage clock, over ground at three depths. */
class NightfallSystemTest {

    private var day = 0f
    private val keyframes = Daylight.KEYFRAMES.size
    private val far = NamedSheet("far", 960, 116 * keyframes)
    private val near = NamedSheet("near", 1440, 57 * keyframes)
    private val backdrop = Backdrop.Nightfall(
        layers = listOf(ParallaxLayer(far, top = 192, speed = 0.2f), ParallaxLayer(near, top = 304, speed = 1f)),
        moon = NamedSheet("moon", 24, 24),
    )
    private val world = World().apply {
        addSystem(NightfallSystem(backdrop, FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, dayPosition = { day }))
        addSystem(RenderSystem())
    }

    private fun draw(): List<Drawn> = CallRecordingGraphics().also { world.draw(it) }.calls

    private fun obstacle(rowHeight: Int = 57): Obstacle {
        val sheet = NamedSheet("rock", 38, rowHeight * keyframes)
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(200f, 303f, 38f, rowHeight.toFloat())))
        val sprite = SpriteComponent(sheet, srcHeight = rowHeight)
        world.addComponent(id, sprite)
        val crossfade = CrossfadeComponent()
        world.addComponent(id, crossfade)
        return Obstacle(sprite, crossfade)
    }

    private data class Obstacle(val sprite: SpriteComponent, val crossfade: CrossfadeComponent)

    @Test
    fun `the sky is the back of the picture and the sprites land on top of it`() {
        obstacle()
        world.update(TICK, null)

        val calls = draw()

        assertEquals(Drawn("rect", x = 0, y = 0, w = FRAME_BUFFER_WIDTH, h = 3), calls.first(), "the sky did not start the frame")
        assertEquals("rock", calls.last { it.kind == "pixmap" }.name, "something was drawn over the scenery's sprites")
    }

    @Test
    fun `an obstacle is put in the ground's light on every tick`() {
        val (sprite, crossfade) = obstacle(rowHeight = 57)
        day = (Daylight.KEYFRAMES[1] + Daylight.KEYFRAMES[2]) / 2f

        world.update(TICK, null)

        assertEquals(57, sprite.srcY, "the row underneath is not the keyframe the day is leaving")
        assertEquals(2 * 57, crossfade.srcY, "the row fading in is not the next keyframe")
        assertEquals(0.5f, crossfade.alpha, 0.001f)
    }

    @Test
    fun `the ground is crossfaded between keyframes and drawn plain on one`() {
        day = Daylight.KEYFRAMES[1]
        world.update(TICK, null)
        assertTrue(draw().none { it.kind == "faded" && it.name != "moon" }, "a keyframe's own hour was blended")

        day = (Daylight.KEYFRAMES[1] + Daylight.KEYFRAMES[2]) / 2f
        world.update(TICK, null)
        assertTrue(draw().any { it.kind == "faded" && it.name == "near" }, "the hours between were not blended")
    }

    /** One pixel a star, plus a few crosses: the only 1x1 rectangles the system draws. */
    @Test
    fun `there are no stars by day and a sky full of them by night`() {
        world.update(TICK, null)
        assertEquals(0, draw().count { it.kind == "rect" && it.w == 1 })

        day = 1f
        world.update(TICK, null)
        assertTrue(draw().count { it.kind == "rect" && it.w == 1 } > 100, "the night sky is empty")
    }

    @Test
    fun `the sun is drawn by day and not once it has set`() {
        world.update(TICK, null)
        assertTrue(draw().any { it.kind == "oval" })

        day = Daylight.SUNSET
        world.update(TICK, null)
        assertTrue(draw().none { it.kind == "oval" })
    }

    @Test
    fun `each band of ground scrolls at its own depth and covers the frame`() {
        repeat(100) { world.update(TICK, null) }

        val calls = draw()
        val farSpans = calls.filter { it.kind == "pixmap" && it.name == "far" }
        val nearSpans = calls.filter { it.kind == "pixmap" && it.name == "near" }
        assertEquals(20, farSpans.first().srcX, "the far band did not move at a fifth of the near one's pace")
        assertEquals(100, nearSpans.first().srcX)
        // Each span paints a column short, so together they have to ask for one more than the frame.
        assertTrue(nearSpans.sumOf { it.w - 1 } >= FRAME_BUFFER_WIDTH, "the near band does not reach across the frame")
    }

    @Test
    fun `a band wraps round to its own start as it crosses the frame`() {
        repeat(1300) { world.update(TICK, null) } // the near band is 1440 wide: 1300 in, it has to wrap

        val spans = draw().filter { it.kind == "pixmap" && it.name == "near" }
        assertEquals(2, spans.size)
        assertEquals(0, spans[1].srcX, "the second span does not start the strip over")
        assertEquals(spans[0].x + spans[0].w - 1, spans[1].x, "the spans do not meet")
    }
}
