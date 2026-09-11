package at.smiech.engine.ecs

import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class BlankPixmap : Pixmap {
    override val width = 24
    override val height = 12
    override val format = Graphics.PixmapFormat.ARGB8888
    override fun dispose() = Unit
}

/** The artwork points right, so a shot travelling right is drawn at zero. */
class FacingSystemTest {

    private val world = World().apply {
        // The order the game uses: a bounce reverses the velocity, and the facing answers it on
        // the same frame rather than a frame later.
        addSystem(BounceSystem(480, 320))
        addSystem(FacingSystem())
    }

    private fun spawn(velocity: Vector2, x: Float = 100f, y: Float = 100f, faces: Boolean = true): EntityId {
        val id = world.createEntity()
        world.addComponent(id, TransformComponent(Rect.fromLTWH(x, y, 24f, 12f)))
        world.addComponent(id, VelocityComponent(velocity))
        world.addComponent(id, SpriteComponent(BlankPixmap()))
        if (faces) world.addComponent(id, FacesVelocityComponent())
        return id
    }

    private fun angleOf(id: EntityId) = world.getComponent(id, SpriteComponent::class)!!.rotationDegrees

    private fun tick() = world.update(0.019f, null)

    @Test
    fun `travelling right is the artwork's own orientation`() {
        val id = spawn(Vector2(4f, 0f))

        tick()

        assertEquals(0f, angleOf(id))
    }

    /** The case the feature exists for: a shot that has come back off a wall points back. */
    @Test
    fun `travelling left turns the sprite around`() {
        val id = spawn(Vector2(-4f, 0f))

        tick()

        assertEquals(180f, angleOf(id), 0.01f)
    }

    @Test
    fun `travelling down points down, and up points up`() {
        val down = spawn(Vector2(0f, 4f))
        val up = spawn(Vector2(0f, -4f), y = 200f)

        tick()

        assertEquals(90f, angleOf(down), 0.01f)
        assertEquals(-90f, angleOf(up), 0.01f)
    }

    /** A fanned shot leaves at a slight angle, and is drawn along it rather than straight. */
    @Test
    fun `a shot fired at an angle is drawn along it`() {
        val id = spawn(Vector2(4f, 4f))

        tick()

        assertEquals(45f, angleOf(id), 0.01f)
    }

    /**
     * The whole point, end to end: a shot reaching the right edge is reflected and is drawn
     * pointing the other way on that very frame, not the next one.
     */
    @Test
    fun `a shot bouncing off an edge turns on the frame it reverses`() {
        val id = spawn(Vector2(4f, 0f), x = 470f)
        tick()
        assertEquals(0f, angleOf(id), 0.01f, "it has not reached the edge yet")

        world.addComponent(id, BounceComponent(1))
        // Carry it into the right edge; the bounce and the facing both land on this tick.
        repeat(3) { tick() }

        assertEquals(180f, angleOf(id), 0.01f)
    }

    @Test
    fun `an entity at a standstill keeps the way it was last pointing`() {
        val id = spawn(Vector2(-4f, 0f))
        tick()

        world.getComponent(id, VelocityComponent::class)!!.velocity = Vector2.Zero
        tick()

        assertEquals(180f, angleOf(id), 0.01f, "a stopped sprite should not flick back to zero")
    }

    /** Opt-in: the bat and the enemies are drawn from artwork that has an up. */
    @Test
    fun `a sprite without the component is never turned`() {
        val id = spawn(Vector2(-4f, 0f), faces = false)

        tick()

        assertEquals(0f, angleOf(id))
    }

    @Test
    fun `the angle tracks a velocity that keeps changing`() {
        val id = spawn(Vector2(4f, 0f))
        tick()
        val first = angleOf(id)

        world.getComponent(id, VelocityComponent::class)!!.velocity = Vector2(4f, 2f)
        tick()

        assertTrue(angleOf(id) > first, "the sprite did not follow the velocity down")
    }
}
