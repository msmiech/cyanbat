package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.ARMOUR_FLOOR
import at.smiech.cyanbat.util.MAX_FLAT_DAMAGE_REDUCTION
import at.smiech.cyanbat.util.MAX_HEALTH_REGEN_PER_SECOND
import at.smiech.cyanbat.util.MAX_REVIVES
import at.smiech.cyanbat.util.MAX_SHOT_BOUNCE
import at.smiech.cyanbat.util.MAX_SHOT_PIERCE
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.MAX_EXTRA_SHOTS
import at.smiech.cyanbat.util.MAX_HIT_COOLDOWN_SECONDS
import at.smiech.cyanbat.util.MIN_SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.PLAYER_HIT_COOLDOWN_SECONDS
import at.smiech.cyanbat.util.PLAYER_MAX_HIT_POINTS
import at.smiech.cyanbat.util.SHOT_INTERVAL_SECONDS
import kotlin.math.ceil

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

    /** Shots fanned out either side of the straight one. Zero is the single shot it starts with. */
    var extraShots: Int = 0
        private set

    /** What one of the bat's shots takes off what it hits. */
    var shotDamage: Int = DAMAGE_PER_HIT
        private set

    /** The size of the bat's health bar. Raising it heals by the same amount; see [gainMaxHealth]. */
    var maxHitPoints: Int = PLAYER_MAX_HIT_POINTS
        private set

    /** Mercy invulnerability after a hit, in seconds. */
    var hitCooldownSeconds: Float = PLAYER_HIT_COOLDOWN_SECONDS
        private set

    /** Incoming damage is multiplied by this. 1 is unarmoured; floored so it never reaches zero. */
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

    fun quickenShots(factor: Float) {
        shotIntervalSeconds = (shotIntervalSeconds * factor).coerceAtLeast(MIN_SHOT_INTERVAL_SECONDS)
    }

    fun addShot() {
        extraShots = (extraShots + 1).coerceAtMost(MAX_EXTRA_SHOTS)
    }

    fun addShotDamage(amount: Int) {
        shotDamage += amount
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
        damageTaken = (damageTaken * factor).coerceAtLeast(ARMOUR_FLOOR)
    }

    fun addHealthRegen(perSecond: Float) {
        healthRegenPerSecond = (healthRegenPerSecond + perSecond).coerceAtMost(MAX_HEALTH_REGEN_PER_SECOND)
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
        flatDamageReduction = (flatDamageReduction + damageReduction).coerceAtMost(MAX_FLAT_DAMAGE_REDUCTION)
        // Rounded up, so the smallest raise is still worth a point of damage rather than nothing.
        shotDamage += ceil(shotDamage * extraDamageFraction).toInt()
    }

    fun addPierce() {
        shotPierce = (shotPierce + 1).coerceAtMost(MAX_SHOT_PIERCE)
    }

    fun addBounce() {
        shotBounce = (shotBounce + 1).coerceAtMost(MAX_SHOT_BOUNCE)
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
    val canLengthenHitCooldown: Boolean get() = hitCooldownSeconds < MAX_HIT_COOLDOWN_SECONDS
    val canReduceDamageTaken: Boolean get() = damageTaken > ARMOUR_FLOOR
    val canAddHealthRegen: Boolean get() = healthRegenPerSecond < MAX_HEALTH_REGEN_PER_SECOND
    val canAddRevive: Boolean get() = revives < MAX_REVIVES
    val canCounterweight: Boolean get() = flatDamageReduction < MAX_FLAT_DAMAGE_REDUCTION
    val canAddPierce: Boolean get() = shotPierce < MAX_SHOT_PIERCE
    val canAddBounce: Boolean get() = shotBounce < MAX_SHOT_BOUNCE
}
