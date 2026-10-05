package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.ElitePalette
import at.smiech.cyanbat.util.BEHIND_HOLD_X_MAX_FRACTION
import at.smiech.cyanbat.util.BEHIND_HOLD_X_MIN_FRACTION
import at.smiech.cyanbat.util.BOSS_BAR_HEIGHT
import at.smiech.cyanbat.util.BOSS_BAR_TOP
import at.smiech.cyanbat.util.BOSS_BAR_WIDTH
import at.smiech.cyanbat.util.BOSS_SPRITE_SCALE
import at.smiech.cyanbat.util.BURROW_SHOWING
import at.smiech.cyanbat.util.ELITE_FIRE_INTERVAL_FACTOR
import at.smiech.cyanbat.util.ELITE_HIT_POINT_FACTOR
import at.smiech.cyanbat.util.FIRST_SHOT_JITTER
import at.smiech.cyanbat.util.FORMATION_RANKS
import at.smiech.cyanbat.util.FORMATION_RANK_SPACING_X
import at.smiech.cyanbat.util.FORMATION_RANK_SPACING_Y
import at.smiech.cyanbat.util.GROUP_EDGE_MARGIN
import at.smiech.cyanbat.util.HOLD_X_MAX_FRACTION
import at.smiech.cyanbat.util.HOLD_X_MIN_FRACTION
import at.smiech.cyanbat.util.NAGA_BODY_DAMAGE
import at.smiech.cyanbat.util.NAGA_FRAME
import at.smiech.cyanbat.util.NAGA_HEAD_DAMAGE
import at.smiech.cyanbat.util.SAND_WYRM_BODY_DAMAGE
import at.smiech.cyanbat.util.SAND_WYRM_FRAME
import at.smiech.cyanbat.util.SAND_WYRM_HEAD_DAMAGE
import at.smiech.cyanbat.util.SWARM_SIZE
import at.smiech.cyanbat.util.SWARM_SIZE_MAX
import at.smiech.cyanbat.util.SWARM_SIZE_PER_TWO_WAVES
import at.smiech.cyanbat.util.SWARM_SPREAD_X
import at.smiech.cyanbat.util.SWARM_SPREAD_Y
import at.smiech.cyanbat.util.WAVE_SHIELD_FRACTION
import at.smiech.cyanbat.util.WOUND_ROWS
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
    /** One picture of an enemy: the sheet stacks each of them unhurt, wounded and battered. */
    private val realEnemyHeight = enemyPixmap.height / WOUND_ROWS

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

    /** The boss's own logic; see [CacoImpBrain], [MothQueenBrain], [SandWyrmBrain] and [NagaBrain]. */
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

    /**
     * Picks a species from [wave] and sends it in however it travels, returning the group's cost.
     *
     * Whether the group brings an elite is rolled here, once for the whole group; see
     * [WaveDesign.eliteChance]. It falls to the group's first member: one of a swarm, scattered like
     * the rest of it, and the leader of a formation, at the point of the V.
     */
    private fun spawnGroup(wave: EnemyWave): Float {
        val species = wave.enemyTypes[random.nextInt(wave.enemyTypes.size)]
        // Only rolled where the wave sends elites at all, so an opening minute draws the dice it
        // always did.
        val elite = wave.eliteChance > 0f && random.nextFloat() < wave.eliteChance
        when (species.squad) {
            Squad.SOLO -> spawnSolo(species, wave, elite)
            Squad.SWARM -> spawnSwarm(species, wave, elite = elite)
            Squad.V_FORMATION -> spawnFormation(species, wave, elite)
            Squad.BURROW -> spawnBurrowed(species, wave, elite = elite)
            Squad.FROM_BEHIND -> spawnBehind(species, wave, elite)
        }
        return species.squad.cost
    }

    private fun spawnSolo(species: EnemySpecies, wave: EnemyWave, elite: Boolean) {
        val y = 100f + random.nextInt(worldHeight - 200)
        spawn(species, wave, x = xSpawnPosition.toFloat(), laneY = y, elite = elite)
    }

    /**
     * A flock around a shared path: every member is spawned on this tick, so they share a clock
     * and a sway, and each is scattered a little around the path and given its own buzz.
     *
     * @param elite whether one of them is an elite.
     */
    private fun spawnSwarm(species: EnemySpecies, wave: EnemyWave, centerY: Float? = null, elite: Boolean = false) {
        val size = (SWARM_SIZE + wave.index / 2 * SWARM_SIZE_PER_TWO_WAVES).coerceAtMost(SWARM_SIZE_MAX)
        val laneY = (centerY ?: groupLane()) - realEnemyHeight / 2f
        repeat(size) { member ->
            spawn(
                species, wave,
                x = xSpawnPosition + random.nextFloat() * SWARM_SPREAD_X,
                laneY = laneY,
                offsetY = (random.nextFloat() * 2f - 1f) * SWARM_SPREAD_Y,
                phase = random.nextFloat() * TWO_PI,
                elite = elite && member == 0,
            )
        }
    }

    /**
     * Five in a V, the leader at the point and two ranks trailing it on either side.
     *
     * @param elite whether the leader is an elite.
     */
    private fun spawnFormation(species: EnemySpecies, wave: EnemyWave, elite: Boolean) {
        val laneY = groupLane() - realEnemyHeight / 2f
        spawn(species, wave, x = xSpawnPosition.toFloat(), laneY = laneY, elite = elite)
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
    private fun spawnBurrowed(
        species: EnemySpecies,
        wave: EnemyWave,
        x: Float = xSpawnPosition.toFloat(),
        elite: Boolean = false,
    ) {
        spawn(species, wave, x = x, laneY = worldHeight - BURROW_SHOWING, elite = elite)
    }

    /**
     * In along the bottom edge from the left - behind the bat - with only its fin above the water, to
     * leap forward from its station; see [EnemySpecies.SHARK].
     */
    private fun spawnBehind(species: EnemySpecies, wave: EnemyWave, elite: Boolean) {
        spawn(species, wave, x = -ENEMY_COLLISION_WIDTH, laneY = worldHeight - BURROW_SHOWING, elite = elite)
    }

    /** A center for a group's path, far enough from the edges that its sway stays on screen. */
    private fun groupLane(): Float =
        GROUP_EDGE_MARGIN + random.nextFloat() * (worldHeight - 2f * GROUP_EDGE_MARGIN)

    /**
     * One enemy of [species] at [wave]'s strength, with whatever shield and gun the dice give it.
     *
     * Dice are only rolled for what a species can actually have, so a stage whose enemies never
     * carry shields or guns - the cave - draws exactly the numbers it always did until an elite
     * turns up, and only an elite rolls for its colors.
     *
     * @param elite whether it is one: tougher, quicker to fire, armed whatever its kind, and in
     *   colors of its own; see [ElitePalette].
     */
    private fun spawn(
        species: EnemySpecies,
        wave: EnemyWave,
        x: Float,
        laneY: Float,
        offsetY: Float = 0f,
        phase: Float = 0f,
        elite: Boolean = false,
    ) {
        val ordinaryHitPoints = (wave.hitPoints * species.hitPointFactor).roundToInt().coerceAtLeast(1)
        val hitPoints =
            if (elite) (ordinaryHitPoints * ELITE_HIT_POINT_FACTOR).roundToInt() else ordinaryHitPoints
        val damage = (wave.damage * species.damageFactor).roundToInt().coerceAtLeast(1)

        // Sized off an ordinary one of its kind: what makes an elite tougher is what is inside the
        // shell, and an elite beetle behind two and a half shells' worth would be a wall.
        val shieldPoints = when {
            species.innateShield > 0f -> (ordinaryHitPoints * species.innateShield).roundToInt()
            species.canBeShielded && wave.shieldChance > 0f && random.nextFloat() < wave.shieldChance ->
                (ordinaryHitPoints * WAVE_SHIELD_FRACTION).roundToInt()
            else -> 0
        }
        val issued = species.gun ?: EnemySpecies.ISSUED_GUN.takeIf {
            species.armable && wave.gunChance > 0f && random.nextFloat() < wave.gunChance
        }
        val gun = if (elite) eliteGun(issued, species) else issued
        val palette = if (elite) ElitePalette.entries[random.nextInt(ElitePalette.entries.size)] else null
        val holdX = when {
            // Behind the bat, for something coming from behind, so its leap tops out about where the bat is.
            species.squad == Squad.FROM_BEHIND ->
                xSpawnPosition * (BEHIND_HOLD_X_MIN_FRACTION + random.nextFloat() * (BEHIND_HOLD_X_MAX_FRACTION - BEHIND_HOLD_X_MIN_FRACTION))
            species.movement in HOLDING ->
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
            elite = palette,
        )
    }

    /**
     * What an elite fires: its kind's own gun, or the waves' issued one for a kind that carries none -
     * an aimed one for a kind that comes from behind - on a shorter cadence either way; see
     * [ELITE_FIRE_INTERVAL_FACTOR].
     */
    private fun eliteGun(gun: EnemyGun?, species: EnemySpecies): EnemyGun {
        val base = gun ?: if (species.drawnFacingRight) EnemySpecies.ISSUED_AIMED_GUN else EnemySpecies.ISSUED_GUN
        return base.copy(interval = base.interval * ELITE_FIRE_INTERVAL_FACTOR)
    }

    private fun spawnBossOnce() {
        if (bossSpawned) return
        bossSpawned = true

        val wave = progression.bossWave()
        val id = when (progression.design.boss) {
            BossKind.CACO_IMP -> factory.createBoss(
                // Level with the edge it enters from, so it slides in rather than appearing in place.
                x = xSpawnPosition.toFloat(),
                // Centered, so its weave has the same room above it as below.
                y = (worldHeight - realEnemyHeight * BOSS_SPRITE_SCALE) / 2f,
                holdX = xSpawnPosition * BOSS_HOLD_X_FRACTION,
                pixmap = enemyPixmap,
                scale = BOSS_SPRITE_SCALE,
                hitPoints = wave.hitPoints,
                damage = wave.damage,
                gun = CacoImpBrain.SMOULDERING_GUN,
                bar = pinnedBar(),
            ).also { imp ->
                bossBrain = CacoImpBrain(
                    factory.world,
                    imp,
                    frameWidth = xSpawnPosition,
                    frameHeight = worldHeight,
                    random = random,
                    onSummon = ::summonImps,
                    onPhaseChanged = onBossPhaseChanged,
                )
            }

            BossKind.MOTH_QUEEN -> {
                val sheet = requireNotNull(bossPixmap) { "The Moth Queen needs her own sheet" }
                factory.createMothQueen(
                    x = xSpawnPosition.toFloat(),
                    y = (worldHeight - sheet.height / WOUND_ROWS) / 2f,
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
                    bar = pinnedBar(),
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

            BossKind.NAGA -> {
                val sheet = requireNotNull(bossPixmap) { "The Naga needs its own sheet" }
                val ids = factory.createNaga(
                    // Under the water; its brain buries it properly and picks where it first comes up.
                    x = xSpawnPosition * 0.7f,
                    y = worldHeight + NAGA_FRAME.toFloat(),
                    pixmap = sheet,
                    hitPoints = wave.hitPoints,
                    damage = (wave.damage * NAGA_HEAD_DAMAGE).roundToInt().coerceAtLeast(1),
                    bodyDamage = (wave.damage * NAGA_BODY_DAMAGE).roundToInt().coerceAtLeast(1),
                    gun = NagaBrain.GUN,
                    bar = pinnedBar(),
                )
                bossBrain = NagaBrain(
                    factory.world,
                    factory,
                    sheet,
                    headId = ids.first(),
                    parts = ids.drop(1),
                    frameWidth = xSpawnPosition,
                    frameHeight = worldHeight,
                    random = random,
                    onSummon = ::summonBrood,
                    onPhaseChanged = onBossPhaseChanged,
                )
                ids.first()
            }
        }
        bossId = id
        onBossSpawned(id)
    }

    /**
     * Where a boss's health bar is pinned, for the bosses that spend part of their fight out of
     * sight: centered under the stage timer.
     */
    private fun pinnedBar(): Rect = Rect.fromLTWH(
        (xSpawnPosition - BOSS_BAR_WIDTH) / 2f,
        BOSS_BAR_TOP.toFloat(),
        BOSS_BAR_WIDTH.toFloat(),
        BOSS_BAR_HEIGHT.toFloat(),
    )

    /**
     * A swarm the boss has called in: the same wasps as the stage's own, at the strength of the
     * wave that escorted her in - at her own difficulty, see [StageProgression.escortWave] -
     * arriving from the edge in a lane away from the middle: she holds the middle, and a swarm
     * spawned inside her would be a swarm the player never saw arrive.
     *
     * Never with an elite in it, and nor is anything a boss calls up: the fight is the boss's, and
     * a glow in the swarm would pull the player's fire off her.
     */
    private fun summonSwarm() {
        val high = random.nextBoolean()
        val centerY = if (high) GROUP_EDGE_MARGIN else worldHeight - GROUP_EDGE_MARGIN
        spawnSwarm(EnemySpecies.WASP, progression.escortWave(), centerY)
    }

    /**
     * Two of the Caco Imp's own kind, called in ablaze: strikers, whose crimson it wears, at the
     * strength of the wave that escorted it in, one in a lane above it and one below.
     */
    private fun summonImps() {
        val escort = progression.escortWave()
        for (laneY in floatArrayOf(GROUP_EDGE_MARGIN, worldHeight - GROUP_EDGE_MARGIN)) {
            spawn(EnemySpecies.STRIKER, escort, x = xSpawnPosition.toFloat(), laneY = laneY - realEnemyHeight / 2f)
        }
    }

    /**
     * Wyrmlings the Sand Wyrm has called up: its own brood, at the strength of the wave that
     * escorted it in, coming in under the sand like any other - spaced out, so each one's back is
     * its own warning rather than two arriving as one.
     */
    private fun summonWyrmlings(count: Int) {
        val escort = progression.escortWave()
        repeat(count) { spawnBurrowed(EnemySpecies.WYRMLING, escort, x = xSpawnPosition + it * SUMMON_SPACING) }
    }

    /**
     * The Naga's brood, called up out of the sea: [kraits] of its kraits in from the right in lanes a
     * way apart, and with them - enraged - a school of piranhas low over the water, all at the strength
     * of the wave that escorted it in.
     */
    private fun summonBrood(kraits: Int, school: Boolean) {
        val escort = progression.escortWave()
        val lanes = worldHeight - 2f * GROUP_EDGE_MARGIN
        repeat(kraits) {
            val laneY = GROUP_EDGE_MARGIN + lanes * (it + 0.5f) / kraits
            spawn(
                EnemySpecies.KRAIT, escort,
                x = xSpawnPosition + it * SUMMON_SPACING * 0.5f,
                laneY = laneY - realEnemyHeight / 2f,
            )
        }
        if (school) spawnSwarm(EnemySpecies.PIRANHA, escort, worldHeight - GROUP_EDGE_MARGIN)
    }

    private companion object {
        /** The movements that stop somewhere on screen, and so pick where. */
        val HOLDING = setOf(
            EnemyMovementType.HOVER, EnemyMovementType.DIVE, EnemyMovementType.LEAP, EnemyMovementType.LOOP,
        )

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
