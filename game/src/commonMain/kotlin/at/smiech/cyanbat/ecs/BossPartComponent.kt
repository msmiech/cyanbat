package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.Component

/**
 * Marks an entity as part of a boss whose body is its attack (the Sand Wyrm's head and plates, the
 * Naga's head and body parts), each its own entity so it can be collided with, shot and flashed.
 *
 * A shot that hits a part lands on the boss's head, the one entity carrying health,
 * so the fight has one bar and one death however long the body. It lands once, for
 * one pierce, however many parts the shot meets. The bat flying into a part lands
 * nothing, as with every boss: the body sweeping through the bat is how this boss
 * hits. Only the brain that placed a part moves or removes it.
 *
 * @param share how much of a shot's damage it passes on to the boss, 0..1. A plate is armor and
 *   passes on part of it; the head passes on all of it, making it the place to aim. Otherwise a
 *   long body would take every shot of a spread at full weight and melt at once.
 */
class BossPartComponent(val share: Float = 1f) : Component
