package at.smiech.cyanbat.resource

import android.os.Vibrator
import at.grueneis.game.framework.Music
import at.grueneis.game.framework.Pixmap
import at.grueneis.game.framework.Sound

data class GameAssets(
    var graphics: Graphics,
    var audio: Audio,
    var vib: Vibrator
) {
    data class Graphics(
        var bat: Pixmap,
        var death: Pixmap,
        var gameOver: Pixmap,
        var background: Pixmap,
        var topObstacles: Array<Pixmap?> = arrayOfNulls(2),
        var bottomObstacles: Array<Pixmap?> = arrayOfNulls(2),
        var explosion: Pixmap,
        var shot: Pixmap,
        var enemy: Pixmap
    )

    data class Audio(
        var gameTrack: Music,
        var deathSound: Sound,
        var gameOverMusic: Music
    )
}
