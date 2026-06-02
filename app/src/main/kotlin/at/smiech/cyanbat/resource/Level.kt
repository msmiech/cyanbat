package at.smiech.cyanbat.resource

import at.grueneis.game.framework.Music
import at.grueneis.game.framework.Pixmap

data class Level(
    val id: Int,
    val background: Pixmap,
    val topObstacles: Array<Pixmap?>,
    val bottomObstacles: Array<Pixmap?>,
    val music: Music
)
