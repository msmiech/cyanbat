package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.desktop.DesktopGame
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.engine.GameLoop
import at.smiech.engine.Haptics
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.impl.ControlHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The recorder's one blind spot, covered: [RunProbe] reads [GameScreen]'s private fields by name,
 * so a rename there compiles cleanly and only breaks the recorder when somebody next runs it. This
 * flies the opening seconds of the cave on the [Autopilot], through the real desktop host, and reads
 * every one of them.
 */
class RunProbeTest {

    @Test
    fun `reads a run the autopilot is flying`() {
        val controls = ControlHandler()
        val game = DesktopGame(480, 320, controls)
        val screen = GameScreen(
            game,
            CyanBatEnvironment(
                assets = GameAssets.load(game.graphics, game.audio),
                haptics = Haptics.None,
                highscores = object : HighscoreStore {
                    override val byStage = MutableStateFlow(emptyMap<Int, Int>())
                    override fun saveAsync(stageId: Int, value: Int) = Unit
                },
                stageUnlocks = StageUnlockStore.InMemory(),
                onExitToMenu = {},
                audioSettings = object : AudioSettings {
                    override val musicEnabled = false
                    override val soundsEnabled = false
                },
            ),
            1,
        )
        try {
            game.setScreen(screen)
            val probe = RunProbe(screen)
            val autopilot = Autopilot(controls, 480, 320)
            val loop = GameLoop(game)
            var clock = STEP_NANOS
            loop.frame(clock)
            repeat(TICKS) {
                autopilot.fly(probe.world, probe.batId)
                clock += STEP_NANOS
                loop.frame(clock)
            }

            assertEquals(true, probe.world.getComponent(probe.batId, HealthComponent::class)?.alive)
            assertTrue(probe.score > 0, "surviving scores from the first tick")
            assertEquals(1, probe.level)
            assertTrue(probe.offer.isEmpty())
            assertFalse(probe.stageComplete)
            // The opening wave is not announced, and nothing else has been yet.
            assertNull(probe.banner)
        } finally {
            screen.dispose()
            game.audio.dispose()
        }
    }

    private companion object {
        /** A tick and a hair, as the recorder steps the loop. */
        const val STEP_NANOS = 19_000_500L

        /** About three seconds: long enough to fly, too short for the first enemy to arrive. */
        const val TICKS = 160
    }
}
