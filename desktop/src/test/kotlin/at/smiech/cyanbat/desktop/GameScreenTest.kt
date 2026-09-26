package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.desktop.recorder.RunProbe
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.service.StageProgression
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.engine.GameLoop
import at.smiech.engine.Haptics
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.TransformComponent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [GameScreen] itself, flown headless on the desktop's own host.
 *
 * The screen needs a live Game and a real asset set, which is why the rest of the game's logic is
 * tested apart from it. The recorder already hosts it this way, and its [RunProbe] is how these
 * tests read the parts of the run the screen keeps to itself.
 */
class GameScreenTest {

    /**
     * The boss goes down partway through a tick's collision pass, and the score moves on after
     * that: the kill itself is counted once the pass has hurt both sides, and the tick still pays
     * for being survived. A record banked as the boss died came out below the score the victory
     * overlay shows it beside.
     */
    @Test
    fun `a stage won on a record banks the score the overlay shows`() {
        val game = DesktopGame(480, 320)
        val assets = GameAssets.load(game.graphics, game.audio)
        val highscores = RecordingHighscores()
        val screen = GameScreen(
            game,
            CyanBatEnvironment(
                assets = assets,
                haptics = Haptics.None,
                highscores = highscores,
                stageUnlocks = StageUnlockStore.InMemory(),
                onExitToMenu = {},
                audioSettings = Silent,
            ),
            CAVE,
        )
        try {
            val probe = RunProbe(screen)
            val world = probe.world

            // Straight to the boss. The stage clock is the generator's, and the boss arrives the
            // moment it reads the boss's minute.
            screen.enmGen.update(StageProgression.forStage(CAVE).bossTimeSeconds)
            val bossId = assertNotNull(screen.enmGen.bossId, "the boss should have arrived")

            // One hit from dead, with one of the bat's shots already inside it, so it goes down in
            // the first collision pass.
            world.getComponent(bossId, HealthComponent::class)!!.hitPoints = 1
            val boss = world.getComponent(bossId, TransformComponent::class)!!.rect
            val shot = assets.graphics.shot
            EntityFactory(world).createShot(
                x = boss.centerX - SHOT_FRAME_WIDTH / 2f,
                y = boss.centerY - shot.height / 2f,
                width = SHOT_FRAME_WIDTH.toFloat(),
                height = shot.height.toFloat(),
                pixmap = shot,
                isPlayer = true,
            )

            // The longest frame the loop hands out, which is two ticks: the one that wins the stage,
            // and one after it that must not move the score on again.
            screen.update(GameLoop.MAX_FRAME_DELTA_SECONDS)

            assertTrue(probe.stageComplete, "the boss should be down")
            assertEquals(probe.score, screen.highscore, "the overlay's highscore against its score")
            assertEquals(probe.score, highscores.saved[CAVE], "the record stored for the stage")
        } finally {
            screen.dispose()
            game.audio.dispose()
        }
    }

    private companion object {
        const val CAVE = 1
    }
}

/** Keeps the highest value banked for each stage, the way every real store does. */
private class RecordingHighscores : HighscoreStore {
    val saved = mutableMapOf<Int, Int>()

    /**
     * Never emits, so the screen's read of the stored record never comes back - as a slow disk's
     * might not before a run is over. The screen makes that read on the Swing thread, and an
     * answer would race this test's thread for the screen's highscore.
     */
    override val byStage: Flow<Map<Int, Int>> = flow { awaitCancellation() }

    override fun saveAsync(stageId: Int, value: Int) {
        saved[stageId] = maxOf(saved[stageId] ?: 0, value)
    }
}

private object Silent : AudioSettings {
    override val musicEnabled = false
    override val soundsEnabled = false
}
