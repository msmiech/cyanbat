package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class Oval(val x: Int, val y: Int, val width: Int, val height: Int, val color: Int)
private data class Line(val xFrom: Int, val yFrom: Int, val xTo: Int, val yTo: Int, val color: Int)
private data class Blip(val x: Int, val y: Int, val width: Int, val height: Int, val color: Int)

private class AuraRecordingGraphics : Graphics {
    val ovals = mutableListOf<Oval>()
    val lines = mutableListOf<Line>()
    val blips = mutableListOf<Blip>()

    override fun drawOval(x: Int, y: Int, width: Int, height: Int, color: Int) {
        ovals += Oval(x, y, width, height, color)
    }

    override fun drawLine(xFrom: Int, yFrom: Int, xTo: Int, yTo: Int, color: Int) {
        lines += Line(xFrom, yFrom, xTo, yTo, color)
    }

    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        blips += Blip(x, y, width, height, color)
    }

    override fun newPixmap(filename: String, format: Graphics.PixmapFormat) = throw UnsupportedOperationException()
    override fun clear(color: Int) = Unit
    override fun drawPixel(x: Int, y: Int, color: Int) = Unit
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

    override fun drawPixmapSilhouette(

        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int,

        dstWidth: Int, dstHeight: Int, color: Int

    ) = Unit


    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) = Unit
    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) = Unit
    override val width = 480
    override val height = 320
}

class AuraSystemTest {

    private val world = World().apply {
        addSystem(AuraSystem(AuraSystem.Layer.HALO))
        addSystem(AuraSystem(AuraSystem.Layer.ARCS))
    }

    /** A bat-sized entity at a round position, so the halo's geometry reads off the numbers. */
    private fun charged(
        intensity: Float = 0f,
        tier: Int = 0,
        phase: Float = 0f,
        alive: Boolean? = null,
    ): AuraComponent {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(100f, 100f, 45f, 40f)))
        val aura = AuraComponent(intensity = intensity, tier = tier, phase = phase)
        world.addComponent(id, aura)
        if (alive != null) world.addComponent(id, HealthComponent(hitPoints = 1, alive = alive))
        return aura
    }

    private fun draw(): AuraRecordingGraphics = AuraRecordingGraphics().also { world.draw(it) }

    @Test
    fun `an uncharged aura draws nothing at all`() {
        charged(intensity = 0f, tier = 0)

        val drawn = draw()

        assertTrue(drawn.ovals.isEmpty() && drawn.lines.isEmpty() && drawn.blips.isEmpty())
    }

    @Test
    fun `a glowing aura draws its rings`() {
        charged(intensity = 0.5f)

        assertEquals(16, draw().ovals.size)
    }

    /** The whole point of [AuraComponent.intensity]: the halo is how far the run has come. */
    @Test
    fun `the halo grows with intensity`() {
        val faint = charged(intensity = 0.2f)
        val small = draw().ovals.maxOf { it.width }

        faint.intensity = 1f
        val large = draw().ovals.maxOf { it.width }

        assertTrue(large > small, "halo did not grow: $small -> $large")
    }

    /**
     * Every ring, not just the outermost: concentric is the thing being claimed, and one ring
     * sitting on the entity's center says nothing about whether the rest share it.
     *
     * Asserted against the entity's own center with a pixel of slack rather than against measured
     * numbers. A ring is placed by rounding a float center and an odd width rounds a half pixel
     * off it, so an exact expectation is really an assertion about the current radius constants -
     * it broke on the first tuning pass, which is the opposite of what this test is for.
     */
    @Test
    fun `the halo is centered on the entity`() {
        charged(intensity = 1f)

        val ovals = draw().ovals
        assertTrue(ovals.isNotEmpty(), "no rings drawn")
        for (oval in ovals) {
            val offCenterX = abs(oval.x + oval.width / 2f - ENTITY_CENTER_X)
            val offCenterY = abs(oval.y + oval.height / 2f - ENTITY_CENTER_Y)
            assertTrue(offCenterX <= 1f, "ring off center by $offCenterX in x: $oval")
            assertTrue(offCenterY <= 1f, "ring off center by $offCenterY in y: $oval")
        }
    }

    /** A glow is not lightning: sparks and arcs are what a tier buys, and nothing else is. */
    @Test
    fun `a glow without a tier has no sparks or arcs`() {
        charged(intensity = 1f, tier = 0)

        val drawn = draw()

        assertTrue(drawn.lines.isEmpty(), "arcs at tier 0: ${drawn.lines.size}")
        assertTrue(drawn.blips.isEmpty(), "sparks at tier 0: ${drawn.blips.size}")
    }

    @Test
    fun `each tier adds arcs`() {
        val aura = charged(intensity = 1f, tier = 1)
        val one = draw().lines.size

        aura.tier = 4
        val four = draw().lines.size

        assertTrue(one > 0, "no arc at tier 1")
        assertTrue(four > one, "arcs did not multiply: $one -> $four")
    }

    @Test
    fun `each tier adds sparks`() {
        val aura = charged(intensity = 1f, tier = 1)
        val one = draw().blips.size

        aura.tier = 3
        val three = draw().blips.size

        assertTrue(three > one, "sparks did not multiply: $one -> $three")
    }

    /**
     * Both passes are added together and share one clock. If each advanced it the aura would run
     * at double speed - and it would do so silently, because everything it draws still looks right
     * at any single instant.
     */
    @Test
    fun `the two passes advance the phase once between them`() {
        val aura = charged(intensity = 1f)

        world.update(0.1f, null)

        assertEquals(0.1f, aura.phase, 1e-5f)
    }

    @Test
    fun `a crossed tier flares and then settles`() {
        val aura = charged(intensity = 1f, tier = 1)
        aura.surge = 1f
        val flared = draw().ovals.maxOf { it.width }

        repeat(50) { world.update(0.02f, null) }

        assertEquals(0f, aura.surge)
        assertTrue(draw().ovals.maxOf { it.width } < flared, "the flare never came back down")
    }

    @Test
    fun `a dead entity stops glowing`() {
        charged(intensity = 1f, tier = 3, alive = false)

        val drawn = draw()

        assertTrue(drawn.ovals.isEmpty() && drawn.lines.isEmpty() && drawn.blips.isEmpty())
    }

    @Test
    fun `a living entity with health still glows`() {
        charged(intensity = 1f, alive = true)

        assertTrue(draw().ovals.isNotEmpty())
    }

    /**
     * An arc is re-derived from its index every frame rather than stored, so this stands in for it
     * having a shape at all: across one flash it holds still, and the next strike is a different
     * squiggle. Without the first it would be static rather than lightning; without the second it
     * would be a wire that blinks.
     *
     * "Holds still" is a pixel or two rather than exactly, because the halo an arc is struck on is
     * breathing underneath it - which is the effect working, not drift.
     */
    @Test
    fun `an arc holds its shape for one flash and is redrawn for the next`() {
        val aura = charged(intensity = 1f, tier = 1, phase = 0.02f)
        val struck = vertices()
        assertTrue(struck.isNotEmpty(), "no arc was struck to compare")

        aura.phase = 0.08f
        assertTrue(
            farthestMove(struck, vertices()) <= 2,
            "the arc moved within its own flash: $struck -> ${vertices()}",
        )

        // Past the flash, and past the gap, into the next strike on the same slot.
        aura.phase = 0.47f
        assertTrue(
            farthestMove(struck, vertices()) > 2,
            "the next strike redrew the same shape: $struck",
        )
    }

    private fun vertices(): List<Pair<Int, Int>> = draw().lines.map { it.xFrom to it.yFrom }

    private fun farthestMove(before: List<Pair<Int, Int>>, after: List<Pair<Int, Int>>): Int {
        if (before.size != after.size) return Int.MAX_VALUE
        return before.zip(after).maxOf { (a, b) ->
            maxOf(kotlin.math.abs(a.first - b.first), kotlin.math.abs(a.second - b.second))
        }
    }

    /** And it does eventually go out, rather than hanging there as a permanent wire. */
    @Test
    fun `an arc is gone once its flash is over`() {
        val aura = charged(intensity = 1f, tier = 1, phase = 0.02f)
        assertTrue(draw().lines.isNotEmpty())

        aura.phase = 0.3f

        assertTrue(draw().lines.isEmpty(), "the arc outlived its flash")
    }

    private companion object {
        /** The center of the box [charged] places its entity at: 100,100 by 45x40. */
        const val ENTITY_CENTER_X = 122.5f
        const val ENTITY_CENTER_Y = 120.0f
    }
}
