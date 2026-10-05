package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.ColorwayComponent
import at.smiech.cyanbat.ecs.ElitePalette
import at.smiech.cyanbat.util.BOSS_SPRITE_SCALE
import at.smiech.cyanbat.util.ENEMY_SHOT_VARIANT_OFFSET
import at.smiech.cyanbat.util.MOTH_QUEEN_SHOT_VARIANT
import at.smiech.cyanbat.util.NAGA_SHOT_VARIANT
import at.smiech.cyanbat.util.PLAYER_SHOT_VARIANT
import at.smiech.cyanbat.util.SAND_WYRM_SHOT_VARIANT
import at.smiech.cyanbat.util.SHOT_FRAME_COUNT
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.AnimationComponent
import at.smiech.engine.ecs.AnimationSystem
import at.smiech.engine.ecs.ProjectileStyleComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
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
    private val shotSheet = SheetPixmap(STRIDE * COLORWAYS, 12)
    private val enemySheet = SheetPixmap(32 * 4 * 3, 29)

    private fun spriteOf(id: Int) = world.getComponent(id, SpriteComponent::class)!!

    private fun shot(variant: Int) = factory.createShot(
        0f, 0f, SHOT_FRAME_WIDTH.toFloat(), 12f, shotSheet, isPlayer = true, damage = 34,
        variant = variant,
    )

    @Test
    fun `a shot is addressed by its colorway and not drawn from the whole sheet`() {
        val sprite = spriteOf(shot(PLAYER_SHOT_VARIANT))

        assertEquals(SHOT_FRAME_WIDTH, sprite.srcWidth, "a shot should be one frame of the sheet")
        assertEquals(0, sprite.baseSrcX)
    }

    @Test
    fun `each colorway reads from its own frames`() {
        for (variant in 0 until COLORWAYS) {
            assertEquals(variant * STRIDE, spriteOf(shot(variant)).baseSrcX)
        }
    }

    @Test
    fun `every colorway stays inside the sheet`() {
        for (variant in 0 until COLORWAYS) {
            val sprite = spriteOf(shot(variant))
            assertTrue(
                sprite.baseSrcX + STRIDE <= shotSheet.width,
                "colorway $variant runs past the sheet",
            )
        }
    }

    /**
     * The bolt burns as it flies: it walks its own colorway's frames and comes round to the first
     * again, never straying into the next colorway's.
     */
    @Test
    fun `a shot plays its colorway's frames over and over`() {
        val variant = 3
        val id = shot(variant)
        val animation = world.getComponent(id, AnimationComponent::class)
        assertNotNull(animation, "a shot should be animated")
        assertEquals(SHOT_FRAME_COUNT, animation.frameCount)
        assertTrue(animation.isLooping, "a shot burns for as long as it flies")

        val system = AnimationSystem().also { world.addSystem(it) }
        val seen = mutableSetOf<Int>()
        repeat(4 * SHOT_FRAME_COUNT) {
            system.update(world, animation.interval * 1.01f, null)
            seen += spriteOf(id).srcX
        }
        assertEquals(
            List(SHOT_FRAME_COUNT) { variant * STRIDE + it * SHOT_FRAME_WIDTH }.toSet(),
            seen
        )
    }

    /** What a shot leaves where it is spent is drawn in the shot's colors, so it carries them. */
    @Test
    fun `a shot carries its colorway`() {
        for (variant in 0 until COLORWAYS) {
            assertEquals(
                variant,
                world.getComponent(shot(variant), ColorwayComponent::class)?.variant
            )
        }
    }

    /** The default is the bat's, so anything without a style of its own fires in cyan. */
    @Test
    fun `a shot with no colorway asked for is the player's`() {
        val id = factory.createShot(
            0f, 0f, SHOT_FRAME_WIDTH.toFloat(), 12f, shotSheet, isPlayer = true, damage = 34,
        )

        assertEquals(PLAYER_SHOT_VARIANT * STRIDE, spriteOf(id).baseSrcX)
    }

    // --- who carries a colorway ------------------------------------------------------------------

    @Test
    fun `an enemy carries its species' colorway`() {
        for (species in EnemySpecies.entries) {
            val id = factory.createEnemy(0f, 0f, 28f, 29f, enemySheet, species = species)
            val style = world.getComponent(id, ProjectileStyleComponent::class)
            assertNotNull(style, "$species has no colorway")
            assertEquals(species.shotVariant, style.variant)
        }
    }

    /** The cave's imps fire the colorway laid out behind the player's in their sheet's order. */
    @Test
    fun `the cave's imps fire in the colors of their own strip`() {
        for (species in listOf(EnemySpecies.SCOUT, EnemySpecies.WEAVER, EnemySpecies.STRIKER)) {
            assertEquals(species.strip + ENEMY_SHOT_VARIANT_OFFSET, species.shotVariant)
        }
    }

    @Test
    fun `every colorway an enemy fires is on the sheet`() {
        for (species in EnemySpecies.entries) {
            assertTrue(
                species.shotVariant in 0 until COLORWAYS,
                "$species fires colorway ${species.shotVariant}"
            )
        }
        assertTrue(MOTH_QUEEN_SHOT_VARIANT in 0 until COLORWAYS)
        assertTrue(NAGA_SHOT_VARIANT in 0 until COLORWAYS)
    }

    /** The case the feature was asked for: the cave's boss's bolts come out crimson, like the rest of it. */
    @Test
    fun `the Caco Imp fires in its own crimson`() {
        val id = factory.createBoss(
            0f,
            0f,
            holdX = 0f,
            pixmap = enemySheet,
            scale = BOSS_SPRITE_SCALE,
            hitPoints = 100,
            damage = 10,
            gun = CacoImpBrain.SMOULDERING_GUN,
            bar = Rect.fromLTWH(0f, 0f, 1f, 1f),
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
        for (species in EnemySpecies.entries) {
            assertTrue(
                species.shotVariant != PLAYER_SHOT_VARIANT,
                "$species fires the player's colorway",
            )
        }
        assertTrue(MOTH_QUEEN_SHOT_VARIANT != PLAYER_SHOT_VARIANT)
    }

    // --- elites -----------------------------------------------------------------------------------

    @Test
    fun `every elite's colorway is on the sheet and is its own`() {
        val variants = ElitePalette.entries.map { it.shotVariant }
        assertTrue(
            variants.all { it in 0 until COLORWAYS },
            "an elite colorway off the sheet: $variants"
        )
        assertEquals(variants.size, variants.toSet().size, "two elite palettes share a colorway")
    }

    /**
     * An elite's fire is told from the rest of the wave's by its color, so no elite colorway may be
     * one anything else fires - nor the player's, which no enemy may ever fire.
     */
    @Test
    fun `no elite fires in a colorway that anything else does`() {
        val everyoneElse = EnemySpecies.entries.map { it.shotVariant }.toSet() +
                setOf(
                    PLAYER_SHOT_VARIANT,
                    2 + ENEMY_SHOT_VARIANT_OFFSET,
                    MOTH_QUEEN_SHOT_VARIANT,
                    SAND_WYRM_SHOT_VARIANT
                )
        for (palette in ElitePalette.entries) {
            assertTrue(
                palette.shotVariant !in everyoneElse,
                "$palette fires colorway ${palette.shotVariant}"
            )
        }
    }

    @Test
    fun `an elite fires in its palette's colorway rather than its kind's`() {
        for (palette in ElitePalette.entries) {
            val id = factory.createEnemy(
                0f, 0f, 28f, 29f, enemySheet, species = EnemySpecies.SCOUT, elite = palette,
            )
            assertEquals(
                palette.shotVariant,
                world.getComponent(id, ProjectileStyleComponent::class)?.variant
            )
        }
    }

    private companion object {
        /**
         * How many colorways shot.png lays out: the player's, the cave's three, the jungle's three,
         * the Sand Wyrm's, the elites' five, and the Naga's.
         */
        const val COLORWAYS = 14

        /** One colorway's frames, side by side. */
        const val STRIDE = SHOT_FRAME_WIDTH * SHOT_FRAME_COUNT
    }
}
