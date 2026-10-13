package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.scenery.Biome
import at.smiech.cyanbat.scenery.EndlessScenery
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.PixelPixmap
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Draws nothing: the scenery only needs it to make its chunks, which it keeps as pixels. */
private object NoGraphics : Graphics {
    override fun newPixmap(filename: String): Pixmap = PixelPixmap(1, 1, IntArray(1))
    override fun clear(color: Int) = Unit
    override fun drawPixel(x: Int, y: Int, color: Int) = Unit
    override fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) = Unit
    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int) = Unit
    override fun drawPixmap(pixmap: Pixmap, x: Int, y: Int) = Unit
    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int,
    ) = Unit

    override fun drawPixmap(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int,
        rotationDegrees: Float,
    ) = Unit

    override fun drawPixmapSilhouette(
        pixmap: Pixmap, x: Int, y: Int, srcX: Int, srcY: Int, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int,
        color: Int,
    ) = Unit

    override fun drawString(s: String?, x: Int, y: Int, fontSize: Int, col: Int) = Unit
    override fun measureString(s: String, fontSize: Int): Int = 0
    override val width = 640
    override val height = 360
}

/** How the endless stage's weather falls, lands and is shaken off. */
class WeatherSystemTest {

    private val world = World()

    /**
     * A creature [size] wide and tall: transparent across its top [crest] rows, solid below, so
     * what lands on it has an outline to land on.
     */
    private fun creature(x: Float, y: Float, size: Int = 60, crest: Int = 12, group: CollisionGroup = CollisionGroup.ENEMY): EntityId {
        val pixels = IntArray(size * size) { if (it / size >= crest) 0xFF336699.toInt() else 0 }
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, size.toFloat(), size.toFloat())))
        world.addComponent(id, SpriteComponent(PixelPixmap(size, size, pixels)))
        world.addComponent(id, CollisionComponent(4f, group))
        return id
    }

    private fun weatherOver(biome: Biome): Pair<EndlessScenery, WeatherSystem> {
        val scenery = EndlessScenery(NoGraphics, 640, Random(1), seed = 5, first = biome)
        val weather = WeatherSystem(scenery, 640, 360, Random(2))
        world.addSystem(weather)
        return scenery to weather
    }

    private fun fly(scenery: EndlessScenery, seconds: Float) {
        repeat((seconds / TICK_INITIAL).toInt()) {
            scenery.update(TICK_INITIAL)
            world.update(TICK_INITIAL, null)
        }
    }

    private fun marksOn(id: EntityId): WeatherMarksComponent? = world.getComponent(id, WeatherMarksComponent::class)

    @Test
    fun `snow settles on top of a creature's outline and piles up`() {
        val (scenery, _) = weatherOver(Biome.PEAKS)
        val target = creature(300f, 120f)
        fly(scenery, 25f)

        val marks = assertNotNull(marksOn(target), "no snow settled")
        assertTrue(marks.count > 0)
        for (m in 0 until marks.count) {
            assertEquals(WeatherSystem.MARK_SNOW, marks.kind[m])
            assertTrue(marks.depth[m] <= 0f && marks.depth[m] > -2f, "snow at depth ${marks.depth[m]} is not on the outline")
            assertTrue(marks.column[m] in 0 until 60)
        }
    }

    @Test
    fun `rain runs down a creature and drips off it`() {
        val (scenery, _) = weatherOver(Biome.SWAMP)
        val target = creature(300f, 120f)
        fly(scenery, 10f)
        val marks = assertNotNull(marksOn(target), "no rain landed")
        assertTrue((0 until marks.count).all { marks.kind[it] == WeatherSystem.MARK_RAIN })
        val column = marks.column[0]
        val depth = marks.depth[0]
        val age = marks.age[0]

        fly(scenery, 0.5f)
        // The same drop, further down, unless it has run off already.
        val still = (0 until marks.count).firstOrNull { marks.column[it] == column && marks.age[it] > age }
        still?.let { assertTrue(marks.depth[it] > depth, "the drop did not run down") }
        // A drop never outlives its run down a creature this tall.
        assertTrue((0 until marks.count).all { marks.depth[it] <= 60f })
    }

    @Test
    fun `a blow shakes the weather off`() {
        val (scenery, weather) = weatherOver(Biome.PEAKS)
        val target = creature(300f, 120f)
        fly(scenery, 25f)
        val before = weather.particles
        val marks = assertNotNull(marksOn(target))
        val shaken = marks.count
        assertTrue(shaken > 0)

        weather.shake(world, target)

        assertEquals(0, marks.count)
        assertTrue(weather.particles >= minOf(before + shaken, WeatherSystem.MAX_PARTICLES) - 1, "the snow knocked off did not fall")
    }

    @Test
    fun `weather settles on creatures and not on the scenery`() {
        val (scenery, _) = weatherOver(Biome.PEAKS)
        val rock = creature(120f, 200f, group = CollisionGroup.OBSTACLE)
        val shot = creature(420f, 200f, size = 20, crest = 0, group = CollisionGroup.PLAYER_PROJECTILE)
        fly(scenery, 20f)
        assertNull(marksOn(rock))
        assertNull(marksOn(shot))
    }

    @Test
    fun `however hard it falls the air never holds more than the system keeps`() {
        val (scenery, weather) = weatherOver(Biome.VOLCANO)
        repeat(3000) {
            scenery.update(TICK_INITIAL)
            world.update(TICK_INITIAL, null)
            assertTrue(weather.particles <= WeatherSystem.MAX_PARTICLES)
        }
        assertTrue(weather.particles > 50, "the ash hardly fell")
    }

    @Test
    fun `a marked creature carries no more marks than its slots`() {
        val (scenery, _) = weatherOver(Biome.PEAKS)
        val target = creature(250f, 80f, size = 120)
        fly(scenery, 60f)
        assertTrue(assertNotNull(marksOn(target)).count <= WeatherMarksComponent.CAPACITY)
    }
}
