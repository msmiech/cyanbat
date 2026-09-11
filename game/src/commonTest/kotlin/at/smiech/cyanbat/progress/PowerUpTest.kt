package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.ARMOUR_FLOOR
import at.smiech.cyanbat.util.COUNTERWEIGHT_REDUCTION
import at.smiech.cyanbat.util.HEAVY_ROUNDS_DAMAGE
import at.smiech.cyanbat.util.MAX_EXTRA_SHOTS
import at.smiech.cyanbat.util.MAX_FLAT_DAMAGE_REDUCTION
import at.smiech.cyanbat.util.MAX_HEALTH_REGEN_PER_SECOND
import at.smiech.cyanbat.util.MAX_HIT_COOLDOWN_SECONDS
import at.smiech.cyanbat.util.MAX_REVIVES
import at.smiech.cyanbat.util.MAX_SHOT_BOUNCE
import at.smiech.cyanbat.util.MAX_SHOT_PIERCE
import at.smiech.cyanbat.util.MIN_SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.POWER_UP_CHOICES
import at.smiech.cyanbat.util.REGEN_PER_SECOND
import at.smiech.cyanbat.util.SCORE_BONUS
import at.smiech.cyanbat.util.VITALITY_HIT_POINTS
import at.smiech.cyanbat.util.XP_BONUS
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The power-ups that scale without a ceiling, and so are still on offer however deep a run has
 * already invested in them. Enough of them is what guarantees a level up always has three things
 * to show; see `an offer can always be filled`.
 */
private val UNCAPPED = setOf(
    PowerUp.VITALITY,
    PowerUp.HEAVY_ROUNDS,
    PowerUp.FAST_LEARNER,
    PowerUp.BOUNTY_HUNTER,
)

class PowerUpTest {

    private val loadout = PlayerLoadout()

    private fun take(powerUp: PowerUp, times: Int = 1) = repeat(times) { powerUp.applyTo(loadout) }

    // region what each one does

    @Test
    fun `rapid fire shortens the gap between shots`() {
        val before = loadout.shotIntervalSeconds

        take(PowerUp.RAPID_FIRE)

        assertTrue(loadout.shotIntervalSeconds < before)
    }

    @Test
    fun `spread shot adds one shot at a time`() {
        take(PowerUp.SPREAD_SHOT)
        assertEquals(1, loadout.extraShots)

        take(PowerUp.SPREAD_SHOT)
        assertEquals(2, loadout.extraShots)
    }

    /** A wider bar that arrived empty would be worth nothing at the moment it is chosen. */
    @Test
    fun `vitality widens the health bar and fills the new room`() {
        take(PowerUp.VITALITY)

        assertEquals(PlayerLoadout().maxHitPoints + VITALITY_HIT_POINTS, loadout.maxHitPoints)
        assertEquals(VITALITY_HIT_POINTS, loadout.takePendingHeal())
        assertEquals(0, loadout.takePendingHeal(), "the heal should only be handed out once")
    }

    @Test
    fun `heavy rounds raise what a shot takes off`() {
        val before = loadout.shotDamage

        take(PowerUp.HEAVY_ROUNDS)

        assertEquals(before + HEAVY_ROUNDS_DAMAGE, loadout.shotDamage)
    }

    @Test
    fun `armour plating cuts incoming damage`() {
        take(PowerUp.ARMOUR_PLATING)

        assertTrue(loadout.damageTaken < 1f)
    }

    @Test
    fun `second wind lengthens the mercy after a hit`() {
        val before = loadout.hitCooldownSeconds

        take(PowerUp.SECOND_WIND)

        assertTrue(loadout.hitCooldownSeconds > before)
    }

    @Test
    fun `regeneration heals over time`() {
        take(PowerUp.REGENERATION)
        assertEquals(REGEN_PER_SECOND.toFloat(), loadout.healthRegenPerSecond)

        take(PowerUp.REGENERATION)
        assertEquals(2f * REGEN_PER_SECOND, loadout.healthRegenPerSecond)
    }

    @Test
    fun `fast learner raises experience earned`() {
        take(PowerUp.FAST_LEARNER)

        assertEquals(1f + XP_BONUS, loadout.experienceMultiplier)
    }

    @Test
    fun `bounty hunter raises score earned`() {
        take(PowerUp.BOUNTY_HUNTER)

        assertEquals(1f + SCORE_BONUS, loadout.scoreMultiplier)
    }

    @Test
    fun `second life banks a death the bat can walk away from`() {
        take(PowerUp.SECOND_LIFE)
        assertEquals(1, loadout.revives)

        assertTrue(loadout.useRevive())
        assertEquals(0, loadout.revives)
        assertTrue(!loadout.useRevive(), "a spent revive must not come back")
    }

    /** The pair is the point: it is the pick for a player who means to stand and trade. */
    @Test
    fun `counterweight blunts incoming damage and sharpens outgoing`() {
        val damageBefore = loadout.shotDamage

        take(PowerUp.COUNTERWEIGHT)

        assertEquals(COUNTERWEIGHT_REDUCTION, loadout.flatDamageReduction)
        assertTrue(loadout.shotDamage > damageBefore, "outgoing damage did not rise")
    }

    /** Rounded up, so the smallest raise is worth a point rather than nothing at all. */
    @Test
    fun `counterweight always raises damage by at least a point`() {
        repeat(5) {
            val before = loadout.shotDamage
            take(PowerUp.COUNTERWEIGHT)
            assertTrue(loadout.shotDamage >= before + 1, "a pick added nothing: $before")
        }
    }

    @Test
    fun `piercing shot lets a shot through one more enemy`() {
        take(PowerUp.PIERCING_SHOT)
        assertEquals(1, loadout.shotPierce)

        take(PowerUp.PIERCING_SHOT)
        assertEquals(2, loadout.shotPierce)
    }

    @Test
    fun `ricochet buys a shot one more reflection`() {
        take(PowerUp.RICOCHET)
        assertEquals(1, loadout.shotBounce)

        take(PowerUp.RICOCHET)
        assertEquals(2, loadout.shotBounce)
    }

    // endregion

    // region clamps

    /**
     * Every stacking power-up has to bottom out somewhere. A shot interval reaching zero would
     * fire every tick, and damage reaching zero would leave a run that cannot be lost.
     */
    @Test
    fun `stacking a power-up never runs off the end`() {
        maxOutEveryCappedPowerUp()

        assertEquals(MIN_SHOT_INTERVAL_SECONDS, loadout.shotIntervalSeconds)
        assertEquals(MAX_EXTRA_SHOTS, loadout.extraShots)
        assertEquals(ARMOUR_FLOOR, loadout.damageTaken)
        assertEquals(MAX_HIT_COOLDOWN_SECONDS, loadout.hitCooldownSeconds)
        assertEquals(MAX_HEALTH_REGEN_PER_SECOND, loadout.healthRegenPerSecond)
        assertEquals(MAX_REVIVES, loadout.revives)
        assertEquals(MAX_FLAT_DAMAGE_REDUCTION, loadout.flatDamageReduction)
        assertEquals(MAX_SHOT_PIERCE, loadout.shotPierce)
        assertEquals(MAX_SHOT_BOUNCE, loadout.shotBounce)
    }

    private fun maxOutEveryCappedPowerUp() =
        PowerUp.entries.forEach { take(it, times = 60) }

    @Test
    fun `a power-up already at its clamp stops being offered`() {
        take(PowerUp.SPREAD_SHOT, times = MAX_EXTRA_SHOTS)

        assertTrue(!PowerUp.SPREAD_SHOT.isAvailable(loadout))
        assertTrue(PowerUp.offer(loadout, Random(1)).none { it == PowerUp.SPREAD_SHOT })
    }

    /** These scale without a ceiling, which is what keeps an offer fillable forever. */
    @Test
    fun `the uncapped power-ups are always available`() {
        maxOutEveryCappedPowerUp()

        assertEquals(UNCAPPED, PowerUp.entries.filter { it.isAvailable(loadout) }.toSet())
    }

    // endregion

    // region the offer

    @Test
    fun `an offer is three distinct power-ups`() {
        val offer = PowerUp.offer(loadout, Random(20260911))

        assertEquals(POWER_UP_CHOICES, offer.size)
        assertEquals(offer.size, offer.toSet().size, "two cards offered the same thing: $offer")
    }

    @Test
    fun `the offer varies between level ups`() {
        val random = Random(20260911)
        val offers = List(20) { PowerUp.offer(loadout, random) }

        assertTrue(offers.toSet().size > 1, "every level up offered the same three")
    }

    /**
     * A dialog with nothing on it would freeze the run, since there is no way to dismiss it
     * without picking. Max everything that can be maxed and check a full offer still fills.
     */
    @Test
    fun `an offer can always be filled, however maxed out the run is`() {
        maxOutEveryCappedPowerUp()

        val offer = PowerUp.offer(loadout, Random(3))

        assertEquals(POWER_UP_CHOICES, offer.size)
        assertTrue(UNCAPPED.containsAll(offer), "a maxed-out run was offered something capped: $offer")
    }

    /**
     * The guarantee above only holds while enough power-ups refuse to cap. Pinning the count means
     * a future one that forgets to say so cannot quietly shrink the pool below a full offer.
     */
    @Test
    fun `enough power-ups are uncapped to fill an offer on their own`() {
        assertTrue(
            UNCAPPED.size >= POWER_UP_CHOICES,
            "only ${UNCAPPED.size} power-ups are uncapped, which cannot fill an offer of $POWER_UP_CHOICES"
        )
    }

    @Test
    fun `every power-up says what it is in words that fit a card`() {
        PowerUp.entries.forEach {
            assertTrue(it.title.length <= 16, "${it.name} has a title too long for a card: ${it.title}")
            assertTrue(it.description.length <= 44, "${it.name} has a description too long: ${it.description}")
        }
    }

    // endregion
}
