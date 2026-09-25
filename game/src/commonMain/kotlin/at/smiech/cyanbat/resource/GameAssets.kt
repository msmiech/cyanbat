package at.smiech.cyanbat.resource

import at.smiech.engine.Audio as EngineAudio
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Music
import at.smiech.engine.Pixmap
import at.smiech.engine.Sound
import at.smiech.engine.Graphics as EngineGraphics

data class GameAssets(
    var graphics: Graphics,
    var audio: Audio,
    var stages: List<Stage> = emptyList()
) {
    /** The stage with this id, or the first one for an id nothing is registered under. */
    fun stage(id: Int): Stage = stages.firstOrNull { it.id == id } ?: stages.first()

    /** Whether a stage follows [id], and so whether clearing it has anything to unlock. */
    fun hasStageAfter(id: Int): Boolean = stages.any { it.id == id + 1 }

    data class Graphics(
        var bat: Pixmap,
        /** The bat going limp: five frames played once, then held while it tumbles and falls. */
        var batDeath: Pixmap,
        var gameOver: Pixmap,
        var explosion: Pixmap,
        /** Rock coming apart, for an obstacle; the explosion is for things that burn. */
        var shatter: Pixmap,
        var shot: Pixmap,
    )

    data class Audio(
        var gameOverMusic: Music,
        var deathSound: Sound,
        /** The swell the bat's aura lets out each time it crosses a tier; see `AuraComponent`. */
        var auraSurgeSound: Sound,
        /** The bat's gun. Short and quiet by design: it plays on every volley. */
        var shotSound: Sound,
    )

    companion object {
        /**
         * Loads every asset the game uses, through whichever platform's [EngineGraphics] and
         * [EngineAudio] it is handed.
         *
         * Shared, because the Android activity and the desktop window used to carry a copy each,
         * and a stage added to one of them would have been missing from the other.
         */
        fun load(g: EngineGraphics, a: EngineAudio): GameAssets {
            fun pixmap(name: String) = g.newPixmap(name, PixmapFormat.ARGB8888)

            // One track for both stages: there is only the one game theme, and sharing the object
            // rather than loading it twice keeps a single player to stop and start.
            val theme = a.newMusic("game_theme.mp3")

            return GameAssets(
                graphics = Graphics(
                    bat = pixmap("cyanBat.png"),
                    gameOver = pixmap("gameover.png"),
                    batDeath = pixmap("cyanBatDeath.png"),
                    explosion = pixmap("explosion.png"),
                    shatter = pixmap("shatter.png"),
                    shot = pixmap("shot.png"),
                ),
                audio = Audio(
                    gameOverMusic = a.newMusic("game_over.mp3"),
                    deathSound = a.newSound("deathSound.mp3"),
                    auraSurgeSound = a.newSound("auraSurge.wav"),
                    shotSound = a.newSound("shotFire.wav"),
                ),
                stages = listOf(
                    Stage(
                        id = 1,
                        name = "Stage 1: The Cave",
                        background = pixmap("background.png"),
                        topObstacles = arrayOf(pixmap("topObstacle1.png"), pixmap("topObstacle2.png")),
                        bottomObstacles = arrayOf(
                            pixmap("bottomObstacle1.png"),
                            pixmap("bottomObstacle2.png"),
                        ),
                        music = theme,
                        enemySheet = pixmap("enemies.png"),
                    ),
                    Stage(
                        id = 2,
                        name = "Stage 2: The Forest",
                        background = pixmap("forestBackground.png"),
                        topObstacles = arrayOf(
                            pixmap("forestTopObstacle1.png"),
                            pixmap("forestTopObstacle2.png"),
                        ),
                        bottomObstacles = arrayOf(
                            pixmap("forestBottomObstacle1.png"),
                            pixmap("forestBottomObstacle2.png"),
                        ),
                        music = theme,
                        enemySheet = pixmap("forestEnemies.png"),
                        bossSheet = pixmap("forestBoss.png"),
                    ),
                ),
            )
        }
    }
}
