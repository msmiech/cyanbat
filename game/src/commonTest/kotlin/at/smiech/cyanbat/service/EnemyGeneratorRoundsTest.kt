package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.World
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A sheet of a given stage's: only its height and identity are read here. */
private class HomeSheet(val home: Int) : Pixmap {
    override val width = 640
    override val height = 87
    override fun dispose() = Unit
}

/**
 * How [EnemyGenerator] runs the endless stage: round after round on one clock, each species drawn
 * from its own stage's sheet.
 */
class EnemyGeneratorRoundsTest {

    private val world = World()
    private val factory = EntityFactory(world)
    private val announced = mutableListOf<Int>()
    private val sheets = (1..4).associateWith { HomeSheet(it) }
    private val stageSheet = HomeSheet(0)

    /** A round of the cave's waves and its boss, a minute of waves long, after [before] waves. */
    private fun round(before: Int, design: StageDesign = StageDesign.CAVE) =
        StageProgression(design = design, bossWave = 1, wavesBefore = before)

    private fun generator(first: StageProgression) = EnemyGenerator(
        xSpawnPosition = FRAME_BUFFER_WIDTH,
        worldHeight = FRAME_BUFFER_HEIGHT,
        factory = factory,
        enemyPixmap = stageSheet,
        progression = first,
        random = Random(5),
        onWaveChanged = { announced += it.index },
        sheets = { sheets.getValue(it.home) },
    )

    private fun EnemyGenerator.run(seconds: Float) {
        repeat((seconds / TICK_INITIAL).toInt()) { update(TICK_INITIAL) }
    }

    private fun bosses() = world.query(EnemyBehaviorComponent::class).count {
        world.getComponent(it, EnemyBehaviorComponent::class)?.type == EnemyMovementType.BOSS
    }

    @Test
    fun `the next round starts its waves afresh on the clock the run keeps`() {
        val generator = generator(round(0))
        generator.run(WAVE_DURATION_SECONDS + 1f)
        assertTrue(generator.bossSpawned)
        val boss = assertNotNull(generator.bossId)
        world.removeEntity(boss)
        generator.clearBoss()
        val clock = generator.elapsedSeconds

        generator.beginRound(round(1))

        assertFalse(generator.bossSpawned)
        assertNull(generator.bossId)
        assertEquals(clock, generator.elapsedSeconds)
        assertEquals(0f, generator.roundSeconds)
        assertEquals(1, generator.currentWave.index)
        assertEquals(1, announced.last(), "the round's first wave was not announced")
        assertEquals(1, generator.progression.wavesBefore)
    }

    @Test
    fun `a new round brings its own boss a round's length later`() {
        val generator = generator(round(0))
        generator.run(WAVE_DURATION_SECONDS + 1f)
        world.update(TICK_INITIAL, null)
        generator.bossId?.let { world.removeEntity(it) }
        generator.clearBoss()
        world.update(TICK_INITIAL, null)
        generator.beginRound(round(1))

        generator.run(WAVE_DURATION_SECONDS - 2f)
        assertFalse(generator.bossSpawned, "the boss came before its round's waves were done")
        generator.run(3f)
        assertTrue(generator.bossSpawned)
        assertEquals(1, bosses())
    }

    @Test
    fun `every species is drawn from its own stage's sheet`() {
        val generator = generator(round(0, StageDesign.JUNGLE.copy(boss = BossKind.CACO_IMP)))
        generator.run(40f)
        val enemies = world.query(EnemyBehaviorComponent::class)
        assertTrue(enemies.isNotEmpty())
        for (id in enemies) {
            assertSame(sheets.getValue(1), world.getComponent(id, SpriteComponent::class)?.pixmap)
        }
    }

    @Test
    fun `a boss drawn off an enemy sheet takes its own stage's`() {
        val generator = generator(round(0))
        generator.run(WAVE_DURATION_SECONDS + 1f)
        val boss = assertNotNull(generator.bossId)
        // The Caco Imp is the cave's, magnified.
        assertSame(sheets.getValue(2), world.getComponent(boss, SpriteComponent::class)?.pixmap)
    }
}
