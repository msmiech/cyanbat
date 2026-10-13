package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.desktop.recorder.RunProbe
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.GameText
import at.smiech.cyanbat.service.BossKind
import at.smiech.cyanbat.service.EndlessCourse
import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.service.SkyViperBrain
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.cyanbat.util.ENDLESS_ROUND_BREAK_SECONDS
import at.smiech.cyanbat.util.ENDLESS_ROUND_WAVES
import at.smiech.cyanbat.util.ENDLESS_STAGE_ID
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.cyanbat.util.STAGE_COMPLETE_BONUS
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.Haptics
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.math.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The endless stage, flown headless on the desktop's own host. */
class EndlessRunTest {

    private object Silent : AudioSettings {
        override val musicEnabled = false
        override val soundsEnabled = false
    }

    private object Unsaved : HighscoreStore {
        override val byStage = MutableStateFlow(emptyMap<Int, Int>())
        override fun saveAsync(stageId: Int, value: Int) = Unit
    }

    /** A seed whose first round ends on [boss]. */
    private fun seedFor(boss: BossKind): Int =
        (0 until 500).first { EndlessCourse(Random(it)).next().design.boss == boss }

    private class Run(val screen: GameScreen, val probe: RunProbe, val assets: GameAssets)

    private fun endless(boss: BossKind, test: Run.() -> Unit) {
        val game = DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT)
        val assets = GameAssets.load(game.graphics, game.audio)
        val screen = GameScreen(
            game,
            CyanBatEnvironment(
                assets = assets,
                text = runBlocking { GameText.load() },
                haptics = Haptics.None,
                highscores = Unsaved,
                stageUnlocks = StageUnlockStore.InMemory(),
                onExitToMenu = {},
                audioSettings = Silent,
            ),
            ENDLESS_STAGE_ID,
            endlessSeed = seedFor(boss),
        )
        try {
            Run(screen, RunProbe(screen), assets).test()
        } finally {
            screen.dispose()
            game.audio.dispose()
        }
    }

    /**
     * Steps the run on through [seconds] of ticks, waving away any level up on offer, since the
     * dialog holds the run - its breather between rounds included - until something is picked.
     */
    private fun Run.fly(seconds: Float) {
        repeat((seconds / TICK_INITIAL).toInt()) {
            if (probe.offer.isNotEmpty()) probe.offer = emptyList()
            screen.update(TICK_INITIAL)
        }
    }

    /** Keeps the bat alive whatever comes at it, so a test can fly as long as it likes. */
    private fun Run.sturdy() {
        probe.world.getComponent(probe.batId, HealthComponent::class)!!.apply {
            maxHitPoints = 1_000_000
            hitPoints = 1_000_000
        }
    }

    /** Brings the round's boss in, on screen, one hit from dead, and shoots it down. */
    private fun Run.downTheBoss() {
        val generator = screen.enmGen
        generator.update(generator.progression.bossTimeSeconds - generator.roundSeconds)
        val boss = assertNotNull(generator.bossId, "the boss should have arrived")
        val world = probe.world
        val transform = world.getComponent(boss, TransformComponent::class)!!
        transform.rect = Rect.fromLTWH(300f, 100f, transform.rect.width, transform.rect.height)
        world.getComponent(boss, HealthComponent::class)!!.hitPoints = 1
        val shot = assets.graphics.shot
        EntityFactory(world).createShot(
            x = transform.rect.centerX - SHOT_FRAME_WIDTH / 2f,
            y = transform.rect.centerY - shot.height / 2f,
            width = SHOT_FRAME_WIDTH.toFloat(),
            height = shot.height.toFloat(),
            pixmap = shot,
            isPlayer = true,
        )
        screen.update(TICK_INITIAL * 1.5f)
    }

    @Test
    fun `the endless stage counts its waves on with no last one`() = endless(BossKind.CACO_IMP) {
        sturdy()
        assertEquals(0, screen.enmGen.currentWave.index)
        fly(61f)
        assertEquals(1, screen.enmGen.currentWave.index)
        assertEquals(ENDLESS_ROUND_WAVES, screen.enmGen.progression.bossWave)
    }

    @Test
    fun `a round's boss going down is banked and the next round follows its breather`() = endless(BossKind.CACO_IMP) {
        sturdy()
        val before = probe.score
        downTheBoss()

        assertFalse(probe.stageComplete, "the endless stage was won")
        assertTrue(probe.score - before >= STAGE_COMPLETE_BONUS, "the boss was not banked")
        assertEquals("THE CACO IMP FALLS", probe.banner)
        assertTrue(screen.enmGen.bossSpawned)

        fly(ENDLESS_ROUND_BREAK_SECONDS + 0.5f)

        val generator = screen.enmGen
        assertFalse(generator.bossSpawned, "the next round did not begin")
        assertEquals(ENDLESS_ROUND_WAVES, generator.progression.wavesBefore)
        assertEquals(ENDLESS_ROUND_WAVES, generator.currentWave.index)
        assertTrue(generator.roundSeconds < 1f)
        assertFalse(probe.stageComplete)
    }

    @Test
    fun `a second round's waves are tougher than the first round's`() = endless(BossKind.CACO_IMP) {
        sturdy()
        val first = screen.enmGen.currentWave.hitPoints
        downTheBoss()
        fly(ENDLESS_ROUND_BREAK_SECONDS + 0.5f)
        assertTrue(screen.enmGen.currentWave.hitPoints > first)
    }

    /** Its entrance is a cutscene wherever it comes, and its venom lifts as it falls. */
    @Test
    fun `the sky viper bites without a blow and its venom lifts with it`() = endless(BossKind.SKY_VIPER) {
        sturdy()
        val health = probe.world.getComponent(probe.batId, HealthComponent::class)!!
        screen.enmGen.update(screen.enmGen.progression.bossTimeSeconds - screen.enmGen.roundSeconds)
        val brain = screen.enmGen.bossBrain as SkyViperBrain
        var left = (20f / TICK_INITIAL).toInt()
        while (!brain.arrived) {
            assertTrue(left-- > 0, "the viper never came into its fight")
            val hitPoints = health.hitPoints
            screen.update(TICK_INITIAL)
            assertEquals(hitPoints, health.hitPoints, "the viper's entrance hurt the bat")
        }
        assertTrue(probe.poison.poisoned, "the bite did not poison the bat")

        downTheBoss()
        fly(3f)
        assertFalse(probe.poison.poisoned, "the venom stayed with the viper down")
    }
}
