package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.CRITICAL_TEXT_FONT_SIZE
import at.smiech.cyanbat.util.DAMAGE_TEXT_FONT_SIZE
import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.DamageComponent
import at.smiech.engine.ecs.FloatingTextComponent
import at.smiech.engine.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class StubPixmap : Pixmap {
    override val width = 24
    override val height = 12
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/** How a critical hit reaches the screen: on the shot that caused it, and in the number it puts up. */
class CriticalShotTest {

    private val world = World()
    private val factory = EntityFactory(world)

    private fun textOf(id: Int) = world.getComponent(id, FloatingTextComponent::class)!!

    // --- the shot carries it --------------------------------------------------------------------

    @Test
    fun `an ordinary shot is not critical`() {
        val id = factory.createShot(
            0f, 0f, 24f, 12f, StubPixmap(), isPlayer = true, damage = 34,
        )

        assertFalse(world.getComponent(id, DamageComponent::class)!!.isCritical)
    }

    /**
     * The crit rides on the projectile rather than being decided where it lands, which is what
     * makes it survive the flight - and what makes a piercing crit critical against everything it
     * goes through.
     */
    @Test
    fun `a critical shot carries its criticality and its damage`() {
        val id = factory.createShot(
            0f, 0f, 24f, 12f, StubPixmap(), isPlayer = true, damage = 136, critical = true,
        )

        val damage = world.getComponent(id, DamageComponent::class)!!
        assertTrue(damage.isCritical)
        assertEquals(136, damage.amount)
    }

    // --- the number it puts up ------------------------------------------------------------------

    @Test
    fun `an ordinary damage number is white and the ordinary size`() {
        val text = textOf(factory.createDamageText(10f, 10f, damage = 34))

        assertEquals(EngineColors.WHITE, text.color)
        assertEquals(DAMAGE_TEXT_FONT_SIZE, text.fontSize)
    }

    @Test
    fun `a critical damage number is red and larger`() {
        val text = textOf(factory.createDamageText(10f, 10f, damage = 136, critical = true))

        assertEquals(EngineColors.RED, text.color)
        assertEquals(CRITICAL_TEXT_FONT_SIZE, text.fontSize)
        assertTrue(
            text.fontSize > DAMAGE_TEXT_FONT_SIZE,
            "a crit should be bigger than an ordinary hit, was ${text.fontSize}",
        )
    }

    /** It is the one number worth reading, so it is given longer to be read. */
    @Test
    fun `a critical damage number stays up longer`() {
        val ordinary = textOf(factory.createDamageText(10f, 10f, damage = 34))
        val critical = textOf(factory.createDamageText(10f, 10f, damage = 136, critical = true))

        assertTrue(
            critical.duration > ordinary.duration,
            "expected a longer life than ${ordinary.duration}, was ${critical.duration}",
        )
    }

    @Test
    fun `the number shown is the damage that landed`() {
        assertEquals("136", textOf(factory.createDamageText(0f, 0f, 136, critical = true)).text)
    }
}
