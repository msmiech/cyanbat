package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.Component

/**
 * The colorway of `shot.png` a shot is drawn in, carried by the shot itself: what it leaves where it
 * is spent - its hit, in `impact.png` - comes out in the same colors, and so does the hit's light.
 *
 * Copied off the shooter's [at.smiech.engine.ecs.ProjectileStyleComponent] as the shot is made, for
 * the reason that one is read then: by the time a shot lands, whatever fired it may be long gone and
 * its id handed to something else.
 */
class ColorwayComponent(val variant: Int) : Component
