package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.ARMOUR_FACTOR
import at.smiech.cyanbat.util.COUNTERWEIGHT_BONUS
import at.smiech.cyanbat.util.COUNTERWEIGHT_REDUCTION
import at.smiech.cyanbat.util.HEAVY_ROUNDS_DAMAGE
import at.smiech.cyanbat.util.POWER_UP_CHOICES
import at.smiech.cyanbat.util.RAPID_FIRE_FACTOR
import at.smiech.cyanbat.util.REGEN_PER_SECOND
import at.smiech.cyanbat.util.SCORE_BONUS
import at.smiech.cyanbat.util.SECOND_WIND_SECONDS
import at.smiech.cyanbat.util.VITALITY_HIT_POINTS
import at.smiech.cyanbat.util.XP_BONUS
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A 0..1 fraction as the percentage a card shows, so the numbers on the cards can never drift from
 * the numbers the power-ups actually apply.
 */
private fun percent(fraction: Float): String = "${(fraction * 100).roundToInt()}%"

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
    },

    REGENERATION("Regeneration", "Heal $REGEN_PER_SECOND health a second") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addHealthRegen(REGEN_PER_SECOND.toFloat())
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddHealthRegen
    },

    FAST_LEARNER("Fast Learner", "+${percent(XP_BONUS)} experience earned") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addExperienceBonus(XP_BONUS)
    },

    BOUNTY_HUNTER("Bounty Hunter", "+${percent(SCORE_BONUS)} score earned") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addScoreBonus(SCORE_BONUS)
    },

    SECOND_LIFE("Second Life", "Cheat death once, at half health") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addRevive()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddRevive
    },

    COUNTERWEIGHT("Counterweight", "-$COUNTERWEIGHT_REDUCTION damage taken, +${percent(COUNTERWEIGHT_BONUS)} dealt") {
        override fun applyTo(loadout: PlayerLoadout) =
            loadout.counterweight(COUNTERWEIGHT_REDUCTION, COUNTERWEIGHT_BONUS)

        override fun isAvailable(loadout: PlayerLoadout) = loadout.canCounterweight
    },

    PIERCING_SHOT("Piercing Shot", "Shots pass through one more") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addPierce()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddPierce
    },

    RICOCHET("Ricochet", "Shots bounce off one more edge") {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addBounce()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddBounce
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
