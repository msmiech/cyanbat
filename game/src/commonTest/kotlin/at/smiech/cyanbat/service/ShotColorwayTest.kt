package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_SPRITE_SCALE
import at.smiech.cyanbat.util.ENEMY_SHOT_VARIANT_OFFSET
import at.smiech.cyanbat.util.PLAYER_SHOT_VARIANT
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.ProjectileStyleComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class SheetPixmap(override val width: Int, override val height: Int) : Pixmap {
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/**
 * Which colorway a shot comes out in, and who decides.
 *
 * The point of the feature: the screen can hold the bat's fire and the boss's at the same moment,
 * travelling in opposite directions, and before this they were the same cyan bolt. Telling them
 * apart meant reading which way each one was moving.
 */
class ShotColorwayTest {

    private val world = World()
    private val factory = EntityFactory(world)
    private val shotSheet = SheetPixmap(SHOT_FRAME_WIDTH * 4, 12)
    private val enemySheet = SheetPixmap(32 * 4 * 3, 29)

    private fun spriteOf(id: Int) = world.getComponent(id, SpriteComponent::class)!!

    private fun shot(variant: Int) = factory.createShot(
        0f, 0f, SHOT_FRAME_WIDTH.toFloat(), 12f, shotSheet, isPlayer = true, damage = 34,
        variant = variant,
    )

    @Test
    fun `a shot is addressed by its colorway, not drawn from the whole sheet`() {
        val sprite = spriteOf(shot(PLAYER_SHOT_VARIANT))

        assertEquals(SHOT_FRAME_WIDTH, sprite.srcWidth, "a shot should be one frame of the sheet")
        assertEquals(0, sprite.baseSrcX)
    }

    @Test
    fun `each colorway reads from its own frame`() {
        for (variant in 0 until 4) {
            assertEquals(variant * SHOT_FRAME_WIDTH, spriteOf(shot(variant)).baseSrcX)
        }
    }

    @Test
    fun `every colorway stays inside the sheet`() {
        for (variant in 0 until 4) {
            val sprite = spriteOf(shot(variant))
            assertTrue(
                sprite.baseSrcX + sprite.srcWidth <= shotSheet.width,
                "colorway $variant runs past the sheet",
            )
        }
    }

    /** The default is the bat's, so anything without a style of its own fires in cyan. */
    @Test
    fun `a shot with no colorway asked for is the player's`() {
        val id = factory.createShot(
            0f, 0f, SHOT_FRAME_WIDTH.toFloat(), 12f, shotSheet, isPlayer = true, damage = 34,
        )

        assertEquals(PLAYER_SHOT_VARIANT * SHOT_FRAME_WIDTH, spriteOf(id).baseSrcX)
    }

    // --- who carries a colorway ------------------------------------------------------------------

    @Test
    fun `an enemy carries the colorway matching its own sprite`() {
        for (type in 0..2) {
            val id = factory.createEnemy(0f, 0f, 28f, 29f, enemySheet, type = type)
            val style = world.getComponent(id, ProjectileStyleComponent::class)
            assertNotNull(style, "enemy type $type has no colorway")
            assertEquals(type + ENEMY_SHOT_VARIANT_OFFSET, style.variant)
        }
    }

    /**
     * The boss is the one enemy that actually has a gun today, so this is the case the feature was
     * asked for: its bolts come out crimson, like the rest of it.
     */
    @Test
    fun `the boss fires in its own crimson`() {
        val id = factory.createBoss(
            0f, 0f, holdX = 0f, pixmap = enemySheet, scale = BOSS_SPRITE_SCALE,
            hitPoints = 100, damage = 10, shotIntervalSeconds = 1.6f,
        )

        val style = world.getComponent(id, ProjectileStyleComponent::class)
        assertNotNull(style)
        assertEquals(
            2 + ENEMY_SHOT_VARIANT_OFFSET,
            style.variant,
            "the boss wears the third enemy's colors"
        )
    }

    /** Nothing an enemy fires should ever come out in the player's color. */
    @Test
    fun `no enemy colorway collides with the player's`() {
        for (type in 0..2) {
            assertTrue(
                type + ENEMY_SHOT_VARIANT_OFFSET != PLAYER_SHOT_VARIANT,
                "enemy type $type fires the player's colorway",
            )
        }
    }
}
