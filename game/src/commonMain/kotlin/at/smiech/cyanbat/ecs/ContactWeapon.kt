package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.Component

/**
 * The bat's weapons that hurt by touch and are never spent, so the run knows what each deals and
 * how soon it may hit the same target again.
 */
enum class ContactWeapon {
    /** One of the orbs circling the bat; see [OrbitSystem]. */
    ORB,

    /** A segment of the bat's wake, once Charged Trail has made it hurt. */
    WAKE,
}

/**
 * Marks one part of a [ContactWeapon]: an orb, or one segment of a charged wake. It collides as
 * [at.smiech.engine.ecs.CollisionGroup.PLAYER_CONTACT], which meets enemies and nothing else.
 */
class ContactWeaponComponent(val weapon: ContactWeapon) : Component

/**
 * When each [ContactWeapon] may next land on whatever carries this, on the run's own clock.
 *
 * Kept on the target rather than the weapon, so it is never read through a recycled id, and so a
 * weapon of many parts (a ring of orbs, a wake of dozens of segments) lands as one weapon rather
 * than once per part touching the target.
 */
class ContactCooldownComponent : Component {
    private val readyAt = FloatArray(ContactWeapon.entries.size)

    /**
     * Whether [weapon] may land at [now], and if so, holds it off for [seconds]. One call, like
     * [at.smiech.engine.ecs.PierceComponent.meet]: a check that did not record the hit would let
     * the next tick's overlap land it again.
     */
    fun take(weapon: ContactWeapon, now: Float, seconds: Float): Boolean {
        if (now < readyAt[weapon.ordinal]) return false
        readyAt[weapon.ordinal] = now + seconds
        return true
    }
}
