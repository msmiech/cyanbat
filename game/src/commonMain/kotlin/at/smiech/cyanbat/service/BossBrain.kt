package at.smiech.cyanbat.service

/**
 * The logic of a boss that does more than its movement pattern: the Moth Queen's phases, the Sand
 * Wyrm's breaches. Run by [EnemyGenerator] on the stage clock from the moment the boss arrives.
 *
 * What a brain changes is state on the boss's own components - its gun, its weapon's cadence, its
 * shield, its movement - so what the boss does is still exactly what the systems run. The brain only
 * decides when it changes.
 */
interface BossBrain {
    /** One tick of the fight, after the world has moved for it. */
    fun update(deltaTime: Float)
}
