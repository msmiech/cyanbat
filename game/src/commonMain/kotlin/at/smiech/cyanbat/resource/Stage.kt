package at.smiech.cyanbat.resource

import at.smiech.engine.Music
import at.smiech.engine.Pixmap

/**
 * Everything a stage looks and sounds like. What it *plays* like - its waves and its boss - is
 * [at.smiech.cyanbat.service.StageDesign], looked up by the same [id].
 *
 * @param id 1-based, and the order stages unlock in.
 * @param enemySheet the sheet every enemy of this stage is drawn from.
 * @param bossSheet the boss's own sheet, for a boss drawn at its own size rather than magnified
 *   off [enemySheet].
 */
data class Stage(
    val id: Int,
    val name: String,
    val background: Pixmap,
    val topObstacles: Array<Pixmap?>,
    val bottomObstacles: Array<Pixmap?>,
    val music: Music,
    val enemySheet: Pixmap,
    val bossSheet: Pixmap? = null,
)
