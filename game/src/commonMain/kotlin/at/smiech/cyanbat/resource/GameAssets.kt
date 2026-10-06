package at.smiech.cyanbat.resource

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.stage_1_name
import at.smiech.cyanbat.resources.stage_2_name
import at.smiech.cyanbat.resources.stage_3_name
import at.smiech.cyanbat.resources.stage_4_name
import at.smiech.cyanbat.scenery.Daybreak
import at.smiech.cyanbat.scenery.Daylight
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
        /** A shot's hit, played where it strikes: in the colorways of [shot], in the same order. */
        var impact: Pixmap,
        /** The orb Guardian Orb sends round the bat: six frames of a glint going round it. */
        var orb: Pixmap,
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
                    impact = pixmap("impact.png"),
                    orb = pixmap("orb.png"),
                ),
                audio = Audio(
                    // Generated like the stages' music, by tools/generate_game_over_music.py.
                    gameOverMusic = a.newMusic("music/game_over.wav"),
                    effects = SoundEffect.entries.associateWith { a.newSound(it.file) },
                ),
                stages = listOf(
                    Stage(
                        id = 1,
                        name = Res.string.stage_1_name,
                        backdrop = Backdrop.Strip(pixmap("jungleBackground.png")),
                        topObstacles = arrayOf(
                            pixmap("jungleTopObstacle1.png"),
                            pixmap("jungleTopObstacle2.png"),
                        ),
                        bottomObstacles = arrayOf(
                            pixmap("jungleBottomObstacle1.png"),
                            pixmap("jungleBottomObstacle2.png"),
                        ),
                        music = StageMusic(
                            "jungle",
                            MusicGrid(beatsPerMinute = 98.0, beatsPerBar = 4)
                        ),
                        enemySheet = pixmap("jungleEnemies.png"),
                        bossSheet = pixmap("jungleBoss.png"),
                    ),
                    Stage(
                        id = 2,
                        name = Res.string.stage_2_name,
                        backdrop = Backdrop.Strip(pixmap("background.png")),
                        topObstacles = arrayOf(
                            pixmap("topObstacle1.png"),
                            pixmap("topObstacle2.png")
                        ),
                        bottomObstacles = arrayOf(
                            pixmap("bottomObstacle1.png"),
                            pixmap("bottomObstacle2.png"),
                        ),
                        // In 12/8: four beats a bar, each a dotted quarter of three rolling eighths.
                        music = StageMusic(
                            "cave",
                            MusicGrid(beatsPerMinute = 63.0, beatsPerBar = 4)
                        ),
                        enemySheet = pixmap("enemies.png"),
                        // Underground, and so the one stage flown in the dark, by the bat's own light.
                        lighting = StageLighting(ambient = CAVE_AMBIENT, glow = CAVE_GLOW),
                    ),
                    Stage(
                        id = 3,
                        name = Res.string.stage_3_name,
                        backdrop = Backdrop.Sky(
                            day = Daylight,
                            // The far band barely moves and the near one moves with the rocks
                            // standing on it, so the three read as three distances.
                            layers = listOf(
                                ParallaxLayer(pixmap("desertFar.png"), top = 192, speed = 0.2f),
                                ParallaxLayer(pixmap("desertMid.png"), top = 254, speed = 0.5f),
                                ParallaxLayer(pixmap("desertNear.png"), top = 304, speed = 1f),
                            ),
                            moon = pixmap("desertMoon.png"),
                            // Level with the far dunes, where the ground takes over from the sky.
                            horizonY = 276,
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
                        music = StageMusic(
                            "desert",
                            MusicGrid(beatsPerMinute = 105.0, beatsPerBar = 4)
                        ),
                        enemySheet = pixmap("desertEnemies.png"),
                        bossSheet = pixmap("desertBoss.png"),
                    ),
                    Stage(
                        id = 4,
                        name = Res.string.stage_4_name,
                        backdrop = Backdrop.Sky(
                            day = Daybreak,
                            // Clouds high up and barely moving; the open sea, from the horizon to the
                            // bottom of the frame; limestone islands far off on the horizon, then
                            // nearer ones; and the water the obstacles stand in. The far islands give
                            // way to a temple on the horizon on the way to the Naga, then the nearer
                            // islands to its ruins, and last the water to its moat.
                            layers = listOf(
                                ParallaxLayer(pixmap("lagoonClouds.png"), top = 12, speed = 0.12f),
                                ParallaxLayer(pixmap("lagoonSea.png"), top = 232, speed = 0.45f),
                                ParallaxLayer(
                                    pixmap("lagoonFar.png"), top = 164, speed = 0.2f,
                                    ahead = pixmap("lagoonFarTemple.png"), aheadFrom = 0.5f,
                                    // The sun's path lies on the open sea, under every island nearer than these.
                                    water = 235..302,
                                ),
                                ParallaxLayer(
                                    pixmap("lagoonMid.png"), top = 186, speed = 0.5f,
                                    ahead = pixmap("lagoonMidTemple.png"), aheadFrom = 0.68f,
                                ),
                                ParallaxLayer(
                                    pixmap("lagoonNear.png"), top = 302, speed = 1f,
                                    ahead = pixmap("lagoonNearTemple.png"), aheadFrom = 0.8f,
                                ),
                            ),
                            moon = pixmap("lagoonMoon.png"),
                            // The sea's horizon, where the far islands stand.
                            horizonY = 232,
                        ),
                        // An open sky, as over the desert: the limestone stands up out of the water.
                        topObstacles = emptyArray(),
                        bottomObstacles = arrayOf(
                            pixmap("lagoonObstacle1.png"),
                            pixmap("lagoonObstacle2.png"),
                            pixmap("lagoonObstacle3.png"),
                            pixmap("lagoonObstacle4.png"),
                        ),
                        // The temple's stones take over from the limestone as its towers come into sight.
                        approach = Approach(
                            from = 0.62f,
                            until = 0.9f,
                            bottomObstacles = arrayOf(
                                pixmap("templeObstacle1.png"),
                                pixmap("templeObstacle2.png"),
                                pixmap("templeObstacle3.png"),
                                pixmap("templeObstacle4.png"),
                            ),
                        ),
                        // The flight to the temple, and then the Naga to a piece of its own, faster
                        // and wilder, which the run hands over to as it rises.
                        music = StageMusic(
                            "lagoon",
                            MusicGrid(beatsPerMinute = 135.0, beatsPerBar = 4),
                            boss = StageMusic(
                                "naga",
                                MusicGrid(beatsPerMinute = 150.0, beatsPerBar = 4)
                            ),
                        ),
                        enemySheet = pixmap("lagoonEnemies.png"),
                        bossSheet = pixmap("lagoonBoss.png"),
                    ),
                ),
            )
        }
    }
}
