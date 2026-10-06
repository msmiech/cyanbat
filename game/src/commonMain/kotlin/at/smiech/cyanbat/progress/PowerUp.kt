package at.smiech.cyanbat.progress

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.power_up_armor_plating
import at.smiech.cyanbat.resources.power_up_armor_plating_description
import at.smiech.cyanbat.resources.power_up_bounty_hunter
import at.smiech.cyanbat.resources.power_up_bounty_hunter_description
import at.smiech.cyanbat.resources.power_up_charged_trail
import at.smiech.cyanbat.resources.power_up_charged_trail_description
import at.smiech.cyanbat.resources.power_up_charged_trail_held
import at.smiech.cyanbat.resources.power_up_counterweight
import at.smiech.cyanbat.resources.power_up_counterweight_description
import at.smiech.cyanbat.resources.power_up_fast_learner
import at.smiech.cyanbat.resources.power_up_fast_learner_description
import at.smiech.cyanbat.resources.power_up_frost_beam
import at.smiech.cyanbat.resources.power_up_frost_beam_description
import at.smiech.cyanbat.resources.power_up_frost_beam_held
import at.smiech.cyanbat.resources.power_up_guardian_orb
import at.smiech.cyanbat.resources.power_up_guardian_orb_description
import at.smiech.cyanbat.resources.power_up_guardian_orb_held
import at.smiech.cyanbat.resources.power_up_heavy_rounds
import at.smiech.cyanbat.resources.power_up_heavy_rounds_description
import at.smiech.cyanbat.resources.power_up_piercing_shot
import at.smiech.cyanbat.resources.power_up_piercing_shot_description
import at.smiech.cyanbat.resources.power_up_rapid_fire
import at.smiech.cyanbat.resources.power_up_rapid_fire_description
import at.smiech.cyanbat.resources.power_up_regeneration
import at.smiech.cyanbat.resources.power_up_regeneration_description
import at.smiech.cyanbat.resources.power_up_ricochet
import at.smiech.cyanbat.resources.power_up_ricochet_description
import at.smiech.cyanbat.resources.power_up_second_life
import at.smiech.cyanbat.resources.power_up_second_life_description
import at.smiech.cyanbat.resources.power_up_second_wind
import at.smiech.cyanbat.resources.power_up_second_wind_description
import at.smiech.cyanbat.resources.power_up_sharpshooter
import at.smiech.cyanbat.resources.power_up_sharpshooter_description
import at.smiech.cyanbat.resources.power_up_spread_shot
import at.smiech.cyanbat.resources.power_up_spread_shot_description
import at.smiech.cyanbat.resources.power_up_vitality
import at.smiech.cyanbat.resources.power_up_vitality_description
import at.smiech.cyanbat.util.ARMOR_FACTOR
import at.smiech.cyanbat.util.COUNTERWEIGHT_BONUS
import at.smiech.cyanbat.util.COUNTERWEIGHT_REDUCTION
import at.smiech.cyanbat.util.CRITICAL_CHANCE_BONUS
import at.smiech.cyanbat.util.HEAVY_ROUNDS_DAMAGE
import at.smiech.cyanbat.util.POWER_UP_CHOICES
import at.smiech.cyanbat.util.RAPID_FIRE_FACTOR
import at.smiech.cyanbat.util.REGEN_PER_SECOND
import at.smiech.cyanbat.util.SCORE_BONUS
import at.smiech.cyanbat.util.SECOND_WIND_SECONDS
import at.smiech.cyanbat.util.VITALITY_HIT_POINTS
import at.smiech.cyanbat.util.XP_BONUS
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A 0..1 fraction as the percentage a card shows, so the cards' numbers cannot drift from what the
 * power-ups apply. The translated text adds the percent sign where its language puts it.
 */
private fun percent(fraction: Float): Int = (fraction * 100).roundToInt()

/**
 * One of the upgrades offered when the bat levels up: a title, a one-line description, and the
 * single change it makes to a [PlayerLoadout]. It never touches the world; the screen makes the bat
 * match the loadout. It names its strings rather than holding text, so the screen reads them in the
 * player's language.
 *
 * @param title what it is called, kept short enough to fit a card in the level up dialog.
 * @param description what it does, in the player's terms rather than the loadout's.
 * @param numbers what [description] quotes, in the order of its placeholders.
 */
enum class PowerUp(
    val title: StringResource,
    val description: StringResource,
    val numbers: List<Int> = emptyList(),
) {

    RAPID_FIRE(Res.string.power_up_rapid_fire, Res.string.power_up_rapid_fire_description) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.quickenShots(RAPID_FIRE_FACTOR)
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canQuickenShots
    },

    SPREAD_SHOT(Res.string.power_up_spread_shot, Res.string.power_up_spread_shot_description) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addShot()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddShot
    },

    VITALITY(
        Res.string.power_up_vitality,
        Res.string.power_up_vitality_description,
        listOf(VITALITY_HIT_POINTS),
    ) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.gainMaxHealth(VITALITY_HIT_POINTS)
    },

    HEAVY_ROUNDS(
        Res.string.power_up_heavy_rounds,
        Res.string.power_up_heavy_rounds_description,
        listOf(HEAVY_ROUNDS_DAMAGE),
    ) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addShotDamage(HEAVY_ROUNDS_DAMAGE)
    },

    SHARPSHOOTER(
        Res.string.power_up_sharpshooter,
        Res.string.power_up_sharpshooter_description,
        listOf(percent(CRITICAL_CHANCE_BONUS)),
    ) {
        override fun applyTo(loadout: PlayerLoadout) =
            loadout.addCriticalChance(CRITICAL_CHANCE_BONUS)

        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddCriticalChance
    },

    ARMOR_PLATING(Res.string.power_up_armor_plating, Res.string.power_up_armor_plating_description) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.reduceDamageTaken(ARMOR_FACTOR)
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canReduceDamageTaken
    },

    SECOND_WIND(Res.string.power_up_second_wind, Res.string.power_up_second_wind_description) {
        override fun applyTo(loadout: PlayerLoadout) =
            loadout.lengthenHitCooldown(SECOND_WIND_SECONDS)

        override fun isAvailable(loadout: PlayerLoadout) = loadout.canLengthenHitCooldown
    },

    REGENERATION(
        Res.string.power_up_regeneration,
        Res.string.power_up_regeneration_description,
        listOf(REGEN_PER_SECOND),
    ) {
        override fun applyTo(loadout: PlayerLoadout) =
            loadout.addHealthRegen(REGEN_PER_SECOND.toFloat())

        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddHealthRegen
    },

    FAST_LEARNER(
        Res.string.power_up_fast_learner,
        Res.string.power_up_fast_learner_description,
        listOf(percent(XP_BONUS)),
    ) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addExperienceBonus(XP_BONUS)
    },

    BOUNTY_HUNTER(
        Res.string.power_up_bounty_hunter,
        Res.string.power_up_bounty_hunter_description,
        listOf(percent(SCORE_BONUS)),
    ) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addScoreBonus(SCORE_BONUS)
    },

    SECOND_LIFE(Res.string.power_up_second_life, Res.string.power_up_second_life_description) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addRevive()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddRevive
    },

    COUNTERWEIGHT(
        Res.string.power_up_counterweight,
        Res.string.power_up_counterweight_description,
        listOf(COUNTERWEIGHT_REDUCTION, percent(COUNTERWEIGHT_BONUS)),
    ) {
        override fun applyTo(loadout: PlayerLoadout) =
            loadout.counterweight(COUNTERWEIGHT_REDUCTION, COUNTERWEIGHT_BONUS)

        override fun isAvailable(loadout: PlayerLoadout) = loadout.canCounterweight
    },

    PIERCING_SHOT(Res.string.power_up_piercing_shot, Res.string.power_up_piercing_shot_description) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addPierce()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddPierce
    },

    RICOCHET(Res.string.power_up_ricochet, Res.string.power_up_ricochet_description) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addBounce()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddBounce
    },

    GUARDIAN_ORB(Res.string.power_up_guardian_orb, Res.string.power_up_guardian_orb_description) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.addOrb()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canAddOrb
        override fun describe(loadout: PlayerLoadout) =
            if (loadout.orbs > 0) Res.string.power_up_guardian_orb_held else description
    },

    CHARGED_TRAIL(Res.string.power_up_charged_trail, Res.string.power_up_charged_trail_description) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.chargeWake()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canChargeWake
        override fun describe(loadout: PlayerLoadout) =
            if (loadout.wakeLevel > 0) Res.string.power_up_charged_trail_held else description
    },

    FROST_BEAM(Res.string.power_up_frost_beam, Res.string.power_up_frost_beam_description) {
        override fun applyTo(loadout: PlayerLoadout) = loadout.buildFrostBeam()
        override fun isAvailable(loadout: PlayerLoadout) = loadout.canBuildFrostBeam
        override fun describe(loadout: PlayerLoadout) =
            if (loadout.frostLevel > 0) Res.string.power_up_frost_beam_held else description
    };

    /** Makes this power-up's change to [loadout]. */
    abstract fun applyTo(loadout: PlayerLoadout)

    /**
     * What the card says to a run with [loadout]: its [description], unless picking it again does
     * something different, as for the standalone weapons (a second orb is not the same news as the
     * first).
     */
    open fun describe(loadout: PlayerLoadout): StringResource = description

    /**
     * Whether this is still worth offering.
     *
     * Stacking power-ups clamp, and one at its clamp would waste a third of the
     * choice without the player being able to tell. The four with no ceiling
     * ([VITALITY], [HEAVY_ROUNDS], [FAST_LEARNER] and [BOUNTY_HUNTER]) never opt
     * out, which guarantees an offer can always be filled.
     */
    open fun isAvailable(loadout: PlayerLoadout): Boolean = true

    companion object {
        /**
         * [count] distinct power-ups drawn at random from those [loadout] can still use. Should
         * fewer be available, the offer is short rather than padded with duplicates.
         */
        fun offer(
            loadout: PlayerLoadout,
            random: Random = Random.Default,
            count: Int = POWER_UP_CHOICES,
        ): List<PowerUp> = entries.filter { it.isAvailable(loadout) }.shuffled(random).take(count)
    }
}
