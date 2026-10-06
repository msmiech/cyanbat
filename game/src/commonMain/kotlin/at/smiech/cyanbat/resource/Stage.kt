package at.smiech.cyanbat.resource

import at.smiech.engine.Pixmap
import org.jetbrains.compose.resources.StringResource

/**
 * How a stage looks and sounds. How it plays (its waves and boss) is
 * [at.smiech.cyanbat.service.StageDesign], looked up by the same [id].
 *
 * @param id 1-based, and the order stages unlock in.
 * @param name what the stage is called, in the run and on the stage select.
 * @param backdrop what the stage is flown in front of.
 * @param topObstacles what hangs into the frame from above. Empty for a stage with an open sky.
 * @param bottomObstacles what stands on the ground.
 * @param music what the stage plays, as layers the run turns up and down; see [StageMusic].
 * @param enemySheet the sheet every enemy of this stage is drawn from.
 * @param bossSheet the boss's own sheet, for a boss drawn at its own size rather than magnified
 *   off [enemySheet].
 * @param lighting how dark the stage is flown, for one flown in the dark; null for daylight.
 * @param approach what the stage's obstacles turn into on the way to its boss; null for a stage whose
 *   scenery is the same from start to finish.
 */
data class Stage(
    val id: Int,
    val name: StringResource,
    val backdrop: Backdrop,
    val topObstacles: Array<Pixmap?>,
    val bottomObstacles: Array<Pixmap?>,
    val music: StageMusic,
    val enemySheet: Pixmap,
    val bossSheet: Pixmap? = null,
    val lighting: StageLighting? = null,
    val approach: Approach? = null,
) {
    /**
     * How many times of day each obstacle is drawn in, stacked on its sheet, so a stage whose light
     * changes can relight its scenery to match.
     */
    val obstacleKeyframes: Int
        get() = (backdrop as? Backdrop.Sky)?.day?.keyframes?.size ?: 1
}

/**
 * What a stage's ground turns into on the way to its boss: from [from] of the way through its day,
 * a growing share of its ground obstacles is drawn from [bottomObstacles] instead, reaching all of
 * them by [until]. The backdrop changes over the same stretch; see [ParallaxLayer.ahead].
 *
 * The replacements keep the originals' footprints one for one, so the scenery
 * changes and the flying does not.
 */
class Approach(val from: Float, val until: Float, val bottomObstacles: Array<Pixmap?>) {
    /** How much of the ground has turned by [position], as 0..1. */
    fun share(position: Float): Float =
        if (until <= from) (if (position >= from) 1f else 0f) else ((position - from) / (until - from)).coerceIn(
            0f,
            1f
        )
}

/**
 * The dark a stage is flown in: the [ambient] light where no other reaches, and how
 * much light shows in the air ([glow]); see `LightingSystem`. What gives off light
 * is set on the entities themselves.
 */
class StageLighting(val ambient: Int, val glow: Float)
