package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.IMPACT_FRAME
import at.smiech.cyanbat.util.IMPACT_FRAME_COUNT
import at.smiech.cyanbat.util.IMPACT_LIGHT_SECONDS
import at.smiech.cyanbat.util.PLAYER_SHOT_VARIANT
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.AnimationComponent
import at.smiech.engine.ecs.LightComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class BurstSheet(override val width: Int, override val height: Int) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/**
 * The two things that can be left where something died, and the fact that they are two - and the
 * hit a shot leaves where it struck, which is a third and much smaller.
 *
 * Obstacles used to use the explosion. They are pale limestone with cyan crystal in them, and the
 * blast set them on fire in a cave where nothing burns - it said the spire had been detonated
 * rather than broken.
 */
class BurstTest {

    private val world = World()
    private val factory = EntityFactory(world)

    private val fireball = BurstSheet(32 * 8, 32)
    private val rubble = BurstSheet(40 * 7, 40)
    private val sparks = BurstSheet(IMPACT_FRAME * IMPACT_FRAME_COUNT * COLORWAYS, IMPACT_FRAME)
    private val bolts = BurstSheet(SHOT_FRAME_WIDTH * 4 * COLORWAYS, 12)

    private fun animationOf(id: Int) = world.getComponent(id, AnimationComponent::class)!!
    private fun spriteOf(id: Int) = world.getComponent(id, SpriteComponent::class)!!
    private fun rectOf(id: Int) = world.getComponent(id, TransformComponent::class)!!.rect

    @Test
    fun `an explosion walks its own sheet`() {
        val id = factory.createExplosion(100f, 100f, fireball)

        assertEquals(32, spriteOf(id).srcWidth)
        assertEquals(8, animationOf(id).frameCount)
    }

    @Test
    fun `a shatter walks its own sheet`() {
        val id = factory.createShatter(100f, 100f, rubble)

        assertEquals(40, spriteOf(id).srcWidth)
        assertEquals(7, animationOf(id).frameCount)
    }

    /**
     * The point of the change. If these ever agree on everything, one of them has been quietly
     * pointed at the other's artwork and an obstacle is back to catching fire.
     */
    @Test
    fun `the two are not the same effect`() {
        val boom = factory.createExplosion(100f, 100f, fireball)
        val break_ = factory.createShatter(100f, 100f, rubble)

        assertNotEquals(spriteOf(boom).pixmap, spriteOf(break_).pixmap)
        assertNotEquals(spriteOf(boom).srcWidth, spriteOf(break_).srcWidth)
        assertNotEquals(animationOf(boom).frameCount, animationOf(break_).frameCount)
    }

    /** Both play once and are reaped; a looping one would sit on the cave floor forever. */
    @Test
    fun `neither loops`() {
        assertFalse(animationOf(factory.createExplosion(0f, 0f, fireball)).isLooping)
        assertFalse(animationOf(factory.createShatter(0f, 0f, rubble)).isLooping)
    }

    /**
     * Centered on what died, because the caller knows what died and not how big a frame of the
     * effect happens to be.
     */
    @Test
    fun `a burst is centered on the point it is given`() {
        val rect = rectOf(factory.createShatter(200f, 150f, rubble))

        assertEquals(200f, rect.centerX, 0.01f)
        assertEquals(150f, rect.centerY, 0.01f)
    }

    @Test
    fun `a burst stays centered when it is scaled up for something big`() {
        val rect = rectOf(factory.createShatter(200f, 150f, rubble, scale = 2f))

        assertEquals(200f, rect.centerX, 0.01f)
        assertEquals(150f, rect.centerY, 0.01f)
        assertEquals(80f, rect.width, 0.01f)
    }

    /** A scaled burst has to actually be bigger, or a boss goes out in the same puff as an escort. */
    @Test
    fun `scale makes a burst larger`() {
        val small = rectOf(factory.createExplosion(0f, 0f, fireball))
        val large = rectOf(factory.createExplosion(0f, 0f, fireball, scale = 3f))

        assertTrue(large.width > small.width && large.height > small.height)
    }

    // --- a shot's hit ------------------------------------------------------------------------------

    @Test
    fun `a hit walks its own colorway's frames once`() {
        val variant = 5
        val id = factory.createImpact(100f, 100f, sparks, variant, isPlayer = false)

        assertEquals(variant * IMPACT_FRAME * IMPACT_FRAME_COUNT, spriteOf(id).baseSrcX)
        assertEquals(IMPACT_FRAME, spriteOf(id).srcWidth)
        assertEquals(IMPACT_FRAME_COUNT, animationOf(id).frameCount)
        assertFalse(animationOf(id).isLooping)
    }

    @Test
    fun `a hit is centered where the shot struck`() {
        val rect = rectOf(factory.createImpact(200f, 150f, sparks, PLAYER_SHOT_VARIANT, isPlayer = true))

        assertEquals(200f, rect.centerX, 0.01f)
        assertEquals(150f, rect.centerY, 0.01f)
    }

    /** A hit is not a kill: smaller than the blast of the smallest thing that dies. */
    @Test
    fun `a hit is smaller than a blast`() {
        val hit = rectOf(factory.createImpact(0f, 0f, sparks, PLAYER_SHOT_VARIANT, isPlayer = true))
        val blast = rectOf(factory.createExplosion(0f, 0f, fireball))

        assertTrue(hit.width < blast.width && hit.height < blast.height)
    }

    @Test
    fun `a hit in daylight gives off no light`() {
        assertNull(world.getComponent(factory.createImpact(0f, 0f, sparks, 0, isPlayer = true), LightComponent::class))
    }

    /**
     * In the dark a hit flares up wider than the shot that made it - the bat's and an enemy's alike -
     * and dies away with its spark.
     */
    @Test
    fun `a hit in the dark lights more than its shot did`() {
        val dark = EntityFactory(world, lit = true)
        for (isPlayer in listOf(true, false)) {
            val shot = dark.createShot(0f, 0f, SHOT_FRAME_WIDTH.toFloat(), 12f, bolts, isPlayer = isPlayer, variant = 3)
            val hit = dark.createImpact(0f, 0f, sparks, 3, isPlayer = isPlayer)
            val shotLight = assertNotNull(world.getComponent(shot, LightComponent::class))
            val hitLight = assertNotNull(world.getComponent(hit, LightComponent::class), "a hit in the dark is a light")

            assertTrue(hitLight.radius > shotLight.radius, "${hitLight.radius} against the shot's ${shotLight.radius}")
            assertEquals(IMPACT_LIGHT_SECONDS, hitLight.fadeSeconds)
            assertEquals(IMPACT_FRAME_COUNT * animationOf(hit).interval, hitLight.fadeSeconds, 0.001f)
        }
    }

    private companion object {
        /** The colorways the shots, and so their hits, come in. */
        const val COLORWAYS = 14
    }
}
