package at.smiech.cyanbat.resource

import at.smiech.cyanbat.scenery.Daylight
import at.smiech.engine.Music
import at.smiech.engine.Pixmap

/**
 * Everything a stage looks and sounds like. What it *plays* like - its waves and its boss - is
 * [at.smiech.cyanbat.service.StageDesign], looked up by the same [id].
 *
 * @param id 1-based, and the order stages unlock in.
 * @param topObstacles what hangs into the frame from above. Empty for a stage with an open sky,
 *   where only the ground has anything standing on it.
 * @param enemySheet the sheet every enemy of this stage is drawn from.
 * @param bossSheet the boss's own sheet, for a boss drawn at its own size rather than magnified
 *   off [enemySheet].
 */
data class Stage(
    val id: Int,
    val name: String,
    val backdrop: Backdrop,
    val topObstacles: Array<Pixmap?>,
    val bottomObstacles: Array<Pixmap?>,
    val music: Music,
    val enemySheet: Pixmap,
    val bossSheet: Pixmap? = null,
) {
    /**
     * How many times of day each obstacle is drawn in, one above the other on its sheet. A stage
     * whose light changes has to change its scenery's light with it: a rock still lit by noon sun
     * under a night sky reads as a sticker on the picture.
     */
    val obstacleKeyframes: Int
        get() = if (backdrop is Backdrop.Nightfall) Daylight.KEYFRAMES.size else 1
}
