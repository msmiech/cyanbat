package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.Component

/**
 * The bat's weapons that hurt by touch and are never spent by it, for the run to know what each one
 * deals and how soon it may land on the same thing again.
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
 * Kept on what is hit rather than on what hits it, for two reasons. It goes with the target, so it is
 * never read back through an id that has been recycled to something else since. And a weapon made of
 * many parts - a ring of orbs, a wake of dozens of segments - lands on a target as one weapon, rather
 * than once for every part of it the target happens to be touching.
 */
class ContactCooldownComponent : Component {
    private val readyAt = FloatArray(ContactWeapon.entries.size)

    /**
     * Whether [weapon] may land at [now] - and if it may, holds it off for [seconds] from there. One
     * call, as [at.smiech.engine.ecs.PierceComponent.meet] is one call: a check that did not take the
     * hit would let the next tick's overlap land it again.
     */
    fun take(weapon: ContactWeapon, now: Float, seconds: Float): Boolean {
        if (now < readyAt[weapon.ordinal]) return false
        readyAt[weapon.ordinal] = now + seconds
        return true
    }
}
