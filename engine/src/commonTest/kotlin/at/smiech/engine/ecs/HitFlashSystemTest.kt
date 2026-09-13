package at.smiech.engine.ecs

import at.smiech.engine.math.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HitFlashSystemTest {

    private val world = World().apply { addSystem(HitFlashSystem()) }

    private fun lit(duration: Float = 0.1f): HitFlashComponent {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(0f, 0f, 10f, 10f)))
        val flash = HitFlashComponent(duration, COLOR)
        world.addComponent(id, flash)
        return flash
    }

    @Test
    fun `a fresh flash is at full strength`() {
        assertEquals(1f, lit().strength)
    }

    @Test
    fun `a flash burns down as time passes`() {
        val flash = lit(duration = 0.1f)

        world.update(0.04f, null)

        assertTrue(flash.strength in 0.55f..0.65f, "expected about 0.6, was ${flash.strength}")
    }

    @Test
    fun `a spent flash reads as nothing`() {
        val flash = lit(duration = 0.1f)

        world.update(0.2f, null)

        assertEquals(0f, flash.strength)
    }

    /**
     * A long frame must not drive the timer negative. Strength clamps either way, but a remaining
     * that kept falling would take longer and longer to re-arm as a run went on.
     */
    @Test
    fun `an overlong frame does not push the timer below zero`() {
        val flash = lit(duration = 0.1f)

        world.update(5f, null)
        world.update(5f, null)

        assertEquals(0f, flash.remaining)
    }

    /**
     * What makes sustained fire read as separate impacts: the second hit restarts the flash rather
     * than being swallowed by the one still burning.
     */
    @Test
    fun `a re-armed flash is back to full`() {
        val flash = lit(duration = 0.1f)
        world.update(0.07f, null)
        assertTrue(flash.strength < 0.5f)

        flash.remaining = flash.duration

        assertEquals(1f, flash.strength)
    }

    /** A zero-length flash is a divide waiting to happen, not something to draw. */
    @Test
    fun `a zero duration flash reads as nothing rather than dividing by zero`() {
        assertEquals(0f, lit(duration = 0f).strength)
    }

    private companion object {
        const val COLOR = 0xE6FFFFFF.toInt()
    }
}
