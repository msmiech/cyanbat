package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.AuraColors
import at.smiech.engine.ecs.Component

/**
 * The colors an elite can come in, one picked at random each: its glow and its bolts share a hue,
 * so an elite's fire shows which enemy it came from.
 *
 * None is a color the bat wears (its cyan, or its aura's gold), and they are spread round the color
 * wheel so two elites on screen are told apart at a glance.
 *
 * Each core is its hue lightened, but well short of near white: cores that pale
 * made every elite the same pastel glow.
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
 * Marks an enemy as an elite wearing [palette]. Its health, faster gun, aura and
 * shot colorway are set on the components it spawns with; the run reads this when
 * it dies to pay out an elite's worth.
 */
class EliteComponent(val palette: ElitePalette) : Component
