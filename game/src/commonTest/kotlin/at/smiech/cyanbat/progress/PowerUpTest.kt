package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.ARMOUR_FLOOR
import at.smiech.cyanbat.util.HEAVY_ROUNDS_DAMAGE
import at.smiech.cyanbat.util.MAX_EXTRA_SHOTS
import at.smiech.cyanbat.util.MAX_HIT_COOLDOWN_SECONDS
import at.smiech.cyanbat.util.MIN_SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.POWER_UP_CHOICES
import at.smiech.cyanbat.util.VITALITY_HIT_POINTS
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    // endregion

    // region clamps

    /**
     * Every stacking power-up has to bottom out somewhere. A shot interval reaching zero would
     * fire every tick, and damage reaching zero would leave a run that cannot be lost.
     */
    @Test
    fun `stacking a power-up never runs off the end`() {
        take(PowerUp.RAPID_FIRE, times = 50)
        take(PowerUp.SPREAD_SHOT, times = 50)
        take(PowerUp.ARMOUR_PLATING, times = 50)
        take(PowerUp.SECOND_WIND, times = 50)

        assertEquals(MIN_SHOT_INTERVAL_SECONDS, loadout.shotIntervalSeconds)
        assertEquals(MAX_EXTRA_SHOTS, loadout.extraShots)
        assertEquals(ARMOUR_FLOOR, loadout.damageTaken)
        assertEquals(MAX_HIT_COOLDOWN_SECONDS, loadout.hitCooldownSeconds)
    }

    @Test
    fun `a power-up already at its clamp stops being offered`() {
        take(PowerUp.SPREAD_SHOT, times = MAX_EXTRA_SHOTS)

        assertTrue(!PowerUp.SPREAD_SHOT.isAvailable(loadout))
        assertTrue(PowerUp.offer(loadout, Random(1)).none { it == PowerUp.SPREAD_SHOT })
    }

    /** These two scale without a ceiling, which is what keeps an offer fillable forever. */
    @Test
    fun `the uncapped power-ups are always available`() {
        take(PowerUp.VITALITY, times = 100)
        take(PowerUp.HEAVY_ROUNDS, times = 100)

        assertTrue(PowerUp.VITALITY.isAvailable(loadout))
        assertTrue(PowerUp.HEAVY_ROUNDS.isAvailable(loadout))
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
     * without picking. Max everything that can be maxed and check the offer still fills.
     */
    @Test
    fun `an offer can always be filled, however maxed out the run is`() {
        take(PowerUp.RAPID_FIRE, times = 50)
        take(PowerUp.SPREAD_SHOT, times = 50)
        take(PowerUp.ARMOUR_PLATING, times = 50)
        take(PowerUp.SECOND_WIND, times = 50)

        val offer = PowerUp.offer(loadout, Random(3))

        assertEquals(listOf(PowerUp.VITALITY, PowerUp.HEAVY_ROUNDS).sortedBy { it.name }, offer.sortedBy { it.name })
        assertTrue(offer.isNotEmpty())
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
