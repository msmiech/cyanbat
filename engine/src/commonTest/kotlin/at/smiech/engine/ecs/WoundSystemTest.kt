package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import kotlin.test.Test
import kotlin.test.assertEquals

/** A bat's sheet: six 45x40 frames, three rows of them - unhurt, wounded, battered. */
private class WoundedSheet : Pixmap {
    override val width = 45 * 6
    override val height = ROW * 3
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

private const val ROW = 40

/** Which row of its sheet a creature is drawn from, against what is left of its health. */
class WoundSystemTest {

    private val changes = mutableListOf<Pair<EntityId, Int>>()

    private val world = World().apply {
        addSystem(AnimationSystem())
        addSystem(WoundSystem { id, row -> changes += id to row })
    }

    private fun creature(hitPoints: Int = 100): Pair<SpriteComponent, HealthComponent> {
        val id = world.createEntity()
        val sprite = SpriteComponent(WoundedSheet(), srcWidth = 45, srcHeight = ROW)
        val health = HealthComponent(hitPoints)
        world.addComponent(id, sprite)
        world.addComponent(id, health)
        world.addComponent(id, AnimationComponent(45, ROW, 6, 0.07f))
        world.addComponent(id, WoundComponent(ROW, floatArrayOf(0.66f, 0.33f)))
        return sprite to health
    }

    @Test
    fun `an unhurt creature is drawn from the top row`() {
        val (sprite, _) = creature()

        world.update(0.01f, null)

        assertEquals(0, sprite.srcY)
    }

    /** A graze is not a wound: the picture holds until a real share of the bar has gone. */
    @Test
    fun `a scratch leaves the picture as it was`() {
        val (sprite, health) = creature()
        health.hitPoints = 67

        world.update(0.01f, null)

        assertEquals(0, sprite.srcY)
    }

    @Test
    fun `each mark crossed moves it one row down the sheet`() {
        val (sprite, health) = creature()

        health.hitPoints = 66
        world.update(0.01f, null)
        assertEquals(ROW, sprite.srcY, "at the first mark")

        health.hitPoints = 33
        world.update(0.01f, null)
        assertEquals(2 * ROW, sprite.srcY, "at the second mark")

        health.hitPoints = 1
        world.update(0.01f, null)
        assertEquals(2 * ROW, sprite.srcY, "past the last mark there is no worse row to move to")
    }

    /** Read off the health rather than latched by the hit, so healing heals the picture too. */
    @Test
    fun `healing puts the picture back`() {
        val (sprite, health) = creature()
        health.hitPoints = 20
        world.update(0.01f, null)

        health.hitPoints = 90
        world.update(0.01f, null)

        assertEquals(0, sprite.srcY)
    }

    /** Health grown mid-run is a longer bar, and the same hit points are a smaller share of it. */
    @Test
    fun `the row follows the fraction rather than the count`() {
        val (sprite, health) = creature()
        health.hitPoints = 60
        health.maxHitPoints = 200

        world.update(0.01f, null)

        assertEquals(2 * ROW, sprite.srcY)
    }

    /** The wound picks the row and the wingbeat the column; neither moves the other. */
    @Test
    fun `the wingbeat carries on across the rows`() {
        val (sprite, health) = creature()
        world.update(0.08f, null)
        val column = sprite.srcX

        health.hitPoints = 50
        world.update(0.01f, null)

        assertEquals(column, sprite.srcX)
        assertEquals(ROW, sprite.srcY)
    }

    /**
     * What a wound does besides show is the game's, and it hears of every row a creature moves to -
     * hurt or healed - once, on the tick it moves there, and of nothing in between.
     */
    @Test
    fun `the game hears of each row a creature moves to and only then`() {
        val (_, health) = creature()

        world.update(0.01f, null)
        assertEquals(
            emptyList(),
            changes.map { it.second },
            "an unhurt creature has nothing to report"
        )

        health.hitPoints = 50
        repeat(3) { world.update(0.01f, null) }
        health.hitPoints = 10
        repeat(3) { world.update(0.01f, null) }
        health.hitPoints = 100
        repeat(3) { world.update(0.01f, null) }

        assertEquals(listOf(1, 2, 0), changes.map { it.second })
    }

    /**
     * The bat falls on a one-row sheet of its own. Moved down a row there, it would be drawn from
     * below the bottom of it - which is to say not drawn at all.
     */
    @Test
    fun `the dead are left on the row they have`() {
        val (sprite, health) = creature()
        health.hitPoints = 0
        health.alive = false

        world.update(0.01f, null)

        assertEquals(0, sprite.srcY)
    }
}
