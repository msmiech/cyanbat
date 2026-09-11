package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.BOSS_SPRITE_SCALE
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EntityId
import kotlin.random.Random

/**
 * Runs a level's enemies against its clock.
 *
 * The generator owns nothing but the clock and the dice: what to spawn at a given second is
 * [LevelProgression]'s answer, and this turns that answer into entities. Keeping the two apart is
 * what lets the whole difficulty curve be tested without a running game.
 *
 * Time is accumulated from the caller's fixed tick rather than read off a wall clock, which is
 * what makes a paused game a paused level - the old generator slept on a coroutine and went on
 * counting down while the player was away from the controls.
 *
 * @param onWaveChanged fired with the new wave whenever the level crosses a minute boundary, so
 *   the screen can announce it. Not fired for the opening wave, which needs no announcing.
 * @param onBossSpawned fired once, with the boss entity, when the level's boss arrives.
 */
class EnemyGenerator(
    private val xSpawnPosition: Int,
    private val worldHeight: Int,
    private val factory: EntityFactory,
    private val enemyPixmap: Pixmap,
    private var progression: LevelProgression,
    private val random: Random = Random.Default,
    private val onWaveChanged: (EnemyWave) -> Unit = {},
    private val onBossSpawned: (EntityId) -> Unit = {},
) {
    private val realEnemyHeight = enemyPixmap.height

    /** Seconds of play into the current level. Everything else is derived from it. */
    var elapsedSeconds = 0f
        private set

    var currentWave: EnemyWave = progression.waveAt(0f)
        private set

    /** The level's boss, once it has arrived. Null until then, and after it has been destroyed. */
    var bossId: EntityId? = null
        private set

    /** True from the moment the boss arrives, whether or not it is still alive. */
    var bossSpawned = false
        private set

    private var timeUntilNextSpawn = progression.nextSpawnDelay(0f, random)

    /**
     * Advances the level clock by one tick and spawns whatever has come due.
     *
     * Ordinary enemies stop the moment the boss is due: the boss is a duel, and a screen still
     * filling with escorts turns it into an ambush the player cannot read.
     */
    fun update(deltaTime: Float) {
        elapsedSeconds += deltaTime

        // Checked before the wave is advanced, so the minute the boss lands on announces the boss
        // rather than a wave of enemies that will never arrive.
        if (progression.isBossDue(elapsedSeconds)) {
            spawnBossOnce()
            return
        }

        advanceWave()

        timeUntilNextSpawn -= deltaTime
        if (timeUntilNextSpawn > 0f) return
        timeUntilNextSpawn += progression.nextSpawnDelay(elapsedSeconds, random)

        repeat(currentWave.burstSize) { spawnEnemy(currentWave) }
    }

    /** The boss has been destroyed; forget it so nothing keeps tracking a recycled id. */
    fun clearBoss() {
        bossId = null
    }

    /** Restarts the clock on a new level, so its first minute opens as gently as level 1's did. */
    fun startLevel(progression: LevelProgression) {
        this.progression = progression
        elapsedSeconds = 0f
        currentWave = progression.waveAt(0f)
        bossId = null
        bossSpawned = false
        timeUntilNextSpawn = progression.nextSpawnDelay(0f, random)
    }

    private fun advanceWave() {
        val wave = progression.waveAt(elapsedSeconds)
        if (wave.index == currentWave.index) {
            // Same wave, but the spawn interval inside it has moved on.
            currentWave = wave
            return
        }
        currentWave = wave
        onWaveChanged(wave)
    }

    private fun spawnEnemy(wave: EnemyWave) {
        factory.createEnemy(
            x = xSpawnPosition.toFloat(),
            y = 100f + random.nextInt(worldHeight - 200),
            width = ENEMY_COLLISION_WIDTH,
            height = realEnemyHeight.toFloat(),
            pixmap = enemyPixmap,
            type = wave.enemyTypes[random.nextInt(wave.enemyTypes.size)],
            hitPoints = wave.hitPoints,
            damage = wave.damage,
            speedMultiplier = wave.speedMultiplier,
        )
    }

    private fun spawnBossOnce() {
        if (bossSpawned) return
        bossSpawned = true

        val wave = progression.bossWave()
        val id = factory.createBoss(
            // Level with the edge it enters from, so it slides in rather than appearing in place.
            x = xSpawnPosition.toFloat(),
            // Centred, so its weave has the same room above it as below.
            y = (worldHeight - realEnemyHeight * BOSS_SPRITE_SCALE) / 2f,
            holdX = xSpawnPosition * BOSS_HOLD_X_FRACTION,
            pixmap = enemyPixmap,
            scale = BOSS_SPRITE_SCALE,
            hitPoints = wave.hitPoints,
            damage = wave.damage,
            shotIntervalSeconds = BOSS_SHOT_INTERVAL_SECONDS,
        )
        bossId = id
        onBossSpawned(id)
    }

    private companion object {
        /**
         * The enemy's collision box is a little narrower than its 32px frame, which is what the
         * original game used and what keeps a near miss reading as a miss.
         */
        const val ENEMY_COLLISION_WIDTH = 28f

        /**
         * Where the boss holds station, as a fraction of the framebuffer width. Far enough in that
         * the player has to come to it, far enough back that the bat still has room to dodge.
         */
        const val BOSS_HOLD_X_FRACTION = 0.62f
    }
}
