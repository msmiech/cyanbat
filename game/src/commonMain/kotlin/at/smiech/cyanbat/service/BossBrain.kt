package at.smiech.cyanbat.service

/**
 * The logic of a boss that does more than follow a movement pattern, such as the
 * Moth Queen's phases or the Sand Wyrm's breaches. Run by [EnemyGenerator] on the
 * stage clock once the boss arrives.
 *
 * A brain only changes state on the boss's components (its gun, weapon cadence, shield, movement),
 * so the systems still run everything the boss does; the brain decides when it changes.
 */
interface BossBrain {
    /** One tick of the fight, after the world has moved for it. */
    fun update(deltaTime: Float)
}
