package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.desktop.recorder.RunProbe
import at.smiech.cyanbat.ecs.BossPartComponent
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.service.StageProgression
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.cyanbat.util.BOSS_SPRITE_SCALE
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.SAND_WYRM_PLATE_SHARE
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.GameLoop
import at.smiech.engine.Haptics
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.math.Rect
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt
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
     * that: the kill itself is counted once the pass has hurt both sides. A record banked as the
     * boss died came out below the score the victory overlay shows it beside.
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

    /**
     * The Sand Wyrm's body is ten entities, and only its head carries health: a shot into a plate
     * has to land on the head, or most of the fight's shots would be wasted - but only part of it,
     * because the plates are armor and the head is the place to aim.
     */
    @Test
    fun `a shot into a plate lands part of its damage on the wyrm, and into the head all of it`() = wyrmFight {
        val world = probe.world
        val health = world.getComponent(head, HealthComponent::class)!!

        // The tail: the part with the most empty frame around it, so a shot at its middle meets
        // nothing else of the body.
        val tail = world.query(BossPartComponent::class).maxBy {
            world.getComponent(it, CollisionComponent::class)!!.tolerance
        }
        val beforeTail = health.hitPoints
        shootInto(world.getComponent(tail, TransformComponent::class)!!.rect.centerY, at = tail)
        assertEquals((DAMAGE_PER_HIT * SAND_WYRM_PLATE_SHARE).roundToInt(), beforeTail - health.hitPoints)

        // High on the head, clear of the plate that hangs below it.
        val beforeHead = health.hitPoints
        shootInto(world.getComponent(head, TransformComponent::class)!!.rect.top + 12f, at = head)
        assertEquals(DAMAGE_PER_HIT, beforeHead - health.hitPoints)
    }

    /** One of the bat's shots, just short of [at]'s middle at the height [y], and a tick to land. */
    private fun WyrmFight.shootInto(y: Float, at: EntityId? = null) {
        val world = probe.world
        val target = at ?: world.query(BossPartComponent::class).first { it != head }
        val rect = world.getComponent(target, TransformComponent::class)!!.rect
        val shot = assets.graphics.shot
        EntityFactory(world).createShot(
            x = rect.centerX - SHOT_FRAME_WIDTH,
            y = y - shot.height / 2f,
            width = SHOT_FRAME_WIDTH.toFloat(),
            height = shot.height.toFloat(),
            pixmap = shot,
            isPlayer = true,
        )
        screen.update(TICK_INITIAL * 1.5f)
    }

    /**
     * Its body sweeping through the bat is how it hits. Were the bat's own contact damage to land on
     * it for every tick of the overlap, every blow the wyrm landed would cost it a slice of its bar.
     */
    @Test
    fun `the bat flying into the Sand Wyrm hurts the bat and not the wyrm`() = wyrmFight {
        val world = probe.world
        val wyrm = world.getComponent(head, HealthComponent::class)!!
        val bat = world.getComponent(probe.batId, HealthComponent::class)!!
        val wyrmBefore = wyrm.hitPoints
        val batBefore = bat.hitPoints
        val batRect = world.getComponent(probe.batId, TransformComponent::class)!!.rect
        world.getComponent(head, TransformComponent::class)!!.rect =
            Rect.fromLTWH(batRect.left, batRect.top, batRect.width, batRect.height)

        screen.update(TICK_INITIAL * 1.5f)

        assertTrue(bat.hitPoints < batBefore, "the wyrm flew through the bat without hurting it")
        assertEquals(wyrmBefore, wyrm.hitPoints, "the wyrm was hurt by landing a blow")
    }

    /**
     * Every creature's sheet stacks it unhurt, wounded and battered, and a run draws one row of it -
     * the one its health calls for. Drawn whole, the bat would be three bats, one above the other.
     */
    @Test
    fun `the bat is drawn from the row its health calls for, and healing puts it back`() = flight(CAVE) {
        val world = probe.world
        val sprite = world.getComponent(probe.batId, SpriteComponent::class)!!
        val health = world.getComponent(probe.batId, HealthComponent::class)!!
        assertEquals(BAT_ROW, sprite.srcHeight, "the bat is drawn a row at a time")
        assertEquals(BAT_ROW.toFloat(), world.getComponent(probe.batId, TransformComponent::class)!!.rect.height)

        health.hitPoints = 50
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(BAT_ROW, sprite.srcY, "wounded")

        health.hitPoints = 20
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(2 * BAT_ROW, sprite.srcY, "battered")

        health.hitPoints = health.maxHitPoints
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(0, sprite.srcY, "healed")
    }

    /**
     * The cave's boss is an imp drawn three times its size: one row of the imps' sheet magnified,
     * not the sheet's three rows - and its box sized off that one row, or the fight changes shape.
     */
    @Test
    fun `the cave's boss is one imp drawn large, and wears its wounds`() = flight(CAVE) {
        screen.enmGen.update(StageProgression.forStage(CAVE).bossTimeSeconds)
        val boss = assertNotNull(screen.enmGen.bossId, "the boss should have arrived")
        val world = probe.world
        val sprite = world.getComponent(boss, SpriteComponent::class)!!
        assertEquals(IMP_ROW, sprite.srcHeight)
        assertEquals(IMP_ROW * BOSS_SPRITE_SCALE, world.getComponent(boss, TransformComponent::class)!!.rect.height)

        val health = world.getComponent(boss, HealthComponent::class)!!
        health.hitPoints = health.maxHitPoints / 4
        screen.update(TICK_INITIAL * 1.5f)

        assertEquals(2 * IMP_ROW, sprite.srcY)
    }

    /**
     * Only the head has health to be wounded by; the plates are drawn from whatever row it is on,
     * so a battered head is never towing a pristine body.
     */
    @Test
    fun `the Sand Wyrm is wounded along its whole body at once`() = wyrmFight {
        val world = probe.world
        val health = world.getComponent(head, HealthComponent::class)!!
        health.hitPoints = health.maxHitPoints / 2

        screen.update(TICK_INITIAL * 1.5f)

        val parts = world.query(BossPartComponent::class)
        assertEquals(10, parts.size, "the head and nine plates")
        for (part in parts) {
            val sprite = world.getComponent(part, SpriteComponent::class)!!
            assertEquals(WYRM_FRAME, sprite.srcHeight)
            assertEquals(WYRM_FRAME, sprite.srcY, "part $part is not drawn wounded")
        }
    }

    /** A run of one stage: the screen, what the probe reads of it, and the assets it was built from. */
    private open class Flight(val screen: GameScreen, val probe: RunProbe, val assets: GameAssets)

    /** A run of [stage] on the desktop's own host, disposed of when [test] is done with it. */
    private fun flight(stage: Int, test: Flight.() -> Unit) {
        val game = DesktopGame(480, 320)
        val assets = GameAssets.load(game.graphics, game.audio)
        val screen = GameScreen(
            game,
            CyanBatEnvironment(
                assets = assets,
                haptics = Haptics.None,
                highscores = RecordingHighscores(),
                stageUnlocks = StageUnlockStore.InMemory(),
                onExitToMenu = {},
                audioSettings = Silent,
            ),
            stage,
        )
        try {
            Flight(screen, RunProbe(screen), assets).test()
        } finally {
            screen.dispose()
            game.audio.dispose()
        }
    }

    /** A desert run at its boss: a [Flight], and the wyrm's head. */
    private class WyrmFight(flight: Flight, val head: EntityId) : Flight(flight.screen, flight.probe, flight.assets)

    /** A desert run straight at its boss, with the escort cleared away. */
    private fun wyrmFight(test: WyrmFight.() -> Unit) = flight(DESERT) {
        screen.enmGen.update(StageProgression.forStage(DESERT).bossTimeSeconds)
        val head = assertNotNull(screen.enmGen.bossId, "the wyrm should have arrived")
        val world = probe.world
        for (id in world.query(CollisionComponent::class)) {
            val group = world.getComponent(id, CollisionComponent::class)?.group
            if (group == CollisionGroup.ENEMY && !world.hasComponent(id, BossPartComponent::class)) world.removeEntity(id)
        }
        WyrmFight(this, head).test()
    }

    private companion object {
        const val CAVE = 1
        const val DESERT = 3

        /**
         * One row of each sheet, spelled out rather than read off the code, for the reason
         * `SpriteSheetTest` gives: a test that derived them the way the game does would agree with it
         * about a sheet that had changed underneath both.
         */
        const val BAT_ROW = 40
        const val IMP_ROW = 29
        const val WYRM_FRAME = 48
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
