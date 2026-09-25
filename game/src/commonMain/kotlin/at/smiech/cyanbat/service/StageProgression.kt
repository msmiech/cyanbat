package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_DAMAGE_PER_STAGE
import at.smiech.cyanbat.util.BOSS_HIT_POINTS_PER_STAGE
import at.smiech.cyanbat.util.BOSS_WAVE
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.ENEMY_BASE_DAMAGE
import at.smiech.cyanbat.util.ENEMY_BASE_HIT_POINTS
import at.smiech.cyanbat.util.ENEMY_DAMAGE_PER_WAVE
import at.smiech.cyanbat.util.ENEMY_HIT_POINTS_PER_WAVE
import at.smiech.cyanbat.util.ENEMY_SPEED_PER_WAVE
import at.smiech.cyanbat.util.STAGE_DIFFICULTY_STEP
import at.smiech.cyanbat.util.MINIMUM_SPAWN_INTERVAL_SECONDS
import at.smiech.cyanbat.util.OPENING_SPAWN_INTERVAL_SECONDS
import at.smiech.cyanbat.util.SPAWN_INTERVAL_JITTER
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import kotlin.random.Random

/**
 * One minute's worth of enemies: what spawns, how tough it is, and how fast it arrives.
 *
 * A wave is a snapshot rather than a schedule - [StageProgression] derives it from the time on the
 * clock, so nothing has to be stepped or kept in sync, and a wave can be asked for at any point in
 * a stage without having played up to it.
 *
 * @param index full minutes into the stage, so wave 0 is the opening minute.
 * @param enemyTypes the species this wave draws from. Changing the mix every minute is what makes a
 *   new wave read as a new *group* of enemies rather than as more of the last one.
 * @param hitPoints what each of them can absorb.
 * @param damage what each of them takes off the bat on contact.
 * @param speedMultiplier applied to the type's own base speed.
 * @param spawnIntervalSeconds average gap between spawns, before jitter.
 * @param burstSize how many arrive together at each spawn.
 * @param shieldChance/gunChance see [WaveDesign].
 */
data class EnemyWave(
    val index: Int,
    val enemyTypes: List<EnemySpecies>,
    val hitPoints: Int,
    val damage: Int,
    val speedMultiplier: Float,
    val spawnIntervalSeconds: Float,
    val burstSize: Int,
    val shieldChance: Float = 0f,
    val gunChance: Float = 0f,
)

/**
 * How the pressure in one stage builds, from its opening seconds to its boss.
 *
 * Two things ramp, and they ramp differently on purpose. Density is *continuous*: the gap between
 * spawns shrinks a little with every second played, so the stage tightens under the player without
 * ever announcing it. Toughness is *stepped*: health, damage and the enemy mix change only on the
 * full minute, so a new wave lands as an event the player can feel and name. A stage that ramped
 * both continuously would just feel like one long slope with nothing in it.
 *
 * Every value is a pure function of the seconds elapsed, which is what makes the whole curve
 * testable without running a game.
 *
 * @param bossWave the wave the boss arrives on, and so the last wave of ordinary enemies. At the
 *   default minute-long waves, stage 1's boss is the five minute mark.
 * @param difficulty scales the whole stage against stage 1, so later stages open where earlier
 *   ones left off instead of starting from nothing again.
 * @param design what the stage's waves and boss are; this class only decides how hard.
 */
data class StageProgression(
    val design: StageDesign = StageDesign.CAVE,
    val waveDurationSeconds: Float = WAVE_DURATION_SECONDS,
    val bossWave: Int = BOSS_WAVE,
    val difficulty: Float = 1f,
    val bossHitPoints: Int = BOSS_HIT_POINTS_PER_STAGE,
    val bossDamage: Int = BOSS_DAMAGE_PER_STAGE,
) {
    /** When the boss arrives, in seconds from the start of the stage. */
    val bossTimeSeconds: Float = bossWave * waveDurationSeconds

    /** Full minutes into the stage, held at [bossWave] once the boss fight has begun. */
    fun waveIndexAt(elapsedSeconds: Float): Int =
        (elapsedSeconds / waveDurationSeconds).toInt().coerceIn(0, bossWave)

    /** True once the stage has run long enough for its boss to be due. */
    fun isBossDue(elapsedSeconds: Float): Boolean = elapsedSeconds >= bossTimeSeconds

    /**
     * The gap between spawns, easing from [OPENING_SPAWN_INTERVAL_SECONDS] down to
     * [MINIMUM_SPAWN_INTERVAL_SECONDS] across the run up to the boss.
     *
     * Clamped at both ends rather than decayed per spawn: a gap is a delay the game then waits
     * out, so one that could reach zero would spawn enemies every single tick.
     */
    fun spawnIntervalAt(elapsedSeconds: Float): Float {
        val progress = (elapsedSeconds / bossTimeSeconds).coerceIn(0f, 1f)
        val opening = OPENING_SPAWN_INTERVAL_SECONDS / difficulty
        val floor = MINIMUM_SPAWN_INTERVAL_SECONDS / difficulty
        return (opening + (floor - opening) * progress).coerceAtLeast(MINIMUM_SPAWN_INTERVAL_SECONDS / 2f)
    }

    /** [spawnIntervalAt] with its jitter applied, so spawns do not fall into a rhythm. */
    fun nextSpawnDelay(elapsedSeconds: Float, random: Random): Float {
        val interval = spawnIntervalAt(elapsedSeconds)
        val jitter = 1f + (random.nextFloat() * 2f - 1f) * SPAWN_INTERVAL_JITTER
        return interval * jitter
    }

    /** The wave in force at [elapsedSeconds]. */
    fun waveAt(elapsedSeconds: Float): EnemyWave =
        waveFor(waveIndexAt(elapsedSeconds), elapsedSeconds)

    // The last entry covers every wave past the table, so a longer stage degrades into its toughest
    // mix instead of running off the end.
    private fun designOf(index: Int): WaveDesign =
        design.waves[index.coerceAtMost(design.waves.lastIndex)]

    private fun waveFor(index: Int, elapsedSeconds: Float) = EnemyWave(
        index = index,
        enemyTypes = designOf(index).species,
        hitPoints = scaled(ENEMY_BASE_HIT_POINTS + index * ENEMY_HIT_POINTS_PER_WAVE),
        damage = scaled(ENEMY_BASE_DAMAGE + index * ENEMY_DAMAGE_PER_WAVE),
        speedMultiplier = 1f + index * ENEMY_SPEED_PER_WAVE,
        spawnIntervalSeconds = spawnIntervalAt(elapsedSeconds),
        // Kept to one arrival for the opening waves: two enemies at once is a shape to read, and
        // the player should meet it once they have learned to read one.
        burstSize = 1 + index / BURST_EVERY_WAVES,
        shieldChance = designOf(index).shieldChance,
        gunChance = designOf(index).gunChance,
    )

    /**
     * The boss of this stage, as a wave of exactly one.
     *
     * It borrows the shape of an ordinary wave so the generator has one kind of answer to read,
     * not two. The pacing fields carry the stage's own end-state values rather than anything
     * special: a wave of one arriving once has no cadence of its own to describe.
     */
    fun bossWave(): EnemyWave = EnemyWave(
        index = bossWave,
        enemyTypes = design.waves.last().species,
        hitPoints = scaled(bossHitPoints),
        damage = scaled(bossDamage),
        speedMultiplier = 1f,
        spawnIntervalSeconds = spawnIntervalAt(bossTimeSeconds),
        burstSize = 1,
    )

    /**
     * How many of the player's shots the boss soaks up. The only honest way to read a boss health
     * pool is as the length of the fight, and at a fixed fire rate that length is a shot count.
     */
    val bossShotsToKill: Int get() = (scaled(bossHitPoints) + DAMAGE_PER_HIT - 1) / DAMAGE_PER_HIT

    private fun scaled(value: Int): Int = (value * difficulty).toInt().coerceAtLeast(1)

    companion object {
        /** Waves between each extra enemy per spawn. */
        private const val BURST_EVERY_WAVES = 3

        /**
         * The progression for the stage with this id, where stage 1 is the baseline and each one
         * after it opens [STAGE_DIFFICULTY_STEP] harder, on top of whatever its own design adds.
         *
         * Stage ids are 1-based, matching [at.smiech.cyanbat.resource.Stage.id].
         */
        fun forStage(id: Int): StageProgression {
            val stepsAboveFirst = (id - 1).coerceAtLeast(0)
            return StageProgression(
                design = StageDesign.forStage(id),
                difficulty = 1f + stepsAboveFirst * STAGE_DIFFICULTY_STEP,
            )
        }

    }
}
