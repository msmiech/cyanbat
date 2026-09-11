package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.ARMOUR_FACTOR
import at.smiech.cyanbat.util.HEAVY_ROUNDS_DAMAGE
import at.smiech.cyanbat.util.POWER_UP_CHOICES
import at.smiech.cyanbat.util.RAPID_FIRE_FACTOR
import at.smiech.cyanbat.util.SECOND_WIND_SECONDS
import at.smiech.cyanbat.util.VITALITY_HIT_POINTS
import kotlin.random.Random

/**
 * One of the upgrades offered when the bat levels up.
 *
 * Each is a title, a line the player can read in the second they spend deciding, and the single
 * change it makes to a [PlayerLoadout]. Nothing here reaches into the world: a power-up says what
 * the bat is now, and the screen is what makes the bat match.
 *
 * @param title what it is called, kept short enough to fit a card on a 480px framebuffer.
 * @param description what it does, in the player's terms rather than the loadout's.
 */
enum class PowerUp(val title: String, val description: String) {

    RAPID_FIRE("Rapid Fire", "Shots come faster") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.quickenShots(RAPID_FIRE_FACTOR)
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canQuickenShots
    },

    SPREAD_SHOT("Spread Shot", "Fire one more, fanned out") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addShot()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddShot
    },

    VITALITY("Vitality", "+$VITALITY_HIT_POINTS max health, healed") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.gainMaxHealth(VITALITY_HIT_POINTS)
    },

    HEAVY_ROUNDS("Heavy Rounds", "+$HEAVY_ROUNDS_DAMAGE damage per shot") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addShotDamage(HEAVY_ROUNDS_DAMAGE)
    },

    ARMOUR_PLATING("Armour Plating", "Take less damage") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.reduceDamageTaken(ARMOUR_FACTOR)
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canReduceDamageTaken
    },

    SECOND_WIND("Second Wind", "Longer mercy after a hit") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.lengthenHitCooldown(SECOND_WIND_SECONDS)
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canLengthenHitCooldown
    };

    abstract fun applyTo(loadout: PlayerLoadout)

    /**
     * Whether this is still worth offering.
     *
     * The stacking power-ups clamp, and one already at its clamp is a wasted third of a choice -
     * worse than a wasted pick, because the player cannot tell it is wasted until after they take
     * it. The two that scale without a ceiling never opt out, which is what guarantees an offer
     * can always be filled.
     */
    open fun isAvailable(loadout: PlayerLoadout): Boolean = true

    companion object {
        /**
         * [POWER_UP_CHOICES] distinct power-ups to choose between, drawn at random from the ones
         * [loadout] can still use.
         *
         * Distinct, because two identical cards is a choice that is not one. If fewer than three
         * remain useful the offer is short rather than padded - [VITALITY] and [HEAVY_ROUNDS] have
         * no ceiling, so it can never be empty.
         */
        fun offer(
            loadout: PlayerLoadout,
            random: Random = Random.Default,
            count: Int = POWER_UP_CHOICES,
        ): List<PowerUp> = entries.filter { it.isAvailable(loadout) }.shuffled(random).take(count)
    }
}
