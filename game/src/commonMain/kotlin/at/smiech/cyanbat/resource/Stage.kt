package at.smiech.cyanbat.resource

import at.smiech.engine.Pixmap

/**
 * Everything a stage looks and sounds like. What it *plays* like - its waves and its boss - is
 * [at.smiech.cyanbat.service.StageDesign], looked up by the same [id].
 *
 * @param id 1-based, and the order stages unlock in.
 * @param topObstacles what hangs into the frame from above. Empty for a stage with an open sky,
 *   where only the ground has anything standing on it.
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
    val name: String,
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
     * How many times of day each obstacle is drawn in, one above the other on its sheet. A stage
     * whose light changes has to change its scenery's light with it: a rock still lit by noon sun
     * under a night sky reads as a sticker on the picture.
     */
    val obstacleKeyframes: Int
        get() = (backdrop as? Backdrop.Sky)?.day?.keyframes?.size ?: 1
}

/**
 * What a stage's ground turns into on the way to its boss: from [from] of the way through its day, a
 * share of the obstacles that stand on it are drawn from [bottomObstacles] instead of the stage's own,
 * a share that grows to all of them by [until]. The backdrop changes over the same stretch; see
 * [ParallaxLayer.ahead].
 *
 * The obstacles keep the stage's footprints, one for one, so the scenery changes and the flying does
 * not.
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
 * The dark a stage is flown in: the light where no other reaches, as the color the frame is multiplied
 * by there, and how much of the light shows in the air; see `LightingSystem`. What gives off light in
 * it, and how much, is the entities' own, set where they are made.
 */
class StageLighting(val ambient: Int, val glow: Float)
