package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.BOSS_SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.BOSS_SPRITE_SCALE
import at.smiech.cyanbat.util.BURROW_SHOWING
import at.smiech.cyanbat.util.FIRST_SHOT_JITTER
import at.smiech.cyanbat.util.FORMATION_RANKS
import at.smiech.cyanbat.util.FORMATION_RANK_SPACING_X
import at.smiech.cyanbat.util.FORMATION_RANK_SPACING_Y
import at.smiech.cyanbat.util.GROUP_EDGE_MARGIN
import at.smiech.cyanbat.util.HOLD_X_MAX_FRACTION
import at.smiech.cyanbat.util.HOLD_X_MIN_FRACTION
import at.smiech.cyanbat.util.SAND_WYRM_BAR_HEIGHT
import at.smiech.cyanbat.util.SAND_WYRM_BAR_TOP
import at.smiech.cyanbat.util.SAND_WYRM_BAR_WIDTH
import at.smiech.cyanbat.util.SAND_WYRM_BODY_DAMAGE
import at.smiech.cyanbat.util.SAND_WYRM_FRAME
import at.smiech.cyanbat.util.SAND_WYRM_HEAD_DAMAGE
import at.smiech.cyanbat.util.SWARM_SIZE
import at.smiech.cyanbat.util.SWARM_SIZE_MAX
import at.smiech.cyanbat.util.SWARM_SIZE_PER_TWO_WAVES
import at.smiech.cyanbat.util.SWARM_SPREAD_X
import at.smiech.cyanbat.util.SWARM_SPREAD_Y
import at.smiech.cyanbat.util.WAVE_SHIELD_FRACTION
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.math.Rect
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Runs a stage's enemies against its clock.
 *
 * The generator owns nothing but the clock and the dice: what to spawn at a given second is
 * [StageProgression]'s answer, and this turns that answer into entities. Keeping the two apart is
 * what lets the whole difficulty curve be tested without a running game.
 *
 * Time is accumulated from the caller's fixed tick rather than read off a wall clock, which is
 * what makes a paused game a paused stage - the old generator slept on a coroutine and went on
 * counting down while the player was away from the controls.
 *
 * @param enemyPixmap the stage's enemy sheet, which every species of that stage is drawn from.
 * @param bossPixmap the sheet of a boss drawn at its own size, for the stages that have one.
 * @param onWaveChanged fired with the new wave whenever the stage crosses a minute boundary, so
 *   the screen can announce it. Not fired for the opening wave, which needs no announcing.
 * @param onBossSpawned fired once, with the boss entity, when the stage's boss arrives.
 * @param onBossPhaseChanged fired when a boss with phases enters a new one.
 */
class EnemyGenerator(
    private val xSpawnPosition: Int,
    private val worldHeight: Int,
    private val factory: EntityFactory,
    private val enemyPixmap: Pixmap,
    private var progression: StageProgression,
    private val random: Random = Random.Default,
    private val onWaveChanged: (EnemyWave) -> Unit = {},
    private val onBossSpawned: (EntityId) -> Unit = {},
    private val bossPixmap: Pixmap? = null,
    private val onBossPhaseChanged: (Int) -> Unit = {},
) {
    private val realEnemyHeight = enemyPixmap.height

    /** Seconds of play into the current stage. Everything else is derived from it. */
    var elapsedSeconds = 0f
        private set

    var currentWave: EnemyWave = progression.waveAt(0f)
        private set

    /** The stage's boss, once it has arrived. Null until then, and after it has been destroyed. */
    var bossId: EntityId? = null
        private set

    /** True from the moment the boss arrives, whether or not it is still alive. */
    var bossSpawned = false
        private set

    /** The boss's own logic, for a boss that has any; see [MothQueenBrain] and [SandWyrmBrain]. */
    var bossBrain: BossBrain? = null
        private set

    private var timeUntilNextSpawn = progression.nextSpawnDelay(0f, random)

    /**
     * Advances the stage clock by one tick and spawns whatever has come due.
     *
     * Ordinary enemies stop the moment the boss is due: the boss is a duel, and a screen still
     * filling with escorts turns it into an ambush the player cannot read. What a boss calls in
     * itself is part of its fight, and comes through its brain.
     */
    fun update(deltaTime: Float) {
        elapsedSeconds += deltaTime

        // Checked before the wave is advanced, so the minute the boss lands on announces the boss
        // rather than a wave of enemies that will never arrive.
        if (progression.isBossDue(elapsedSeconds)) {
            spawnBossOnce()
            bossBrain?.update(deltaTime)
            return
        }

        advanceWave()

        timeUntilNextSpawn -= deltaTime
        if (timeUntilNextSpawn > 0f) return

        // A group buys the player more time than a loner does, so the gap after it is stretched by
        // its cost; see [Squad.cost]. A burst waits on its most expensive group.
        var cost = 0f
        repeat(currentWave.burstSize) { cost = maxOf(cost, spawnGroup(currentWave)) }
        timeUntilNextSpawn += progression.nextSpawnDelay(elapsedSeconds, random) * cost
    }

    /** The boss has been destroyed; forget it so nothing keeps tracking a recycled id. */
    fun clearBoss() {
        bossId = null
        bossBrain = null
    }

    /** Restarts the clock on a new stage, so its first minute opens as gently as stage 1's did. */
    fun startStage(progression: StageProgression) {
        this.progression = progression
        elapsedSeconds = 0f
        currentWave = progression.waveAt(0f)
        bossId = null
        bossBrain = null
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

    /** Picks a species from [wave] and sends it in however it travels, returning the group's cost. */
    private fun spawnGroup(wave: EnemyWave): Float {
        val species = wave.enemyTypes[random.nextInt(wave.enemyTypes.size)]
        when (species.squad) {
            Squad.SOLO -> spawnSolo(species, wave)
            Squad.SWARM -> spawnSwarm(species, wave)
            Squad.V_FORMATION -> spawnFormation(species, wave)
            Squad.BURROW -> spawnBurrowed(species, wave)
        }
        return species.squad.cost
    }

    private fun spawnSolo(species: EnemySpecies, wave: EnemyWave) {
        val y = 100f + random.nextInt(worldHeight - 200)
        spawn(species, wave, x = xSpawnPosition.toFloat(), laneY = y)
    }

    /**
     * A flock around a shared path: every member is spawned on this tick, so they share a clock
     * and a sway, and each is scattered a little around the path and given its own buzz.
     */
    private fun spawnSwarm(species: EnemySpecies, wave: EnemyWave, centerY: Float? = null) {
        val size = (SWARM_SIZE + wave.index / 2 * SWARM_SIZE_PER_TWO_WAVES).coerceAtMost(SWARM_SIZE_MAX)
        val laneY = (centerY ?: groupLane()) - realEnemyHeight / 2f
        repeat(size) {
            spawn(
                species, wave,
                x = xSpawnPosition + random.nextFloat() * SWARM_SPREAD_X,
                laneY = laneY,
                offsetY = (random.nextFloat() * 2f - 1f) * SWARM_SPREAD_Y,
                phase = random.nextFloat() * TWO_PI,
            )
        }
    }

    /** Five in a V, the leader at the point and two ranks trailing it on either side. */
    private fun spawnFormation(species: EnemySpecies, wave: EnemyWave) {
        val laneY = groupLane() - realEnemyHeight / 2f
        spawn(species, wave, x = xSpawnPosition.toFloat(), laneY = laneY)
        for (rank in 1..FORMATION_RANKS) {
            for (side in SIDES) {
                spawn(
                    species, wave,
                    x = xSpawnPosition + rank * FORMATION_RANK_SPACING_X,
                    laneY = laneY,
                    offsetY = side * rank * FORMATION_RANK_SPACING_Y,
                )
            }
        }
    }

    /**
     * In along the bottom edge from the right, under the sand with only its back showing, to leap
     * from its station; see [at.smiech.engine.ecs.EnemyMovementType.LEAP].
     */
    private fun spawnBurrowed(species: EnemySpecies, wave: EnemyWave, x: Float = xSpawnPosition.toFloat()) {
        spawn(species, wave, x = x, laneY = worldHeight - BURROW_SHOWING)
    }

    /** A center for a group's path, far enough from the edges that its sway stays on screen. */
    private fun groupLane(): Float =
        GROUP_EDGE_MARGIN + random.nextFloat() * (worldHeight - 2f * GROUP_EDGE_MARGIN)

    /**
     * One enemy of [species] at [wave]'s strength, with whatever shield and gun the dice give it.
     *
     * Dice are only rolled for what a species can actually have, so a stage whose enemies never
     * carry shields or guns - the cave - draws exactly the numbers it always did.
     */
    private fun spawn(
        species: EnemySpecies,
        wave: EnemyWave,
        x: Float,
        laneY: Float,
        offsetY: Float = 0f,
        phase: Float = 0f,
    ) {
        val hitPoints = (wave.hitPoints * species.hitPointFactor).roundToInt().coerceAtLeast(1)
        val damage = (wave.damage * species.damageFactor).roundToInt().coerceAtLeast(1)

        val shieldPoints = when {
            species.innateShield > 0f -> (hitPoints * species.innateShield).roundToInt()
            species.canBeShielded && wave.shieldChance > 0f && random.nextFloat() < wave.shieldChance ->
                (hitPoints * WAVE_SHIELD_FRACTION).roundToInt()
            else -> 0
        }
        val gun = species.gun ?: EnemySpecies.ISSUED_GUN.takeIf {
            species.armable && wave.gunChance > 0f && random.nextFloat() < wave.gunChance
        }
        val holdX = when (species.movement) {
            EnemyMovementType.HOVER, EnemyMovementType.DIVE, EnemyMovementType.LEAP, EnemyMovementType.LOOP ->
                xSpawnPosition * (HOLD_X_MIN_FRACTION + random.nextFloat() * (HOLD_X_MAX_FRACTION - HOLD_X_MIN_FRACTION))
            else -> 0f
        }

        factory.createEnemy(
            x = x,
            y = laneY + offsetY,
            width = ENEMY_COLLISION_WIDTH,
            height = realEnemyHeight.toFloat(),
            pixmap = enemyPixmap,
            species = species,
            hitPoints = hitPoints,
            damage = damage,
            speedMultiplier = wave.speedMultiplier,
            laneY = laneY,
            offsetY = offsetY,
            phase = phase,
            holdX = holdX,
            shieldPoints = shieldPoints,
            // Only a shell of its own grows back. One a wave handed out is spent once it is spent.
            shieldRegrowth = if (species.innateShield > 0f) species.shieldRegrowth else 0f,
            gun = gun,
            firstShotDelay = gun?.let { it.interval * FIRST_SHOT_JITTER * random.nextFloat() } ?: 0f,
        )
    }

    private fun spawnBossOnce() {
        if (bossSpawned) return
        bossSpawned = true

        val wave = progression.bossWave()
        val id = when (progression.design.boss) {
            BossKind.CAVE_DRONE -> factory.createBoss(
                // Level with the edge it enters from, so it slides in rather than appearing in place.
                x = xSpawnPosition.toFloat(),
                // Centered, so its weave has the same room above it as below.
                y = (worldHeight - realEnemyHeight * BOSS_SPRITE_SCALE) / 2f,
                holdX = xSpawnPosition * BOSS_HOLD_X_FRACTION,
                pixmap = enemyPixmap,
                scale = BOSS_SPRITE_SCALE,
                hitPoints = wave.hitPoints,
                damage = wave.damage,
                shotIntervalSeconds = BOSS_SHOT_INTERVAL_SECONDS,
            )

            BossKind.MOTH_QUEEN -> {
                val sheet = requireNotNull(bossPixmap) { "The Moth Queen needs her own sheet" }
                factory.createMothQueen(
                    x = xSpawnPosition.toFloat(),
                    y = (worldHeight - sheet.height) / 2f,
                    holdX = xSpawnPosition * BOSS_HOLD_X_FRACTION,
                    pixmap = sheet,
                    hitPoints = wave.hitPoints,
                    damage = wave.damage,
                    gun = MothQueenBrain.OPENING_GUN,
                ).also { queen ->
                    bossBrain = MothQueenBrain(
                        factory.world,
                        queen,
                        onSummon = ::summonSwarm,
                        onPhaseChanged = onBossPhaseChanged,
                    )
                }
            }

            BossKind.SAND_WYRM -> {
                val sheet = requireNotNull(bossPixmap) { "The Sand Wyrm needs its own sheet" }
                val ids = factory.createSandWyrm(
                    // Under the sand to the right of the frame's middle; its brain buries it
                    // properly and picks where it first comes up.
                    x = xSpawnPosition * 0.7f,
                    y = worldHeight + SAND_WYRM_FRAME.toFloat(),
                    pixmap = sheet,
                    hitPoints = wave.hitPoints,
                    damage = (wave.damage * SAND_WYRM_HEAD_DAMAGE).roundToInt().coerceAtLeast(1),
                    bodyDamage = (wave.damage * SAND_WYRM_BODY_DAMAGE).roundToInt().coerceAtLeast(1),
                    gun = SandWyrmBrain.HUNTING_GUN,
                    bar = Rect.fromLTWH(
                        (xSpawnPosition - SAND_WYRM_BAR_WIDTH) / 2f,
                        SAND_WYRM_BAR_TOP.toFloat(),
                        SAND_WYRM_BAR_WIDTH.toFloat(),
                        SAND_WYRM_BAR_HEIGHT.toFloat(),
                    ),
                )
                bossBrain = SandWyrmBrain(
                    factory.world,
                    factory,
                    sheet,
                    headId = ids.first(),
                    parts = ids.drop(1),
                    frameWidth = xSpawnPosition,
                    frameHeight = worldHeight,
                    onSummon = ::summonWyrmlings,
                    onPhaseChanged = onBossPhaseChanged,
                )
                ids.first()
            }
        }
        bossId = id
        onBossSpawned(id)
    }

    /**
     * A swarm the boss has called in: the same wasps as the stage's own, at the strength of the
     * wave that escorted her in, arriving from the edge in a lane away from the middle - she holds
     * the middle, and a swarm spawned inside her would be a swarm the player never saw arrive.
     */
    private fun summonSwarm() {
        val escort = progression.waveAt(progression.bossTimeSeconds - 1f)
        val high = random.nextBoolean()
        val centerY = if (high) GROUP_EDGE_MARGIN else worldHeight - GROUP_EDGE_MARGIN
        spawnSwarm(EnemySpecies.WASP, escort, centerY)
    }

    /**
     * Wyrmlings the Sand Wyrm has called up: its own brood, at the strength of the wave that
     * escorted it in, coming in under the sand like any other - spaced out, so each one's back is
     * its own warning rather than two arriving as one.
     */
    private fun summonWyrmlings(count: Int) {
        val escort = progression.waveAt(progression.bossTimeSeconds - 1f)
        repeat(count) { spawnBurrowed(EnemySpecies.WYRMLING, escort, x = xSpawnPosition + it * SUMMON_SPACING) }
    }

    private companion object {
        /** How far apart, along the sand, wyrmlings called up together start out. */
        const val SUMMON_SPACING = 90f

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

        const val TWO_PI = (2.0 * PI).toFloat()

        /** Above and below the leader, for the two wings of a V. */
        val SIDES = floatArrayOf(-1f, 1f)
    }
}
