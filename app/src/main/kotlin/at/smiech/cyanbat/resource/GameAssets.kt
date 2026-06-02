package at.smiech.cyanbat.resource

import android.os.Vibrator
import at.grueneis.game.framework.Music
import at.grueneis.game.framework.Pixmap
import at.grueneis.game.framework.Sound

data class GameAssets(
    var graphics: Graphics,
    var audio: Audio,
    var vib: Vibrator,
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
