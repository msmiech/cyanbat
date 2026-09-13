package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.CRITICAL_CHANCE
import at.smiech.cyanbat.util.CRITICAL_DAMAGE_MULTIPLIER
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.HEAVY_ROUNDS_DAMAGE
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The critical hit's two numbers, and the one relationship between them that matters: a crit is
 * the bat's *own* gun landing well, so it has to move when the gun does.
 */
class CriticalHitTest {

    private val loadout = PlayerLoadout()

    @Test
    fun `a run opens at the base critical chance`() {
        assertEquals(CRITICAL_CHANCE, loadout.criticalChance)
    }

    @Test
    fun `a critical is worth the multiplier over an ordinary shot`() {
        assertEquals(
            (DAMAGE_PER_HIT * CRITICAL_DAMAGE_MULTIPLIER).roundToInt(),
            loadout.criticalDamage,
        )
    }

    /**
     * The reason [PlayerLoadout.criticalDamage] is derived rather than stored. A crit fixed at
     * spawn would quietly become the *worse* outcome in a run that stacked Heavy Rounds far
     * enough - a "critical" hit for less than a normal one.
     */
    @Test
    fun `heavy rounds raises what a critical is worth`() {
        val before = loadout.criticalDamage

        loadout.addShotDamage(HEAVY_ROUNDS_DAMAGE)

        assertTrue(
            loadout.criticalDamage > before,
            "critical damage did not follow the gun: $before -> ${loadout.criticalDamage}",
        )
        assertEquals(
            (loadout.shotDamage * CRITICAL_DAMAGE_MULTIPLIER).roundToInt(),
            loadout.criticalDamage,
        )
    }

    @Test
    fun `a critical always beats an ordinary shot, however upgraded the gun is`() {
        repeat(40) {
            loadout.addShotDamage(HEAVY_ROUNDS_DAMAGE)
            assertTrue(
                loadout.criticalDamage > loadout.shotDamage,
                "critical ${loadout.criticalDamage} is not above ordinary ${loadout.shotDamage}",
            )
        }
    }

    /**
     * Counterweight raises shot damage by a fraction rather than a flat amount, so it is the other
     * route into the gun and has to carry the crit with it too.
     */
    @Test
    fun `counterweight raises what a critical is worth`() {
        val before = loadout.criticalDamage

        loadout.counterweight(damageReduction = 1, extraDamageFraction = 0.10f)

        assertTrue(loadout.criticalDamage > before)
    }

    /** One in a hundred: rare enough to be an event, and deliberately not zero or a certainty. */
    @Test
    fun `the critical chance is a small probability`() {
        assertTrue(
            loadout.criticalChance > 0f && loadout.criticalChance < 0.1f,
            "expected a rare chance, was ${loadout.criticalChance}",
        )
    }
}
