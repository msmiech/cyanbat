package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.Component

/**
 * The colorway of `shot.png` a shot is drawn in, carried by the shot so its hit (`impact.png`) and
 * the hit's light come out in the same colors.
 *
 * Copied from the shooter's [at.smiech.engine.ecs.ProjectileStyleComponent] when the shot is made,
 * since by the time it lands the shooter may be gone and its id reused.
 */
class ColorwayComponent(val variant: Int) : Component
