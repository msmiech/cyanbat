package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.AuraColors
import at.smiech.engine.ecs.Component

/**
 * The colors an elite can come out in, one picked at random for each: the glow around it, and the
 * bolts it fires, which are the same hue - so an elite's fire says which of the enemies on screen
 * it came from.
 *
 * None of them is a color the bat wears. Not its cyan, which is how the player finds it on a busy
 * screen, and not the gold of its own aura, which is how they read how far their run has come.
 * Between those, they are spread round the wheel as far apart as they will go, so two elites on
 * screen at once are told apart at a glance.
 *
 * Each core is its hue lightened, but well short of the near white the bat's gold settles into. Over
 * the dark of the cave a pale core is what reads as light, but one that pale in every palette gave
 * every elite the same pastel glow - scarlet came out rose, next to a fuchsia that was rose too - and
 * the hue was left to the faint outer rings to carry.
 *
 * @param shotVariant the colorway of `shot.png` its bolts are drawn in. `generate_shot_sprite.py`
 *   draws each one's body in exactly this palette's [AuraColors.rim], which `SpriteSheetTest` holds
 *   it to.
 */
enum class ElitePalette(val shotVariant: Int, val aura: AuraColors) {
    SCARLET(
        shotVariant = 8,
        aura = AuraColors(
            rim = 0xFFFF3848.toInt(),
            core = 0xFFFF968C.toInt(),
            spark = 0xFFFFC8C2.toInt(),
            bolt = 0xFFFFE4E0.toInt(),
        ),
    ),
    EMBER(
        shotVariant = 9,
        aura = AuraColors(
            rim = 0xFFFF7A1E.toInt(),
            core = 0xFFFFC07A.toInt(),
            spark = 0xFFFFDAB0.toInt(),
            bolt = 0xFFFFF0E0.toInt(),
        ),
    ),
    VENOM(
        shotVariant = 10,
        aura = AuraColors(
            rim = 0xFF74EE3C.toInt(),
            core = 0xFFD2FCBE.toInt(),
            spark = 0xFFDCFFCC.toInt(),
            bolt = 0xFFEEFFE6.toInt(),
        ),
    ),
    ULTRAVIOLET(
        shotVariant = 11,
        aura = AuraColors(
            rim = 0xFFA458FF.toInt(),
            core = 0xFFE2CCFF.toInt(),
            spark = 0xFFE8D8FF.toInt(),
            bolt = 0xFFF4ECFF.toInt(),
        ),
    ),
    FUCHSIA(
        shotVariant = 12,
        aura = AuraColors(
            rim = 0xFFFF40C4.toInt(),
            core = 0xFFFFAAE8.toInt(),
            spark = 0xFFFFD6F2.toInt(),
            bolt = 0xFFFFEAF8.toInt(),
        ),
    ),
}

/**
 * Marks an enemy as an elite, and says which [palette] it wears.
 *
 * Everything else about being one is set on the components it spawns with: the health, the faster
 * gun, the aura and the colorway its shots are drawn in. This is what the run reads when it is shot
 * down, to pay out what an elite is worth.
 */
class EliteComponent(val palette: ElitePalette) : Component
