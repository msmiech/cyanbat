package at.smiech.cyanbat.resource

import at.smiech.engine.Music
import at.smiech.engine.Pixmap
import at.smiech.engine.Sound

data class GameAssets(
    var graphics: Graphics,
    var audio: Audio,
    var levels: List<Level> = emptyList()
) {
    data class Graphics(
        var bat: Pixmap,
        var death: Pixmap,
        var gameOver: Pixmap,
        var explosion: Pixmap,
        var shot: Pixmap,
        var enemy: Pixmap
    )

    data class Audio(
        var gameOverMusic: Music,
        var deathSound: Sound
    )
}
