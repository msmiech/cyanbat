package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.Component

/** Where the shots of one volley go. */
enum class ShotPattern {
    /** Straight ahead, which for an enemy is to the left - what the cave's boss has always fired. */
    STRAIGHT,

    /** At the player, from wherever the shooter is. */
    AIMED,

    /** [Volley.count] shots fanned around the line to the player, [Volley.spreadDegrees] apart. */
    AIMED_FAN,

    /** [Volley.count] shots evenly round a full circle, turned a little further every volley. */
    RADIAL,
}

/**
 * One pull of an enemy's trigger.
 *
 * @param speed how fast the shots travel, in framebuffer pixels per tick. Enemy fire is kept well
 *   under the bat's own - a bolt the player cannot react to is not a threat, it is a coin toss.
 * @param damageFactor what each bolt deals, as a share of the shooter's own contact damage. A
 *   volley of many bolts needs each one to hurt less, or a single ring would be a death sentence.
 */
data class Volley(
    val pattern: ShotPattern,
    val count: Int = 1,
    val spreadDegrees: Float = 0f,
    val speed: Float,
    val damageFactor: Float = 1f,
)

/**
 * What an enemy's weapon fires, alongside the engine's `WeaponComponent` that decides when.
 *
 * The split is the engine's: `WeaponSystem` owns the cadence and hands every shot to the game,
 * and this is what the game reads to decide what that shot is. An enemy with a `WeaponComponent`
 * and no gun - the cave's boss - fires one straight bolt, as it always has.
 *
 * @param volleys fired in turn, one per trigger pull, so a boss can alternate a fan with a ring.
 *   Mutable because a boss changes its whole repertoire when it changes phase.
 * @param next which volley comes next.
 * @param spin how far the next [ShotPattern.RADIAL] ring is turned, so consecutive rings do not
 *   leave the same safe lanes open.
 */
class GunComponent(
    var volleys: List<Volley>,
    var next: Int = 0,
    var spin: Float = 0f,
) : Component {
    /** The volley to fire now, advancing to the one after it. */
    fun pull(): Volley {
        val volley = volleys[next % volleys.size]
        next = (next + 1) % volleys.size
        return volley
    }
}
