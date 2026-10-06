package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.Volley
import at.smiech.cyanbat.util.MOTH_QUEEN_ENRAGED_SUMMON_SECONDS
import at.smiech.cyanbat.util.MOTH_QUEEN_ENRAGED_TEMPO
import at.smiech.cyanbat.util.MOTH_QUEEN_FAN_DAMAGE
import at.smiech.cyanbat.util.MOTH_QUEEN_PHASE_2_AT
import at.smiech.cyanbat.util.MOTH_QUEEN_PHASE_3_AT
import at.smiech.cyanbat.util.MOTH_QUEEN_RING_DAMAGE
import at.smiech.cyanbat.util.MOTH_QUEEN_SHIELD_FRACTION
import at.smiech.cyanbat.util.MOTH_QUEEN_SUMMON_SECONDS
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.ShieldComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.World
import kotlin.math.roundToInt

/**
 * The Moth Queen's fight, in three phases marked by her health.
 *
 * 1. **Courting.** She traces her figure eight and fires fans of three at the player.
 * 2. **The swarm.** At two thirds she raises a shield, fires rings with lanes to thread, and calls
 *    in a wasp swarm every few seconds.
 * 3. **Enraged.** At one third the shield rises again, she flies faster, and alternates a wide
 *    five-way fan with a denser ring, with the swarms coming quicker.
 *
 * Phases follow her health rather than a clock, so hitting hard moves the fight on
 * and dodging is not punished. A hit that drops her past both thresholds plays both
 * entrances in order, so no shield is skipped.
 *
 * Like every [BossBrain], this only changes state on her components.
 *
 * @param onSummon called when she calls in a swarm; the generator is what knows how to spawn one.
 * @param onPhaseChanged called with the phase she has just entered, 2 or 3, for the screen to
 *   announce.
 */
class MothQueenBrain(
    private val world: World,
    private val bossId: EntityId,
    private val onSummon: () -> Unit = {},
    private val onPhaseChanged: (Int) -> Unit = {},
) : BossBrain {
    /** The phase she is in, 1 to 3. */
    var phase = 1
        private set

    /** Seconds until the next swarm, from phase 2 on. */
    private var summonTimer = 0f

    override fun update(deltaTime: Float) {
        val health = world.getComponent(bossId, HealthComponent::class) ?: return
        if (!health.alive) return

        val due = phaseFor(health.fraction)
        while (phase < due) enter(phase + 1, health)

        if (phase < 2) return
        summonTimer -= deltaTime
        if (summonTimer <= 0f) {
            summonTimer += summonInterval()
            onSummon()
        }
    }

    /**
     * Plays the entrance of phase [next]: a fresh shield, the phase's gun and, enraged, more speed.
     */
    private fun enter(next: Int, health: HealthComponent) {
        phase = next

        world.getComponent(bossId, ShieldComponent::class)
            ?.raise(
                (health.maxHitPoints * MOTH_QUEEN_SHIELD_FRACTION).roundToInt().coerceAtLeast(1)
            )

        val gun = gunFor(next)
        world.getComponent(bossId, GunComponent::class)?.apply {
            volleys = gun.volleys
            this.next = 0
        }
        world.getComponent(bossId, WeaponComponent::class)?.interval = gun.interval

        if (next >= 3) {
            world.getComponent(bossId, EnemyBehaviorComponent::class)?.tempo =
                MOTH_QUEEN_ENRAGED_TEMPO
        }

        // The phase's first swarm comes a moment after its shield, so the two land as separate
        // events.
        summonTimer = FIRST_SUMMON_DELAY
        onPhaseChanged(next)
    }

    private fun summonInterval(): Float =
        if (phase >= 3) MOTH_QUEEN_ENRAGED_SUMMON_SECONDS else MOTH_QUEEN_SUMMON_SECONDS

    companion object {
        /** Seconds from a phase's shield to its first swarm. */
        private const val FIRST_SUMMON_DELAY = 2.5f

        /** What she opens the fight with: fans of three, at a pace the player can learn. */
        val OPENING_GUN = EnemyGun(
            1.5f,
            listOf(
                Volley(
                    ShotPattern.AIMED_FAN, count = 3, spreadDegrees = 16f, speed = 2.6f,
                    damageFactor = MOTH_QUEEN_FAN_DAMAGE,
                ),
            ),
        )

        /** Rings to thread, and a single aimed shot between them so standing still is not safe. */
        private val SWARM_GUN = EnemyGun(
            1.2f,
            listOf(
                Volley(
                    ShotPattern.RADIAL,
                    count = 12,
                    speed = 2.0f,
                    damageFactor = MOTH_QUEEN_RING_DAMAGE
                ),
                Volley(ShotPattern.AIMED, speed = 2.8f, damageFactor = MOTH_QUEEN_FAN_DAMAGE),
            ),
        )

        /** Faster, wider and denser: the fight's last minute. */
        private val ENRAGED_GUN = EnemyGun(
            1.1f,
            listOf(
                Volley(
                    ShotPattern.AIMED_FAN, count = 5, spreadDegrees = 13f, speed = 2.9f,
                    damageFactor = MOTH_QUEEN_FAN_DAMAGE,
                ),
                Volley(
                    ShotPattern.RADIAL,
                    count = 14,
                    speed = 2.2f,
                    damageFactor = MOTH_QUEEN_RING_DAMAGE
                ),
            ),
        )

        /** Her gun in [phase]. */
        fun gunFor(phase: Int): EnemyGun = when {
            phase >= 3 -> ENRAGED_GUN
            phase == 2 -> SWARM_GUN
            else -> OPENING_GUN
        }

        /** The phase her health puts her in. */
        fun phaseFor(healthFraction: Float): Int = when {
            healthFraction <= MOTH_QUEEN_PHASE_3_AT -> 3
            healthFraction <= MOTH_QUEEN_PHASE_2_AT -> 2
            else -> 1
        }
    }
}
