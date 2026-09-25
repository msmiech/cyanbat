package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeSheet : Pixmap {
    override val width = 225
    override val height = 40
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/**
 * How a dying thing falls.
 *
 * Before this existed the dead player was handed a flat downward drift by `PlayerInputSystem` -
 * the code that reads the controls deciding how a corpse moves - and slid off the bottom of the
 * frame at a constant two pixels a tick, still beating its wings.
 */
class DeathSystemTest {

    private var puffs = 0

    private val world = World().apply {
        addSystem(DeathSystem { puffs++ })
        addSystem(MovementSystem())
    }

    private val transforms = world.mapper(TransformComponent::class)
    private val velocities = world.mapper(VelocityComponent::class)
    private val sprites = world.mapper(SpriteComponent::class)

    private fun dying(
        velocity: Vector2 = Vector2.Zero,
        gravity: Float = 7.5f,
        terminal: Float = 6f,
        spin: Float = 210f,
        puffInterval: Float = 0.22f,
    ): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(100f, 100f, 45f, 40f)))
        world.addComponent(id, VelocityComponent(velocity))
        world.addComponent(id, SpriteComponent(FakeSheet(), srcWidth = 45))
        world.addComponent(id, DeathThroesComponent(gravity, terminal, spin, puffInterval))
        return id
    }

    @Test
    fun `it picks up speed as it falls`() {
        val id = dying()

        world.update(0.1f, null)
        val first = velocities.require(id).velocity.y
        world.update(0.1f, null)
        val second = velocities.require(id).velocity.y

        assertTrue(first > 0f, "it did not start falling: $first")
        assertTrue(second > first, "it is not accelerating: $first then $second")
    }

    /**
     * Accelerating from whatever it was doing rather than snapping to a fixed drop is what makes
     * the fall read as the same object carrying on, instead of a new one dropped in its place.
     */
    @Test
    fun `it carries on from the velocity it died with`() {
        val id = dying(velocity = Vector2(-3f, -2f))

        world.update(0.05f, null)

        val velocity = velocities.require(id).velocity
        assertEquals(-3f, velocity.x, "sideways motion should be untouched")
        assertTrue(
            velocity.y > -2f && velocity.y < 0f,
            "it should still be rising, slower: ${velocity.y}"
        )
    }

    @Test
    fun `the fall is capped so a long drop does not turn into a blur`() {
        val id = dying(terminal = 6f)

        repeat(200) { world.update(0.05f, null) }

        assertEquals(6f, velocities.require(id).velocity.y)
    }

    @Test
    fun `it tumbles as it goes`() {
        val id = dying(spin = 210f)

        world.update(0.5f, null)

        assertEquals(105f, sprites.require(id).rotationDegrees, 0.01f)
    }

    /** The tumble is cosmetic, like every rotation in this engine: the box stays upright. */
    @Test
    fun `the collision box does not turn with the sprite`() {
        val id = dying(velocity = Vector2.Zero, gravity = 0f, spin = 400f)
        val before = transforms.require(id).rect

        world.update(0.5f, null)

        val after = transforms.require(id).rect
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
    }

    @Test
    fun `it sheds debris on a cadence`() {
        dying(puffInterval = 0.2f)

        repeat(10) { world.update(0.1f, null) }

        assertEquals(5, puffs, "expected one puff every 0.2s across a second")
    }

    /** A long frame must not lose the remainder, or the trail of blasts thins out under load. */
    @Test
    fun `a long frame still owes its debris`() {
        dying(puffInterval = 0.2f)

        world.update(0.2f, null)
        world.update(0.2f, null)

        assertEquals(2, puffs)
    }

    @Test
    fun `nothing is shed when the interval is zero`() {
        dying(puffInterval = 0f)

        repeat(10) { world.update(0.1f, null) }

        assertEquals(0, puffs)
    }

    /** A dying thing with no sprite is still allowed to fall. */
    @Test
    fun `an entity without a sprite still falls`() {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(0f, 0f, 10f, 10f)))
        world.addComponent(id, VelocityComponent(Vector2.Zero))
        world.addComponent(id, DeathThroesComponent(7.5f, 6f, 210f, 0f))

        world.update(0.1f, null)

        assertTrue(velocities.require(id).velocity.y > 0f)
    }
}
