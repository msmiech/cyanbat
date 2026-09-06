package at.smiech.cyanbat.resource

import at.smiech.engine.Music
import at.smiech.engine.Pixmap

data class Level(
    val id: Int,
    val name: String,
    val background: Pixmap,
    val topObstacles: Array<Pixmap?>,
    val bottomObstacles: Array<Pixmap?>,
    val music: Music
)
