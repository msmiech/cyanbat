package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Each sprite gets its own pixmap, so a recorded draw call identifies the entity that made it. */
private class TaggedPixmap(val tag: String) : Pixmap {
    override val width = 10
    override val height = 10
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/**
 * One blit, as the system asked for it: which sprite, how big it came out on screen, and which way
 * round it was turned.
 */
private data class DrawnSprite(
    val tag: String,
    val dstWidth: Int,
    val dstHeight: Int,
    val rotationDegrees: Float = 0f,
)

/** One hit flash: whose silhouette was filled, and with what. */
private data class DrawnFlash(val tag: String, val color: Int)

private class RecordingGraphics : Graphics {
    val sprites = mutableListOf<DrawnSprite>()
    val flashes = mutableListOf<DrawnFlash>()
    val drawn: List<String> get() = sprites.map { it.tag }

    /**
     * Recorded into the sprite list as well as its own, so a test can see *where in the order* a
     * flash landed. That placement is the entire reason RenderSystem draws flashes rather than a
     * system of its own, so it is the thing worth pinning down.
     */
    override fun drawPixmapSilhouette(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, color: Int
    ) {
        val tag = (pixmap as TaggedPixmap).tag
        flashes += DrawnFlash(tag, color)
        sprites += DrawnSprite("$tag:flash", dstWidth, dstHeight)
    }

    override fun drawPixmap(
        pixmap: Pixmap,
        x: Int,
        y: Int,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int
    ) {
        sprites += DrawnSprite((pixmap as TaggedPixmap).tag, srcWidth, srcHeight)
    }

    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int
    ) {
        sprites += DrawnSprite((pixmap as TaggedPixmap).tag, dstWidth, dstHeight)
    }

    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,
        dstWidth: Int, dstHeight: Int, rotationDegrees: Float
    ) {
        sprites += DrawnSprite((pixmap as TaggedPixmap).tag, dstWidth, dstHeight, rotationDegrees)
    }

    override fun newPixmap(filename: String, format: Graphics.PixmapFormat) = TaggedPixmap(filename)
    override fun clear(color: Int) = Unit
    override fun drawPixel(x: Int, y: Int, color: Int) = Unit
    override fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) = Unit
    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) = Unit
    override fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) = Unit
    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) = Unit
    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) = Unit
    override val width = 480
    override val height = 320
}

class RenderSystemTest {

    private val world = World().apply { addSystem(RenderSystem()) }

    private fun spawn(tag: String, zIndex: Int? = null): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(0f, 0f, 10f, 10f)))
        world.addComponent(id, SpriteComponent(TaggedPixmap(tag)))
        if (zIndex != null) world.addComponent(id, ZIndexComponent(zIndex))
        return id
    }

    private fun drawnOrder(): List<String> = RecordingGraphics().also { world.draw(it) }.drawn

    private fun drawnSprites(): List<DrawnSprite> =
        RecordingGraphics().also { world.draw(it) }.sprites

    /** Backgrounds sit at -100 and explosions at 50, so negative layers have to sort correctly. */
    @Test
    fun `sprites draw from the lowest z index up`() {
        spawn("explosion", zIndex = 50)
        spawn("background", zIndex = -100)
        spawn("enemy", zIndex = 10)

        assertContentEquals(listOf("background", "enemy", "explosion"), drawnOrder())
    }

    @Test
    fun `a sprite without a z index draws at zero`() {
        spawn("above", zIndex = 5)
        spawn("unlayered")
        spawn("below", zIndex = -5)

        assertContentEquals(listOf("below", "unlayered", "above"), drawnOrder())
    }

    @Test
    fun `sprites sharing a layer keep their creation order`() {
        spawn("first", zIndex = 10)
        spawn("second", zIndex = 10)
        spawn("third", zIndex = 10)

        assertContentEquals(listOf("first", "second", "third"), drawnOrder())
    }

    /**
     * Ids are recycled, so a late arrival can hold a low id. Ordering a layer by id would draw it
     * as though it had been there all along.
     */
    @Test
    fun `a recycled id does not jump the queue within its layer`() {
        val doomed = spawn("doomed", zIndex = 10)
        spawn("survivor", zIndex = 10)
        world.removeEntity(doomed)
        world.update(0.016f, null)

        spawn("latecomer", zIndex = 10)

        assertContentEquals(listOf("survivor", "latecomer"), drawnOrder())
    }

    /**
     * The sheet holds one size of every sprite, so a boss is that artwork blown up. Scaling is
     * the renderer's job, not the sheet's: the source rect stays the frame it always was.
     */
    @Test
    fun `a scaled sprite is blitted into a scaled destination`() {
        val id = spawn("boss")
        world.getComponent(id, SpriteComponent::class)!!.scale = 3f

        assertContentEquals(listOf(DrawnSprite("boss", 30, 30)), drawnSprites())
    }

    @Test
    fun `an unscaled sprite is blitted at its source size`() {
        spawn("enemy")

        assertContentEquals(listOf(DrawnSprite("enemy", 10, 10)), drawnSprites())
    }

    /**
     * A turned sprite keeps its box: rotation changes which way the artwork points, not how much
     * of the screen it covers.
     */
    @Test
    fun `a rotated sprite is blitted turned but at the same size`() {
        val id = spawn("shot")
        world.getComponent(id, SpriteComponent::class)!!.rotationDegrees = 180f

        assertContentEquals(listOf(DrawnSprite("shot", 10, 10, 180f)), drawnSprites())
    }

    @Test
    fun `rotation and scale travel together`() {
        val id = spawn("shot")
        world.getComponent(id, SpriteComponent::class)!!.apply {
            rotationDegrees = 90f
            scale = 2f
        }

        assertContentEquals(listOf(DrawnSprite("shot", 20, 20, 90f)), drawnSprites())
    }

    /** The draw buffer is reused across frames; a quieter frame must not redraw the busy one. */
    @Test
    fun `a shrinking scene leaves nothing behind`() {
        val crowd = (0 until 100).map { spawn("sprite$it", zIndex = it) }
        assertContentEquals((0 until 100).map { "sprite$it" }, drawnOrder())

        crowd.drop(2).forEach { world.removeEntity(it) }
        world.update(0.016f, null)

        assertContentEquals(listOf("sprite0", "sprite1"), drawnOrder())
    }

    // --- hit flash -------------------------------------------------------------------

    @Test
    fun `a sprite without a flash draws once`() {
        spawn("enemy")

        assertContentEquals(listOf("enemy"), drawnOrder())
    }

    @Test
    fun `a flash is drawn straight over its own sprite and not over the whole scene`() {
        val lit = spawn("behind", zIndex = 10)
        world.addComponent(lit, HitFlashComponent(duration = 0.1f, color = FLASH))
        spawn("in front", zIndex = 20)

        // The flash belongs between the sprite it lights and the one layered above it.
        // Drawn by a system of its own it would land last and glow through "in front".
        assertContentEquals(listOf("behind", "behind:flash", "in front"), drawnOrder())
    }

    @Test
    fun `a flash fades with what is left of it`() {
        val lit = spawn("enemy")
        val flash = HitFlashComponent(duration = 0.1f, color = FLASH)
        world.addComponent(lit, flash)

        val full = RecordingGraphics().also { world.draw(it) }.flashes.single().color
        flash.remaining = 0.025f
        val dying = RecordingGraphics().also { world.draw(it) }.flashes.single().color

        assertEquals(0xE6, full ushr 24, "a fresh flash should be at the color's own alpha")
        assertTrue(
            (dying ushr 24) in 1 until (full ushr 24),
            "a quarter-spent flash should be dimmer but still visible: ${dying ushr 24}"
        )
        assertEquals(full and 0xFFFFFF, dying and 0xFFFFFF, "the hue should not drift as it fades")
    }

    /** Spent is spent: RenderSystem must not keep painting a flash HitFlashSystem finished. */
    @Test
    fun `a spent flash draws nothing`() {
        val lit = spawn("enemy")
        world.addComponent(lit, HitFlashComponent(duration = 0.1f, color = FLASH, remaining = 0f))

        assertContentEquals(listOf("enemy"), drawnOrder())
    }

    private companion object {
        const val FLASH = 0xE6FFFFFF.toInt()
    }
}
