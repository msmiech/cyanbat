package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.ARMOUR_FLOOR
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.MAX_EXTRA_SHOTS
import at.smiech.cyanbat.util.MAX_HIT_COOLDOWN_SECONDS
import at.smiech.cyanbat.util.MIN_SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.PLAYER_HIT_COOLDOWN_SECONDS
import at.smiech.cyanbat.util.PLAYER_MAX_HIT_POINTS
import at.smiech.cyanbat.util.SHOT_INTERVAL_SECONDS

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

    /** Consumed by the screen once the healing has been applied to the bat. */
    fun takePendingHeal(): Int = pendingHeal.also { pendingHeal = 0 }

    // --- what is still worth offering ---------------------------------------------------------

    val canQuickenShots: Boolean get() = shotIntervalSeconds > MIN_SHOT_INTERVAL_SECONDS
    val canAddShot: Boolean get() = extraShots < MAX_EXTRA_SHOTS
    val canLengthenHitCooldown: Boolean get() = hitCooldownSeconds < MAX_HIT_COOLDOWN_SECONDS
    val canReduceDamageTaken: Boolean get() = damageTaken > ARMOUR_FLOOR
}
