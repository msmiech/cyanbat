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
import at.smiech.cyanbat.util.MINIMUM_SPAWN_INTERVAL_SECONDS
import at.smiech.cyanbat.util.OPENING_SPAWN_INTERVAL_SECONDS
import at.smiech.cyanbat.util.SPAWN_INTERVAL_JITTER
import at.smiech.cyanbat.util.STAGE_DIFFICULTY_STEP
import at.smiech.cyanbat.util.WAVE_DURATION_SECONDS
import kotlin.random.Random

/**
 * One minute's worth of enemies: what spawns, how tough it is, and how fast it arrives.
 *
 * A snapshot rather than a schedule: [StageProgression] derives it from the clock, so nothing needs
 * stepping or syncing, and any point in a stage can be asked for without playing up to it.
 *
 * @param index full minutes into the stage, so wave 0 is the opening minute.
 * @param enemyTypes the species this wave draws from. A new mix every minute makes a new wave read
 *   as a new group of enemies rather than more of the last.
 * @param hitPoints what each of them can absorb.
 * @param damage what each of them takes off the bat on contact.
 * @param speedMultiplier applied to the type's own base speed.
 * @param spawnIntervalSeconds average gap between spawns, before jitter.
 * @param burstSize how many arrive together at each spawn.
 * @param shieldChance see [WaveDesign.shieldChance].
 * @param gunChance see [WaveDesign.gunChance].
 * @param eliteChance see [WaveDesign.eliteChance].
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
    val eliteChance: Float = 0f,
)

/**
 * How the pressure in one stage builds, from its opening seconds to its boss.
 *
 * Density ramps continuously: the gap between spawns shrinks a little every second, so the stage
 * tightens without announcing it. Toughness is stepped: health, damage and the enemy mix change
 * only on the full minute, so a new wave lands as an event.
 *
 * Every value is a pure function of the seconds elapsed, so the curve is testable without a game.
 *
 * @param design what the stage's waves and boss are; this class only decides how hard.
 * @param bossWave the wave the boss arrives on, and so the end of the ordinary waves. With the
 *   default minute-long waves, stage 1's boss arrives at five minutes.
 * @param difficulty scales the whole stage against stage 1, so later stages open where earlier ones
 *   left off.
 * @param bossDifficulty scales the boss and its summons the same way: the stage's difficulty unless
 *   its design keeps the boss harder; see [StageDesign.bossToughness].
 */
data class StageProgression(
    val design: StageDesign = StageDesign.CAVE,
    val waveDurationSeconds: Float = WAVE_DURATION_SECONDS,
    val bossWave: Int = BOSS_WAVE,
    val difficulty: Float = 1f,
    val bossDifficulty: Float = difficulty,
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
     * The gap between spawns, falling linearly from [OPENING_SPAWN_INTERVAL_SECONDS] to
     * [MINIMUM_SPAWN_INTERVAL_SECONDS] by the boss, scaled by difficulty and floored at half the
     * minimum, so it can never reach zero and spawn every tick.
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

    // The last entry covers every wave past the table, so a longer stage keeps its toughest mix.
    private fun designOf(index: Int): WaveDesign =
        design.waves[index.coerceAtMost(design.waves.lastIndex)]

    private fun waveFor(index: Int, elapsedSeconds: Float) = EnemyWave(
        index = index,
        enemyTypes = designOf(index).species,
        hitPoints = scaled(ENEMY_BASE_HIT_POINTS + index * ENEMY_HIT_POINTS_PER_WAVE),
        damage = scaled(ENEMY_BASE_DAMAGE + index * ENEMY_DAMAGE_PER_WAVE),
        speedMultiplier = 1f + index * ENEMY_SPEED_PER_WAVE,
        spawnIntervalSeconds = spawnIntervalAt(elapsedSeconds),
        // One arrival at a time in the opening waves, so the player learns single enemies first.
        burstSize = 1 + index / BURST_EVERY_WAVES,
        shieldChance = designOf(index).shieldChance,
        gunChance = designOf(index).gunChance,
        eliteChance = designOf(index).eliteChance,
    )

    /**
     * The stage's boss, as a wave of exactly one, so the generator reads one kind of answer. The
     * pacing fields carry the stage's end-state values, since a single arrival has no cadence.
     */
    fun bossWave(): EnemyWave = EnemyWave(
        index = bossWave,
        enemyTypes = design.waves.last().species,
        hitPoints = scaled(bossHitPointsBeforeDifficulty, bossDifficulty),
        damage = scaled(bossDamage, bossDifficulty),
        speedMultiplier = 1f,
        spawnIntervalSeconds = spawnIntervalAt(bossTimeSeconds),
        burstSize = 1,
    )

    /**
     * What a boss's summons arrive as: the wave that escorted it in, at the boss's difficulty,
     * since they are part of its fight.
     */
    fun escortWave(): EnemyWave =
        copy(difficulty = bossDifficulty).waveAt(bossTimeSeconds - ESCORT_LEAD_SECONDS)

    /**
     * How many base-damage shots the boss takes to kill: a boss's health read as the length of its
     * fight, for balancing.
     */
    val bossShotsToKill: Int
        get() = (scaled(
            bossHitPointsBeforeDifficulty,
            bossDifficulty
        ) + DAMAGE_PER_HIT - 1) / DAMAGE_PER_HIT

    /** The boss's health before its difficulty scales it: the stage's, times its design's vitality. */
    private val bossHitPointsBeforeDifficulty: Int
        get() = (bossHitPoints * design.bossVitality).toInt()

    /** [value] scaled by [by], and never below 1. */
    private fun scaled(value: Int, by: Float = difficulty): Int =
        (value * by).toInt().coerceAtLeast(1)

    companion object {
        /** Waves between each extra enemy per spawn. */
        private const val BURST_EVERY_WAVES = 3

        /** How far before the boss the escort is read, to land inside its last wave. */
        private const val ESCORT_LEAD_SECONDS = 1f

        /**
         * The progression for the stage with this 1-based [id]: stage 1 is the baseline, and each
         * one after it opens [STAGE_DIFFICULTY_STEP] harder, on top of what its design adds.
         */
        fun forStage(id: Int): StageProgression {
            val stepsAboveFirst = (id - 1).coerceAtLeast(0)
            val design = StageDesign.forStage(id)
            val difficulty = 1f + stepsAboveFirst * STAGE_DIFFICULTY_STEP
            return StageProgression(
                design = design,
                difficulty = difficulty,
                bossDifficulty = difficulty * design.bossToughness,
            )
        }
    }
}
