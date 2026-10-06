package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Gloss
import at.smiech.engine.Graphics
import at.smiech.engine.Lighting
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Keeps the light it is handed, and how many times it was handed one. */
private class LightRecordingGraphics : Graphics {
    var lighting: Lighting? = null
    var draws = 0

    override fun drawLighting(lighting: Lighting) {
        this.lighting = lighting
        draws++
    }

    override fun newPixmap(filename: String) =
        throw UnsupportedOperationException()

    override fun clear(color: Int) = Unit
    override fun drawPixel(x: Int, y: Int, color: Int) = Unit
    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) = Unit
    override fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int
    ) = Unit

    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) = Unit
    override fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
    ) = Unit

    override fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        rotationDegrees: Float,
    ) = Unit

    override fun drawPixmapSilhouette(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        color: Int,
    ) = Unit

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) = Unit
    override fun measureString(s: String, fontSize: Int) = 0
    override val width = 640
    override val height = 360
}

/** One [size]-pixel frame with something in it to read: a disc, or the whole square. */
private class ShapeSheet(private val size: Int, private val round: Boolean) : Pixmap {
    override val width = size
    override val height = size
    override fun dispose() = Unit

    override fun readPixels(buffer: IntArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        val middle = size / 2f
        for (row in 0 until height) for (column in 0 until width) {
            val dx = x + column + 0.5f - middle
            val dy = y + row + 0.5f - middle
            val inside = !round || dx * dx + dy * dy <= middle * middle
            buffer[row * width + column] = if (inside) 0xFF808080.toInt() else 0
        }
        return true
    }
}

/** What [LightingSystem] works out of the lights in a frame, their shadows and their glints. */
class LightingSystemTest {

    private val graphics = LightRecordingGraphics()
    private val world = World().apply { addSystem(LightingSystem(640, 360, AMBIENT, glow = 0.1f)) }

    private fun light(
        x: Float,
        y: Float,
        radius: Int = 60,
        color: Int = EngineColors.WHITE,
        fadeSeconds: Float = 0f,
        removeWhenFaded: Boolean = false,
        size: Float = 2f,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(
            id,
            TransformComponent(Rect.fromLTWH(x - size / 2f, y - size / 2f, size, size))
        )
        world.addComponent(
            id,
            LightComponent(
                color,
                radius,
                fadeSeconds = fadeSeconds,
                removeWhenFaded = removeWhenFaded
            )
        )
        return id
    }

    private fun occluder(
        left: Float,
        top: Float,
        size: Int = 16,
        shine: Float = 0f,
        round: Boolean = true
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(
            id,
            TransformComponent(Rect.fromLTWH(left, top, size.toFloat(), size.toFloat()))
        )
        world.addComponent(id, SpriteComponent(ShapeSheet(size, round)))
        world.addComponent(id, OccluderComponent(shine))
        return id
    }

    /** A tick, and a frame drawn after it: the light as that frame is lit. */
    private fun frame(): Lighting {
        world.update(TICK, null)
        world.draw(graphics)
        return graphics.lighting!!
    }

    @Test
    fun `the frame is lit by the dark and by every light centered on the pixel in the middle of it`() {
        light(100.4f, 50.9f, radius = 40, color = EngineColors.CYAN)

        val lighting = frame()

        assertEquals(AMBIENT, lighting.ambient)
        assertEquals(0.1f, lighting.glow)
        assertEquals(1, lighting.count)
        val lit = lighting[0]
        assertEquals(100, lit.x)
        assertEquals(50, lit.y)
        assertEquals(40, lit.radius)
        assertEquals(EngineColors.CYAN, lit.color)
        assertEquals(1f, lit.intensity)
    }

    @Test
    fun `a light that cannot reach the frame is left out and one that can is kept`() {
        light(-70f, 100f, radius = 60)
        light(-40f, 100f, radius = 60)
        light(700f, 100f, radius = 50)

        assertEquals(listOf(-40), frame().lights().map { it.x })
    }

    @Test
    fun `a fading light dims as it goes and a light that is nothing else goes with it`() {
        val flash = light(100f, 100f, fadeSeconds = 10 * TICK, removeWhenFaded = true)
        val ember = light(200f, 100f, fadeSeconds = 10 * TICK)

        val early = frame().lights().map { it.intensity }
        repeat(4) { world.update(TICK, null) }
        val later = frame().lights().map { it.intensity }
        assertTrue(later.zip(early).all { (now, then) -> now < then }, "$early, then $later")

        repeat(10) { world.update(TICK, null) }
        assertFalse(
            world.hasComponent(flash, LightComponent::class),
            "the flash outlived its light"
        )
        assertTrue(world.hasComponent(ember, LightComponent::class), "a fire went with its light")
        assertEquals(0, frame().count, "a light gone out still shines")
    }

    @Test
    fun `an occluder in a light's reach throws a shadow from it and one out of its reach none`() {
        light(100f, 100f, radius = 60)
        occluder(130f, 92f)
        occluder(400f, 92f)

        assertEquals(1, frame()[0].shadowCount)
    }

    /** A creature's own glow, centered on it, would otherwise throw the creature's shadow over everything. */
    @Test
    fun `a light inside an occluder throws no shadow from it`() {
        occluder(100f, 100f)
        light(108f, 108f)

        assertEquals(0, frame()[0].shadowCount)
    }

    /**
     * Cast from the sprite's own outline rather than its box: just past the corner of a disc's box,
     * where a square would stand in the way, the light gets by.
     */
    @Test
    fun `a shadow is cast in the shape of the sprite`() {
        light(100f, 80f, radius = 100)
        occluder(130f, 70f, size = 20, round = true)
        val disc = frame()[0]
        // Just inside the ray that grazes the top left corner of the box, past the box: a square's
        // shadow, and well clear of a disc's.
        val pastTheCorner = 159.5f to 60.5f
        assertFalse(
            disc.inShadow(pastTheCorner.first, pastTheCorner.second),
            "the disc's corner is air"
        )

        val square = World().apply { addSystem(LightingSystem(640, 360, AMBIENT)) }
        val recorded = LightRecordingGraphics()
        square.createEntity().also { id ->
            square.addComponent(id, TransformComponent(Rect.fromLTWH(99f, 79f, 2f, 2f)))
            square.addComponent(id, LightComponent(EngineColors.WHITE, 100))
        }
        square.createEntity().also { id ->
            square.addComponent(id, TransformComponent(Rect.fromLTWH(130f, 70f, 20f, 20f)))
            square.addComponent(id, SpriteComponent(ShapeSheet(20, round = false)))
            square.addComponent(id, OccluderComponent())
        }
        square.update(TICK, null)
        square.draw(recorded)
        assertTrue(
            recorded.lighting!![0].inShadow(pastTheCorner.first, pastTheCorner.second),
            "a square stops it"
        )
    }

    @Test
    fun `a light glints off what shines from its own side and in its own color`() {
        occluder(100f, 100f, shine = 0.5f)
        light(60f, 108f, color = EngineColors.CYAN)

        val lighting = frame()

        assertEquals(1, lighting.glintCount)
        val glint = lighting.glint(0)
        assertEquals(Gloss.DIRECTIONS / 2, glint.direction, "a light to the left")
        assertEquals(EngineColors.CYAN, glint.color)
        assertEquals(100, glint.x)
        assertEquals(100, glint.y)
        assertTrue(glint.strength > 0f && glint.strength <= 0.5f)
    }

    @Test
    fun `nothing glints that is matte or has the light inside it or stands in another's shadow`() {
        occluder(100f, 100f, shine = 0f)
        light(60f, 108f)
        assertEquals(0, frame().glintCount, "a matte occluder")

        val glowing = World().apply { addSystem(LightingSystem(640, 360, AMBIENT)) }
        val recorded = LightRecordingGraphics()
        glowing.createEntity().also { id ->
            glowing.addComponent(id, TransformComponent(Rect.fromLTWH(100f, 100f, 16f, 16f)))
            glowing.addComponent(id, SpriteComponent(ShapeSheet(16, round = true)))
            glowing.addComponent(id, OccluderComponent(shine = 1f))
            glowing.addComponent(id, LightComponent(EngineColors.RED, 40))
        }
        glowing.update(TICK, null)
        glowing.draw(recorded)
        assertEquals(0, recorded.lighting!!.glintCount, "its own light")

        val behind = World().apply { addSystem(LightingSystem(640, 360, AMBIENT)) }
        val shaded = LightRecordingGraphics()
        behind.createEntity().also { id ->
            behind.addComponent(id, TransformComponent(Rect.fromLTWH(19f, 107f, 2f, 2f)))
            behind.addComponent(id, LightComponent(EngineColors.WHITE, 120))
        }
        for (left in listOf(40f, 90f)) {
            behind.createEntity().also { id ->
                behind.addComponent(id, TransformComponent(Rect.fromLTWH(left, 100f, 16f, 16f)))
                behind.addComponent(id, SpriteComponent(ShapeSheet(16, round = true)))
                behind.addComponent(id, OccluderComponent(shine = if (left == 40f) 0f else 1f))
            }
        }
        behind.update(TICK, null)
        behind.draw(shaded)
        assertEquals(0, shaded.lighting!!.glintCount, "in the shadow of the one in front")
    }

    /** A frame no tick has moved on since the last is lit the same, and says so by its version. */
    @Test
    fun `the light is worked out once a tick`() {
        light(100f, 100f)
        val first = frame().version

        world.draw(graphics)
        assertEquals(first, graphics.lighting!!.version, "worked out again with nothing moved")

        assertNotEquals(first, frame().version)
        assertEquals(3, graphics.draws)
    }

    private fun Lighting.lights(): List<Lighting.Light> = (0 until count).map { this[it] }

    private companion object {
        const val TICK = 0.019f
        val AMBIENT = 0xFF202838.toInt()
    }
}
