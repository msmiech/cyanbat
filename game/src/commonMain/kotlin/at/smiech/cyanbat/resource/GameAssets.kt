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
        /** The bat going limp: five frames played once, then held while it tumbles and falls. */
        var batDeath: Pixmap,
        var gameOver: Pixmap,
        var explosion: Pixmap,
        /** Rock coming apart, for an obstacle; the explosion is for things that burn. */
        var shatter: Pixmap,
        var shot: Pixmap,
        var enemy: Pixmap
    )

    data class Audio(
        var gameOverMusic: Music,
        var deathSound: Sound,
        /** The swell the bat's aura lets out each time it crosses a tier; see `AuraComponent`. */
        var auraSurgeSound: Sound,
        /** The bat's gun. Short and quiet by design: it plays on every volley. */
        var shotSound: Sound,
    )
}
