package at.smiech.cyanbat.service

import at.smiech.cyanbat.resource.Backdrop
import at.smiech.cyanbat.resource.ParallaxLayer
import at.smiech.cyanbat.resource.Stage
import at.smiech.cyanbat.resource.StageMusic
import at.smiech.cyanbat.scenery.Daylight
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.Graphics
import at.smiech.engine.MusicGrid
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CrossfadeComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class Sheet(override val width: Int, override val height: Int) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/** Where the scenery goes, and how big it is, on a stage with a ceiling and on one without. */
class ObstacleGeneratorTest {

    private val world = World()
    private val keyframes = Daylight.KEYFRAMES.size

    /** A stage like the desert: an open sky, and ground scenery drawn in every light of the day. */
    private val openSky = Stage(
        id = 3,
        name = "TEST",
        backdrop = Backdrop.Nightfall(listOf(ParallaxLayer(Sheet(960, 400), top = 150, speed = 1f)), Sheet(24, 24)),
        topObstacles = emptyArray(),
        bottomObstacles = arrayOf(Sheet(38, 57 * keyframes), Sheet(96, 54 * keyframes)),
        music = StageMusic("test", MusicGrid(beatsPerMinute = 120.0, beatsPerBar = 4)),
        enemySheet = Sheet(640, 29),
    )

    private fun obstacles() = world.query(CollisionComponent::class, TransformComponent::class)

    private fun placeFor(seconds: Float, stage: Stage) {
        val generator = ObstacleGenerator(480, 320, EntityFactory(world), stage, Random(20260926))
        repeat((seconds / TICK_INITIAL).toInt()) { generator.update(TICK_INITIAL) }
    }

    @Test
    fun `under an open sky everything stands on the ground`() {
        placeFor(30f, openSky)

        assertTrue(obstacles().size >= 5, "only ${obstacles().size} obstacles in thirty seconds")
        obstacles().forEach {
            assertEquals(320f, world.getComponent(it, TransformComponent::class)!!.rect.bottom, "one of them is not standing on the ground")
        }
    }

    /** A roll for the top places nothing, so the ground keeps the density every other stage has. */
    @Test
    fun `an open sky does not crowd the ground with the ceiling's share`() {
        val withCeiling = openSky.copy(topObstacles = arrayOf(Sheet(41, 46 * keyframes)))
        placeFor(60f, withCeiling)
        val ceilingStage = obstacles().size
        obstacles().forEach { world.removeEntity(it) }
        world.update(TICK_INITIAL, null)

        placeFor(60f, openSky)

        assertTrue(obstacles().size < ceilingStage * 0.75f, "${obstacles().size} on the ground against $ceilingStage in all")
    }

    @Test
    fun `an obstacle drawn in every light of the day is one light tall and gets lit`() {
        placeFor(10f, openSky)

        obstacles().forEach {
            val rect = world.getComponent(it, TransformComponent::class)!!.rect
            val sprite = world.getComponent(it, SpriteComponent::class)!!
            assertEquals(sprite.pixmap.height / keyframes, rect.height.toInt(), "its box is the whole sheet")
            assertEquals(rect.height.toInt(), sprite.srcHeight)
            assertTrue(world.hasComponent(it, CrossfadeComponent::class), "nothing will ever relight it")
        }
    }
}
