package at.smiech.cyanbat.resource

import at.smiech.cyanbat.util.CAVE_AMBIENT
import at.smiech.cyanbat.util.CAVE_GLOW
import at.smiech.engine.Graphics.PixmapFormat
import at.smiech.engine.Music
import at.smiech.engine.MusicGrid
import at.smiech.engine.Pixmap
import at.smiech.engine.Sound
import at.smiech.engine.Audio as EngineAudio
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
        /** Every [SoundEffect], loaded up front: they play mid-fight, where a load would stall it. */
        var effects: Map<SoundEffect, Sound>,
    )

    companion object {
        /**
         * The fanfare a won stage gets, from tools/generate_victory_music.py.
         *
         * Opened by the run that wins rather than loaded with the rest: it plays once, from the top,
         * and a track that has been played partway carries on from there the next time. So each win
         * gets a fresh one, and the run that opened it disposes of it - which also stops it the
         * moment the player flies on, rather than over the next stage's music.
         */
        const val VICTORY_MUSIC = "music/victory.wav"

        /**
         * Loads every asset the game uses, through whichever platform's [EngineGraphics] and
         * [EngineAudio] it is handed.
         *
         * Shared, because the Android activity and the desktop window used to carry a copy each,
         * and a stage added to one of them would have been missing from the other.
         */
        fun load(g: EngineGraphics, a: EngineAudio): GameAssets {
            fun pixmap(name: String) = g.newPixmap(name, PixmapFormat.ARGB8888)

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
                    // Generated like the stages' music, by tools/generate_game_over_music.py.
                    gameOverMusic = a.newMusic("music/game_over.wav"),
                    effects = SoundEffect.entries.associateWith { a.newSound(it.file) },
                ),
                stages = listOf(
                    Stage(
                        id = 1,
                        name = "Stage 1: The Jungle",
                        backdrop = Backdrop.Strip(pixmap("jungleBackground.png")),
                        topObstacles = arrayOf(
                            pixmap("jungleTopObstacle1.png"),
                            pixmap("jungleTopObstacle2.png"),
                        ),
                        bottomObstacles = arrayOf(
                            pixmap("jungleBottomObstacle1.png"),
                            pixmap("jungleBottomObstacle2.png"),
                        ),
                        music = StageMusic("jungle", MusicGrid(beatsPerMinute = 98.0, beatsPerBar = 4)),
                        enemySheet = pixmap("jungleEnemies.png"),
                        bossSheet = pixmap("jungleBoss.png"),
                    ),
                    Stage(
                        id = 2,
                        name = "Stage 2: The Cave",
                        backdrop = Backdrop.Strip(pixmap("background.png")),
                        topObstacles = arrayOf(pixmap("topObstacle1.png"), pixmap("topObstacle2.png")),
                        bottomObstacles = arrayOf(
                            pixmap("bottomObstacle1.png"),
                            pixmap("bottomObstacle2.png"),
                        ),
                        // In 12/8: four beats a bar, each a dotted quarter of three rolling eighths.
                        music = StageMusic("cave", MusicGrid(beatsPerMinute = 63.0, beatsPerBar = 4)),
                        enemySheet = pixmap("enemies.png"),
                        // Underground, and so the one stage flown in the dark, by the bat's own light.
                        lighting = StageLighting(ambient = CAVE_AMBIENT, glow = CAVE_GLOW),
                    ),
                    Stage(
                        id = 3,
                        name = "Stage 3: The Desert",
                        backdrop = Backdrop.Nightfall(
                            // The far band barely moves and the near one moves with the rocks
                            // standing on it, so the three read as three distances.
                            layers = listOf(
                                ParallaxLayer(pixmap("desertFar.png"), top = 192, speed = 0.2f),
                                ParallaxLayer(pixmap("desertMid.png"), top = 254, speed = 0.5f),
                                ParallaxLayer(pixmap("desertNear.png"), top = 304, speed = 1f),
                            ),
                            moon = pixmap("desertMoon.png"),
                        ),
                        // An open sky: nothing hangs into the desert from above. What comes at the
                        // bat from outside the frame here comes up out of the sand instead.
                        topObstacles = emptyArray(),
                        bottomObstacles = arrayOf(
                            pixmap("desertObstacle1.png"),
                            pixmap("desertObstacle2.png"),
                            pixmap("desertObstacle3.png"),
                            pixmap("desertObstacle4.png"),
                        ),
                        music = StageMusic("desert", MusicGrid(beatsPerMinute = 105.0, beatsPerBar = 4)),
                        enemySheet = pixmap("desertEnemies.png"),
                        bossSheet = pixmap("desertBoss.png"),
                    ),
                ),
            )
        }
    }
}
