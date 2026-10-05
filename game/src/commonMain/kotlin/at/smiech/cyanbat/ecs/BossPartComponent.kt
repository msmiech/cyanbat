package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.Component

/**
 * Marks an entity as part of the body of a boss whose body is its attack: the Sand Wyrm's head and
 * each of its plates, every one an entity so it can be collided with, shot and flashed where it is.
 *
 * What a shot lands on a part lands on the boss - the head, which is the one entity the generator
 * tracks as the boss and the only one carrying health - so the fight has one bar and one death,
 * however long the body. And it lands once, for one pierce, however many parts the shot meets: the
 * body is one target, bunched up or strung out. What the bat lands on it by flying into it is
 * nothing, as for every boss: the body sweeping through the bat is how this boss hits, and a boss
 * that lost health every time it landed a blow would be beaten by being let through. Only the brain
 * that placed a plate moves or removes it.
 *
 * @param share how much of a shot's damage it passes on to the boss, as 0..1. A plate is armor and
 *   passes on part of it; the head passes on all of it, which is what makes it the place to aim.
 *   Without that, a body ten parts long would soak every shot of a spread and every target of a
 *   piercing one at full weight, and melt before its fight had begun.
 */
class BossPartComponent(val share: Float = 1f) : Component
