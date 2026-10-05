package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.ARMOR_FLOOR
import at.smiech.cyanbat.util.CRITICAL_CHANCE
import at.smiech.cyanbat.util.CRITICAL_DAMAGE_MULTIPLIER
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.FROST_BEAM_INTERVAL_FACTOR
import at.smiech.cyanbat.util.FROST_BEAM_INTERVAL_SECONDS
import at.smiech.cyanbat.util.FROST_SECONDS
import at.smiech.cyanbat.util.FROST_SECONDS_PER_LEVEL
import at.smiech.cyanbat.util.MAX_CRITICAL_CHANCE
import at.smiech.cyanbat.util.MAX_EXTRA_SHOTS
import at.smiech.cyanbat.util.MAX_FLAT_DAMAGE_REDUCTION
import at.smiech.cyanbat.util.MAX_FROST_LEVEL
import at.smiech.cyanbat.util.MAX_HEALTH_REGEN_PER_SECOND
import at.smiech.cyanbat.util.MAX_HIT_COOLDOWN_SECONDS
import at.smiech.cyanbat.util.MAX_ORBS
import at.smiech.cyanbat.util.MAX_REVIVES
import at.smiech.cyanbat.util.MAX_SHOT_BOUNCE
import at.smiech.cyanbat.util.MAX_SHOT_PIERCE
import at.smiech.cyanbat.util.MAX_WAKE_LEVEL
import at.smiech.cyanbat.util.MIN_SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.ORB_DAMAGE_FRACTION
import at.smiech.cyanbat.util.PLAYER_HIT_COOLDOWN_SECONDS
import at.smiech.cyanbat.util.PLAYER_MAX_HIT_POINTS
import at.smiech.cyanbat.util.SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.WAKE_DAMAGE_FRACTION
import at.smiech.cyanbat.util.WAKE_SECONDS
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Everything a power-up can change about the bat, in one place.
 *
 * The bat's own components stay the source of truth for what is happening *now* - how much health
 * is left, when the next shot is due - while this holds what the run has earned. The screen syncs
 * the two after every pick, which keeps a power-up a single value change here rather than a hunt
 * through the world for the entities it touches.
 *
 * Every field is clamped at the point it is written, so a stacking power-up cannot run off the end
 * into a shot interval of zero or damage the bat cannot take.
 */
class PlayerLoadout {

    /** Seconds between shots. Lower is faster; floored so the bat cannot fire every tick. */
    var shotIntervalSeconds: Float = SHOT_INTERVAL_SECONDS
        private set

    /** Shots added to the bat's fan. Zero is the single straight shot it starts with. */
    var extraShots: Int = 0
        private set

    /** What one of the bat's shots takes off what it hits. */
    var shotDamage: Int = DAMAGE_PER_HIT
        private set

    /** How often one of the bat's shots leaves the gun critical, as 0..1. */
    var criticalChance: Float = CRITICAL_CHANCE
        private set

    /** What a critical is worth against an ordinary shot; see [criticalDamage]. */
    var criticalMultiplier: Float = CRITICAL_DAMAGE_MULTIPLIER
        private set

    /**
     * What one of the bat's *critical* shots takes off what it hits - a number of its own, read
     * alongside [shotDamage] rather than folded into it.
     *
     * Derived rather than stored, so it cannot drift out of step with the shot damage a run has
     * earned: Heavy Rounds raises [shotDamage], and a crit is the same gun landing well, so it has
     * to follow. Rounded because damage is whole points everywhere else in the game.
     */
    val criticalDamage: Int
        get() = (shotDamage * criticalMultiplier).roundToInt()

    /** The size of the bat's health bar. Raising it heals by the same amount; see [gainMaxHealth]. */
    var maxHitPoints: Int = PLAYER_MAX_HIT_POINTS
        private set

    /** Mercy invulnerability after a hit, in seconds. */
    var hitCooldownSeconds: Float = PLAYER_HIT_COOLDOWN_SECONDS
        private set

    /** Incoming damage is multiplied by this. 1 is unarmored; floored so it never reaches zero. */
    var damageTaken: Float = 1f
        private set

    /** Health handed out by the last [gainMaxHealth], for the screen to apply to the bat. */
    var pendingHeal: Int = 0
        private set

    /** Health the bat gets back every second, whatever else is happening. */
    var healthRegenPerSecond: Float = 0f
        private set

    /** Experience earned is multiplied by this. */
    var experienceMultiplier: Float = 1f
        private set

    /** Points scored are multiplied by this. */
    var scoreMultiplier: Float = 1f
        private set

    /** Deaths the bat can walk away from, each at a fraction of its bar; the screen owns how much. */
    var revives: Int = 0
        private set

    /** Taken off every hit before [damageTaken] scales what is left. */
    var flatDamageReduction: Int = 0
        private set

    /** Enemies one of the bat's shots can pass through before being spent. */
    var shotPierce: Int = 0
        private set

    /** Times one of the bat's shots is reflected off the frame instead of leaving it. */
    var shotBounce: Int = 0
        private set

    /** How many orbs circle the bat; see [at.smiech.cyanbat.ecs.OrbitSystem]. */
    var orbs: Int = 0
        private set

    /** How charged the bat's wake is: zero for the plain wake that hurts nothing. */
    var wakeLevel: Int = 0
        private set

    /** How far the frost beam has been built up: zero for no beam. */
    var frostLevel: Int = 0
        private set

    /**
     * What an orb takes off whatever it hits: a share of [shotDamage], derived rather than stored
     * for the reason [criticalDamage] is, and rounded up so it is never nothing.
     */
    val orbDamage: Int
        get() = ceil(shotDamage * ORB_DAMAGE_FRACTION).toInt()

    /** What the wake's shock takes off whatever it touches, a share of [shotDamage]; zero uncharged. */
    val wakeDamage: Int
        get() = ceil(shotDamage * WAKE_DAMAGE_FRACTION[wakeLevel]).toInt()

    /** How long a segment of the wake lasts, and so how far behind the bat it reaches. */
    val wakeSeconds: Float
        get() = WAKE_SECONDS[wakeLevel]

    /**
     * Seconds between two frost beams, each pick after the first a [FROST_BEAM_INTERVAL_FACTOR] of
     * the last. What the first pick fires at until it has been made, which is all it means then.
     */
    val frostIntervalSeconds: Float
        get() = FROST_BEAM_INTERVAL_SECONDS * FROST_BEAM_INTERVAL_FACTOR.pow(frostPicksPast)

    /** How long the frost beam keeps what it catches frozen; as [frostIntervalSeconds], before then. */
    val frostSeconds: Float
        get() = FROST_SECONDS + frostPicksPast * FROST_SECONDS_PER_LEVEL

    /** Frost Beam picks after the one that made the beam. */
    private val frostPicksPast: Int
        get() = (frostLevel - 1).coerceAtLeast(0)

    fun quickenShots(factor: Float) {
        shotIntervalSeconds =
            (shotIntervalSeconds * factor).coerceAtLeast(MIN_SHOT_INTERVAL_SECONDS)
    }

    fun addShot() {
        extraShots = (extraShots + 1).coerceAtMost(MAX_EXTRA_SHOTS)
    }

    fun addShotDamage(amount: Int) {
        shotDamage += amount
    }

    /** Raises how often a shot leaves the gun critical, up to [MAX_CRITICAL_CHANCE]. */
    fun addCriticalChance(fraction: Float) {
        criticalChance = (criticalChance + fraction).coerceAtMost(MAX_CRITICAL_CHANCE)
    }

    /**
     * Widens the health bar and fills the new room with health.
     *
     * A bigger bar that arrived empty would be worth nothing at the moment it is picked, which is
     * exactly the moment the player is deciding whether it is worth picking.
     */
    fun gainMaxHealth(amount: Int) {
        maxHitPoints += amount
        pendingHeal += amount
    }

    fun lengthenHitCooldown(amount: Float) {
        hitCooldownSeconds = (hitCooldownSeconds + amount).coerceAtMost(MAX_HIT_COOLDOWN_SECONDS)
    }

    fun reduceDamageTaken(factor: Float) {
        damageTaken = (damageTaken * factor).coerceAtLeast(ARMOR_FLOOR)
    }

    fun addHealthRegen(perSecond: Float) {
        healthRegenPerSecond =
            (healthRegenPerSecond + perSecond).coerceAtMost(MAX_HEALTH_REGEN_PER_SECOND)
    }

    fun addExperienceBonus(fraction: Float) {
        experienceMultiplier += fraction
    }

    fun addScoreBonus(fraction: Float) {
        scoreMultiplier += fraction
    }

    fun addRevive() {
        revives = (revives + 1).coerceAtMost(MAX_REVIVES)
    }

    /**
     * Trades a flat cut of incoming damage for a share more outgoing.
     *
     * One power-up rather than two, and stacked together, because the pair is the point: it is the
     * pick for a player who means to stand and trade rather than dodge.
     */
    fun counterweight(damageReduction: Int, extraDamageFraction: Float) {
        flatDamageReduction =
            (flatDamageReduction + damageReduction).coerceAtMost(MAX_FLAT_DAMAGE_REDUCTION)
        // Rounded up, so the smallest raise is still worth a point of damage rather than nothing.
        shotDamage += ceil(shotDamage * extraDamageFraction).toInt()
    }

    fun addPierce() {
        shotPierce = (shotPierce + 1).coerceAtMost(MAX_SHOT_PIERCE)
    }

    fun addBounce() {
        shotBounce = (shotBounce + 1).coerceAtMost(MAX_SHOT_BOUNCE)
    }

    fun addOrb() {
        orbs = (orbs + 1).coerceAtMost(MAX_ORBS)
    }

    fun chargeWake() {
        wakeLevel = (wakeLevel + 1).coerceAtMost(MAX_WAKE_LEVEL)
    }

    fun buildFrostBeam() {
        frostLevel = (frostLevel + 1).coerceAtMost(MAX_FROST_LEVEL)
    }

    /** Spends one revive, if there is one to spend. */
    fun useRevive(): Boolean {
        if (revives <= 0) return false
        revives--
        return true
    }

    /** Consumed by the screen once the healing has been applied to the bat. */
    fun takePendingHeal(): Int = pendingHeal.also { pendingHeal = 0 }

    // --- what is still worth offering ---------------------------------------------------------

    val canQuickenShots: Boolean get() = shotIntervalSeconds > MIN_SHOT_INTERVAL_SECONDS
    val canAddShot: Boolean get() = extraShots < MAX_EXTRA_SHOTS
    val canAddCriticalChance: Boolean get() = criticalChance < MAX_CRITICAL_CHANCE
    val canLengthenHitCooldown: Boolean get() = hitCooldownSeconds < MAX_HIT_COOLDOWN_SECONDS
    val canReduceDamageTaken: Boolean get() = damageTaken > ARMOR_FLOOR
    val canAddHealthRegen: Boolean get() = healthRegenPerSecond < MAX_HEALTH_REGEN_PER_SECOND
    val canAddRevive: Boolean get() = revives < MAX_REVIVES
    val canCounterweight: Boolean get() = flatDamageReduction < MAX_FLAT_DAMAGE_REDUCTION
    val canAddPierce: Boolean get() = shotPierce < MAX_SHOT_PIERCE
    val canAddBounce: Boolean get() = shotBounce < MAX_SHOT_BOUNCE
    val canAddOrb: Boolean get() = orbs < MAX_ORBS
    val canChargeWake: Boolean get() = wakeLevel < MAX_WAKE_LEVEL
    val canBuildFrostBeam: Boolean get() = frostLevel < MAX_FROST_LEVEL
}
