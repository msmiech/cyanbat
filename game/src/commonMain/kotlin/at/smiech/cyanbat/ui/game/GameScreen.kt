package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.ScoreTracker
import at.smiech.cyanbat.ecs.BackgroundScrollingSystem
import at.smiech.cyanbat.progress.PlayerLoadout
import at.smiech.cyanbat.progress.PlayerProgress
import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.service.EnemyGenerator
import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.service.LevelProgression
import at.smiech.cyanbat.service.ObstacleGenerator
import at.smiech.cyanbat.util.BANNER_CHAR_WIDTH
import at.smiech.cyanbat.util.BANNER_FONT_SIZE
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
import at.smiech.cyanbat.util.LEVEL_COMPLETE_ARMING_SECONDS
import at.smiech.cyanbat.util.PAUSE_DIM
import at.smiech.cyanbat.util.POWER_UP_ARMING_SECONDS
import at.smiech.cyanbat.util.POWER_UP_CARD_GAP
import at.smiech.cyanbat.util.POWER_UP_CARD_HEIGHT
import at.smiech.cyanbat.util.POWER_UP_CARD_TOP
import at.smiech.cyanbat.util.POWER_UP_CARD_WIDTH
import at.smiech.cyanbat.util.RESUME_ARMING_SECONDS
import at.smiech.cyanbat.util.REVIVE_HEALTH_FRACTION
import at.smiech.cyanbat.util.SPREAD_ANGLE_DEGREES
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.TRAIL_SEGMENT_HEIGHT_FRACTION
import at.smiech.cyanbat.util.TRAIL_SEGMENT_WIDTH_FRACTION
import at.smiech.cyanbat.util.WAVE_BANNER_SECONDS
import at.smiech.cyanbat.util.XP_BAR_HEIGHT
import at.smiech.cyanbat.util.XP_PER_BOSS
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.Graphics
import at.smiech.engine.Input.TouchEvent
import at.smiech.engine.Screen
import at.smiech.engine.drawOutlinedString
import at.smiech.engine.ecs.AnimationSystem
import at.smiech.engine.ecs.BounceSystem
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.CollisionSystem
import at.smiech.engine.ecs.DamageComponent
import at.smiech.engine.ecs.EnemyBehaviorSystem
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.FloatingTextSystem
import at.smiech.engine.ecs.HealthBarSystem
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LifetimeSystem
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.PierceComponent
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.PlayerInputSystem
import at.smiech.engine.ecs.RenderSystem
import at.smiech.engine.ecs.TrailSystem
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.WeaponSystem
import at.smiech.engine.ecs.World
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class GameScreen(
    override val game: Game,
    private val env: CyanBatEnvironment,
) : Screen {
    var currentLevel = env.assets.levels[0]
    
    private val world = World()
    private val factory = EntityFactory(world)

    /** Cancelled in [dispose], so nothing started here outlives the screen. */
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val batId: EntityId
    
    private val scoring = ScoreTracker()
    var highscore: Int = 0
    var tick = TICK_INITIAL
    private var tickTime = 0f
    
    /** The difficulty curve of the level being played; see [LevelProgression]. */
    private val progression = LevelProgression.forLevel(currentLevel.id)

    var enmGen = EnemyGenerator(
        xSpawnPosition = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        env.assets.graphics.enemy,
        progression = progression,
        onWaveChanged = { wave -> announce("WAVE ${wave.index + 1}") },
        onBossSpawned = { announce("FINAL BOSS") },
    )
    var obsGen = ObstacleGenerator(
        worldWidth = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        currentLevel
    )

    private lateinit var g: Graphics
    private var levelNameDisplayTime = 3.0f

    /** The wave or boss announcement currently on screen, and what is left of its time. */
    private var bannerText: String? = null
    private var bannerTime = 0f

    /**
     * Set when the level's boss goes down. The run is over, but won rather than lost, so the bat
     * stays where it is and the player reads their score off a screen they earned.
     */
    private var levelComplete = false

    /** Time before the victory overlay will accept a tap as "done"; see [handleLevelCompleteControls]. */
    private var levelCompleteArmingTime = 0f

    /** Experience earned this run, and the levels it has bought. */
    private val progress = PlayerProgress()

    /** What the run's power-ups have made of the bat; see [PlayerLoadout]. */
    private val loadout = PlayerLoadout()

    /**
     * Level ups owed but not yet spent.
     *
     * A count rather than a flag: one kill late in a run can cross two thresholds, and the player
     * is owed a pick for each. They are handed out one dialog at a time.
     */
    private var pendingLevelUps = 0

    /** The power-ups currently on offer, or empty when the run is not waiting on a choice. */
    private var offer: List<PowerUp> = emptyList()

    /** Time before the level up dialog will accept a tap; see [handlePowerUpChoice]. */
    private var offerArmingTime = 0f

    /** Fractional health owed by Regeneration, carried between ticks; see [regenerate]. */
    private var regenCarry = 0f

    /** Set by the player, and by the host backgrounding the app. Cleared only by the player. */
    private var paused = false

    /**
     * Time before a tap counts as "resume", in seconds.
     *
     * Android's back *gesture* is a swipe from the edge, so the pointer events that pause the
     * game are followed by the finger lifting. Without this the game would unpause on the tail of
     * the very gesture that paused it.
     */
    private var resumeArmingTime = 0f

    init {
        game.graphics?.let { g = it }

        // Setup Systems
        world.addSystem(PlayerInputSystem(game.frameBufferWidth, game.frameBufferHeight))
        world.addSystem(MovementSystem())
        // Straight after the movement that carries a shot into an edge, and well before the
        // culling that would remove it there.
        world.addSystem(BounceSystem(game.frameBufferWidth, game.frameBufferHeight))
        world.addSystem(WeaponSystem { shooterId -> fireShot(shooterId) })
        world.addSystem(BackgroundScrollingSystem(game.frameBufferWidth, factory))
        world.addSystem(EnemyBehaviorSystem())
        world.addSystem(AnimationSystem())
        world.addSystem(CollisionSystem { id1, id2 -> handleCollision(id1, id2) })
        world.addSystem(LifetimeSystem(game.frameBufferWidth))
        world.addSystem(RenderSystem())
        // After the sprites: these three draw on top of the run rather than into it. The wake goes
        // first of them, so the bar and the damage numbers stay legible over it.
        world.addSystem(TrailSystem { emitterId -> shedTrail(emitterId) })
        world.addSystem(HealthBarSystem(game.frameBufferHeight))
        world.addSystem(FloatingTextSystem())

        // Add the primary background
        factory.createBackground(
            0f, 0f, 
            game.frameBufferWidth.toFloat(), 
            game.frameBufferHeight.toFloat(), 
            currentLevel.background
        )

        batId = factory.createBat(
            x = (game.frameBufferWidth / 3).toFloat(),
            y = (game.frameBufferHeight / 2).toFloat(),
            width = 45f,
            height = env.assets.graphics.bat.height.toFloat(),
            pixmap = env.assets.graphics.bat,
            // From the loadout rather than the constant, so the bat is built from the same stats
            // the power-ups go on to change and the two can never start out disagreeing.
            shotIntervalSeconds = loadout.shotIntervalSeconds,
        )

        startLevelMusic()
        initStats()
    }

    /**
     * Spawns a shot at the shooter's leading edge, centred on it vertically.
     *
     * Which edge leads depends on who is firing: the bat shoots to the right and the boss back to
     * the left, so each shot leaves from the side it travels towards rather than through the
     * sprite that fired it. A shot carries its shooter's damage, which is how the boss hits harder
     * at range than anything else in the level does on contact.
     */
    private fun fireShot(shooterId: EntityId) {
        val transform = world.getComponent(shooterId, TransformComponent::class) ?: return
        val isPlayer = world.hasComponent(shooterId, PlayerControlComponent::class)
        val shot = env.assets.graphics.shot
        val x = if (isPlayer) transform.rect.right else transform.rect.left - shot.width
        val y = transform.rect.centerY - shot.height / 2f
        // The bat's damage is a run stat the power-ups raise; everything else deals what it was
        // spawned with.
        val damage = if (isPlayer) loadout.shotDamage else damageOf(shooterId)

        for (angle in spreadAngles(if (isPlayer) loadout.extraShots else 0)) {
            factory.createShot(
                x = x,
                y = y,
                width = shot.width.toFloat(),
                height = shot.height.toFloat(),
                pixmap = shot,
                isPlayer = isPlayer,
                damage = damage,
                angleDegrees = angle,
                // Piercing and ricochet are the bat's alone. An enemy shot that came back off a
                // wall would be a hazard the player has no way to read or answer.
                pierce = if (isPlayer) loadout.shotPierce else 0,
                bounce = if (isPlayer) loadout.shotBounce else 0,
            )
        }
    }

    /**
     * The headings of one volley: the straight shot, then [extraShots] fanned alternately below
     * and above it, widening a step at a time.
     *
     * The straight shot is always there, so a spread widens the bat's fire rather than replacing
     * it. Alternating sides rather than filling one first is what keeps the fan balanced: an even
     * number of extras is symmetrical, and an odd one leans by a single shot instead of stacking
     * every extra below the line.
     */
    private fun spreadAngles(extraShots: Int): List<Float> =
        (0..extraShots).map { index ->
            val step = (index + 1) / 2
            val sign = if (index % 2 == 1) 1f else -1f
            if (index == 0) 0f else sign * step * SPREAD_ANGLE_DEGREES
        }

    /**
     * Sheds one segment of the bat's wake, just off the back of it.
     *
     * Centred on the sprite rather than sitting under it: the bat's tail is the middle band of
     * the frame, and a wake off its belly would read as coming from the health bar instead.
     */
    private fun shedTrail(emitterId: EntityId) {
        val rect = world.getComponent(emitterId, TransformComponent::class)?.rect ?: return
        val width = rect.width * TRAIL_SEGMENT_WIDTH_FRACTION
        val height = rect.height * TRAIL_SEGMENT_HEIGHT_FRACTION
        factory.createTrail(
            x = rect.left - width,
            y = rect.centerY - height / 2f,
            width = width,
            height = height,
        )
    }

    private fun handleCollision(id1: EntityId, id2: EntityId) {
        val group1 = collisionGroupOf(id1)
        val group2 = collisionGroupOf(id2)
        // Read up front: killing the boss clears it from the generator, and this pass still has to
        // know which of the two it was looking at.
        val bossId = enmGen.bossId

        // Resolved before anything is hurt: a piercing shot that has already gone through this
        // enemy is not colliding with it any more, however many frames the two spend overlapping.
        // Without this the pair would re-hit every frame, spending the pierce and killing the
        // enemy several times over.
        if (hasAlreadyPierced(id1, id2) || hasAlreadyPierced(id2, id1)) return

        // Both read before either side takes its hit: an entity that dies here still lands the
        // blow it arrived with, and reading afterwards would give the survivor a free pass.
        val damage1 = damageOf(id1)
        val damage2 = damageOf(id2)
        val died1 = damage(id1, damage2, dealtBy = id2)
        val died2 = damage(id2, damage1, dealtBy = id1)

        // A kill scores when the enemy actually dies rather than on every shot that lands: past
        // the opening wave they take more than one.
        if (isEnemyShotDown(group1, group2)) {
            val enemyDied = if (group1 == CollisionGroup.ENEMY) died1 else died2
            if (enemyDied) {
                scoring.registerEnemyDestroyed()
                // The boss is banked by completeLevel, which knows it was the boss. Everything
                // else is worth what its wave is worth.
                val enemyId = if (group1 == CollisionGroup.ENEMY) id1 else id2
                if (enemyId != bossId) {
                    awardExperience(PlayerProgress.experienceForKill(enmGen.currentWave.index))
                }
            }
        }
    }

    /**
     * Banks [amount] of experience and queues a power-up pick for every level it bought.
     *
     * Queued rather than shown, because this runs from inside a world update: the dialog goes up
     * on the next frame, once the tick that earned it has finished resolving.
     */
    private fun awardExperience(amount: Int) {
        pendingLevelUps += progress.award((amount * loadout.experienceMultiplier).roundToInt())
    }

    /**
     * True when this pair is one of the player's shots meeting an enemy, in either order - the
     * collision system reports pairs by entity id, not by role.
     *
     * Obstacles deliberately do not count: they are scenery a shot happens to clear, not a kill.
     */
    private fun isEnemyShotDown(group1: CollisionGroup?, group2: CollisionGroup?): Boolean =
        setOf(group1, group2) == setOf(CollisionGroup.PLAYER_PROJECTILE, CollisionGroup.ENEMY)

    private fun collisionGroupOf(id: EntityId): CollisionGroup? =
        world.getComponent(id, CollisionComponent::class)?.group

    /**
     * What [id] takes off whatever it runs into.
     *
     * Anything spawned by a wave carries its own [DamageComponent]; the fallback is for the
     * entities whose damage never varies - the bat itself, and the obstacles bolted to the cave.
     */
    private fun damageOf(id: EntityId): Int =
        world.getComponent(id, DamageComponent::class)?.amount ?: DAMAGE_PER_HIT

    /**
     * True when [shotId] is a piercing shot that has already passed through [targetId], and false
     * for everything else - including the first frame of a pierce, which it records on the way.
     */
    private fun hasAlreadyPierced(shotId: EntityId, targetId: EntityId): Boolean {
        val pierce = world.getComponent(shotId, PierceComponent::class) ?: return false
        if (collisionGroupOf(targetId) != CollisionGroup.ENEMY) return false
        return pierce.meet(targetId)
    }

    /**
     * Hurts [id] for [amount], shows what it cost over an enemy that took the hit, and blows up
     * whatever the hit destroyed.
     *
     * Only enemies get a number. An obstacle is scenery being cleared rather than a target, and
     * what the bat itself has lost is already there to read on its health bar.
     *
     * @param dealtBy the entity on the other side of the collision, which is what decides whether
     *   a piercing shot spends a pierce here or is spent itself.
     * @return true if this hit is what killed it.
     */
    private fun damage(id: EntityId, amount: Int, dealtBy: EntityId): Boolean {
        // A shot with pierce left goes through rather than being stopped. Only enemies count:
        // scenery is what a shot is stopped by however sharp it has been made.
        val pierce = world.getComponent(id, PierceComponent::class)
        if (pierce != null && collisionGroupOf(dealtBy) == CollisionGroup.ENEMY && pierce.spend()) {
            return false
        }

        val dealt = applyDamage(id, amount)
        if (dealt > 0 && collisionGroupOf(id) == CollisionGroup.ENEMY) {
            showDamageText(id, dealt)
        }

        val died = dealt > 0 && world.getComponent(id, HealthComponent::class)?.alive == false
        // Only for the things a player watches die. A blast on every shot that merely lands would
        // bury a tough enemy behind its own hit effects.
        if (died && collisionGroupOf(id) in EXPLODES_ON_DEATH) explode(id)
        return died
    }

    /** A blast the size of whatever just died, so the boss goes out bigger than its escort. */
    private fun explode(id: EntityId) {
        val rect = world.getComponent(id, TransformComponent::class)?.rect ?: return
        val explosion = env.assets.graphics.explosion
        factory.createExplosion(
            centerX = rect.centerX,
            centerY = rect.centerY,
            pixmap = explosion,
            // Never smaller than the artwork was drawn: an ordinary enemy keeps the blast it
            // always had, and only something bigger than one scales the blast up.
            scale = (rect.height / explosion.height).coerceAtLeast(1f),
        )
    }

    /**
     * Takes [amount] off [targetId]'s health and kills it at zero, returning what actually landed.
     *
     * Nothing lands on something with no health to lose, on something already dead, or on a player
     * still inside the cooldown that follows their last hit.
     */
    private fun applyDamage(targetId: EntityId, amount: Int): Int {
        val health = world.getComponent(targetId, HealthComponent::class) ?: return 0
        if (!health.alive) return 0

        var incoming = amount

        // The bat is the only entity with a cooldown: without one a single obstacle would strip the
        // whole bar over the frames the two sprites spend overlapping. How long that cooldown runs
        // and how much of the hit gets through are both run stats the power-ups raise.
        val control = world.getComponent(targetId, PlayerControlComponent::class)
        if (control != null) {
            if (control.hitCooldown > 0f) return 0
            control.hitCooldown = loadout.hitCooldownSeconds
            scoring.registerPlayerHit()

            // The flat cut comes off first and the armour scales what survives it, so the two
            // stack the way a player would expect rather than one swallowing the other. Rounded up
            // and floored at one: no amount of either can make a hit free, which would leave a run
            // the player cannot lose.
            incoming = ((incoming - loadout.flatDamageReduction) * loadout.damageTaken)
                .roundToInt()
                .coerceAtLeast(1)

            // Vibrate on hit
            env.haptics.vibrate(HIT_VIBRATION_MILLIS)
        }

        // Capped at what is left, so an overkill reports the damage the target could actually take.
        val dealt = minOf(incoming, health.hitPoints)
        health.hitPoints -= dealt
        if (health.hitPoints <= 0) {
            // The damage still landed and is still reported: a revive is the bat surviving a blow
            // that would have killed it, not the blow never happening.
            if (control != null && revive(health)) return dealt

            health.hitPoints = 0
            health.alive = false
            if (control != null) endRun()
            if (targetId == enmGen.bossId) completeLevel()
        }
        return dealt
    }

    /**
     * Spends a Second Life, if the run has one, putting the bat back on its feet at half a bar.
     *
     * Half rather than full because a free death should keep a run alive, not undo the damage that
     * ended it - and the mercy window is reset alongside, or the same enemy would take the new
     * health off before the player's hand had moved.
     */
    private fun revive(health: HealthComponent): Boolean {
        if (!loadout.useRevive()) return false

        health.hitPoints = (health.maxHitPoints * REVIVE_HEALTH_FRACTION).roundToInt().coerceAtLeast(1)
        world.getComponent(batId, PlayerControlComponent::class)?.hitCooldown = loadout.hitCooldownSeconds
        announce("SECOND LIFE")
        return true
    }

    /**
     * The level's boss is down, so the level is over.
     *
     * The run stops here rather than rolling on into a sixth minute of enemies: a boss that could
     * be beaten and then followed by more of the same would not be a boss. What is left on screen
     * is left alone - anything still in flight flies out on its own - and the player reads their
     * total off the overlay and taps out when they are ready.
     */
    private fun completeLevel() {
        if (levelComplete) return
        levelComplete = true
        levelCompleteArmingTime = LEVEL_COMPLETE_ARMING_SECONDS
        enmGen.clearBoss()
        scoring.awardLevelCleared()
        // Banked even though the run ends here: the total is what the victory screen reports, and
        // a boss worth nothing would read as a boss that did not count.
        awardExperience(XP_PER_BOSS)
        saveHighscore()

        currentLevel.music.apply {
            stop()
            isLooping = false
        }
    }

    /** Puts [text] up over the run for [WAVE_BANNER_SECONDS], replacing whatever was there. */
    private fun announce(text: String) {
        bannerText = text
        bannerTime = WAVE_BANNER_SECONDS
    }

    /** Banks the score and hands playback over to the game over track. */
    private fun endRun() {
        saveHighscore()

        if (env.audioSettings.soundsEnabled) {
            env.assets.audio.deathSound.play(100f)
        }
        currentLevel.music.apply {
            stop()
            isLooping = false
        }
        if (env.audioSettings.musicEnabled) {
            env.assets.audio.gameOverMusic.play()
        }
    }

    /** Puts the number at the enemy's leading edge, which is the side the bat's shots arrive from. */
    private fun showDamageText(enemyId: EntityId, damage: Int) {
        val rect = world.getComponent(enemyId, TransformComponent::class)?.rect ?: return
        factory.createDamageText(rect.left, rect.centerY, damage)
    }

    private fun initStats() {
        scoring.reset()
        readHighscore()
    }

    // A one-shot read: nothing outside this screen writes the highscore during a run, so there is
    // nothing to keep observing. Stays on the main thread, which is the only thread that touches
    // `highscore`.
    private fun readHighscore() = screenScope.launch {
        highscore = env.highscores.read()
    }

    override fun update(deltaTime: Float) {
        // Ahead of the pause controls, which would otherwise read the player's way out of a won
        // level as a request to pause it.
        if (levelComplete) {
            handleLevelCompleteControls(deltaTime)
            return
        }
        if (handlePauseControls(deltaTime)) return

        // A level up owed is a level up shown, before anything else moves: the pick is meant to
        // change the fight the player is in, not the one after it.
        if (offer.isEmpty() && pendingLevelUps > 0) openLevelUpOffer()
        if (offer.isNotEmpty()) {
            handlePowerUpChoice(deltaTime)
            return
        }

        if (levelNameDisplayTime > 0) {
            levelNameDisplayTime -= deltaTime
        }
        if (bannerTime > 0) {
            bannerTime -= deltaTime
        }

        tickTime += deltaTime
        while (tickTime > tick) {
            tickTime -= tick
            world.update(tick, game.input)
            scoring.awardSurvivalTick()
            regenerate(tick)

            // The level clock is the fixed tick, not the wall clock: a paused game is a paused
            // level, and a slow frame costs the player no ground on the wave they are in.
            enmGen.update(tick)
            // Held back for the boss. The duel is fought in an open cave, because a boss pinning
            // the player against scenery they cannot outrun is a death with nothing to read in it.
            if (!enmGen.bossSpawned) obsGen.generateObstacle()
        }

        val health = world.getComponent(batId, HealthComponent::class)!!
        if (!health.alive) {
            val transform = world.getComponent(batId, TransformComponent::class)!!
            if (transform.rect.top > game.frameBufferHeight) {
                game.setScreen(GameOverScreen(game, env))
            }
        }
    }

    /** Draws the next owed level up, freezing the run until the player has taken something. */
    private fun openLevelUpOffer() {
        pendingLevelUps--
        offer = PowerUp.offer(loadout)
        offerArmingTime = POWER_UP_ARMING_SECONDS

        // A dialog with nothing to choose between would trap the run. Two of the power-ups scale
        // without a ceiling so this cannot happen, but a future one that forgets to say so would
        // otherwise lock the game rather than fail loudly.
        if (offer.isEmpty()) return

        if (currentLevel.music.isPlaying) currentLevel.music.pause()
    }

    /**
     * Reads a pick off the level up dialog: a tap on a card, or its number key.
     *
     * Armed on a delay for the same reason the pause overlay is - the player was steering with a
     * finger down when the level up landed, and the lift that follows is not a choice. There is no
     * way to dismiss this without picking: the pick is the reward, and a dialog that could be
     * waved away would just be a tax on the player who did not read it in time.
     */
    private fun handlePowerUpChoice(deltaTime: Float) {
        offerArmingTime -= deltaTime

        val input = game.input
        val controls = input?.controls
        // Consumed whether or not they can act yet, so the buffer does not hand the whole backlog
        // to the run the moment the dialog closes.
        val touches = input?.touchEvents.orEmpty().filter { it.type == TouchEvent.TOUCH_UP }
        // CONFIRM picks the leftmost card: a game pad has no number keys, and its A button is the
        // only thing on it a player will reach for first.
        val confirmed = controls?.consumePress(GameButton.CONFIRM) == true
        val pressed = GameButton.CHOICES.map { controls?.consumePress(it) == true }

        if (offerArmingTime > 0f) return

        val byKey = pressed.indexOfFirst { it }.takeIf { it >= 0 }
            ?: 0.takeIf { confirmed }
        val chosen = byKey ?: touches.firstNotNullOfOrNull { cardIndexAt(it.x, it.y) }

        if (chosen != null && chosen in offer.indices) choosePowerUp(offer[chosen])
    }

    /** Which card covers ([x], [y]) in framebuffer pixels, or null for a tap that missed. */
    private fun cardIndexAt(x: Int, y: Int): Int? {
        if (y < POWER_UP_CARD_TOP || y > POWER_UP_CARD_TOP + POWER_UP_CARD_HEIGHT) return null
        return offer.indices.firstOrNull { index ->
            val left = cardLeft(index)
            x >= left && x <= left + POWER_UP_CARD_WIDTH
        }
    }

    /** The left edge of card [index], with the row of them centred on the framebuffer. */
    private fun cardLeft(index: Int): Int {
        val stride = POWER_UP_CARD_WIDTH + POWER_UP_CARD_GAP
        val rowWidth = offer.size * stride - POWER_UP_CARD_GAP
        return (game.frameBufferWidth - rowWidth) / 2 + index * stride
    }

    /** Takes the pick, makes the bat match, and hands the run back. */
    private fun choosePowerUp(powerUp: PowerUp) {
        powerUp.applyTo(loadout)
        applyLoadout()
        offer = emptyList()
        announce(powerUp.title)

        // Only if the bat is still flying: a level up banked by the shot that also killed the
        // player has no run left to go back to.
        val health = world.getComponent(batId, HealthComponent::class)
        if (health?.alive == true) startLevelMusic()
    }

    /**
     * Pushes the run's earned stats onto the bat's components.
     *
     * One place rather than each power-up reaching into the world for itself: a power-up says what
     * the bat is now, and this is what makes it so.
     */
    private fun applyLoadout() {
        world.getComponent(batId, WeaponComponent::class)?.interval = loadout.shotIntervalSeconds
        scoring.bonusMultiplier = loadout.scoreMultiplier

        val health = world.getComponent(batId, HealthComponent::class) ?: return
        health.maxHitPoints = loadout.maxHitPoints
        // Capped at the bar rather than added blindly, so healing a nearly full bat is worth
        // whatever room is actually left in it.
        val heal = loadout.takePendingHeal()
        if (heal > 0) health.hitPoints = (health.hitPoints + heal).coerceAtMost(health.maxHitPoints)
    }

    /**
     * Puts back whatever Regeneration is owed this tick.
     *
     * Fractional health is carried rather than rounded, for the same reason the score bonus carries
     * its remainder: two health a second is well under a point per tick, and rounding each tick
     * would heal nothing at all. Only a living bat regenerates, and only up to its own bar.
     */
    private fun regenerate(deltaTime: Float) {
        if (loadout.healthRegenPerSecond <= 0f) return
        val health = world.getComponent(batId, HealthComponent::class) ?: return
        if (!health.alive || health.hitPoints >= health.maxHitPoints) {
            // Dropped rather than banked: health owed while the bar is already full would
            // otherwise pour out in one lump the instant the bat took its next hit.
            regenCarry = 0f
            return
        }

        regenCarry += loadout.healthRegenPerSecond * deltaTime
        val whole = regenCarry.toInt()
        if (whole <= 0) return
        regenCarry -= whole
        health.hitPoints = (health.hitPoints + whole).coerceAtMost(health.maxHitPoints)
    }

    /**
     * The level is won; a tap or a button press takes the player back out to the menu.
     *
     * Armed on a delay for the same reason the pause overlay is: the player was steering with a
     * finger down as the boss died, and the lift that follows is not them asking to leave.
     */
    private fun handleLevelCompleteControls(deltaTime: Float) {
        levelCompleteArmingTime -= deltaTime

        val input = game.input
        // Read whether or not it can act on them, so the buffer does not hoard events.
        val tapped = input?.touchEvents?.any { it.type == TouchEvent.TOUCH_UP } == true
        val controls = input?.controls
        val confirmed = controls?.consumePress(GameButton.CONFIRM) == true
        val backed = controls?.consumePress(GameButton.BACK) == true

        if ((tapped || confirmed || backed) && levelCompleteArmingTime <= 0f) env.onExitToMenu()
    }

    /**
     * Reads the pause controls and, while paused, holds the run still.
     *
     * @return true when the caller should skip this update entirely.
     */
    private fun handlePauseControls(deltaTime: Float): Boolean {
        val input = game.input
        val controls = input?.controls

        // Back pauses a running game and leaves a paused one. That second meaning is what makes
        // the Android back button safe to intercept: it still gets the player out, in two presses
        // rather than one, without a button the touch UI does not have.
        if (controls?.consumePress(GameButton.BACK) == true) {
            if (paused) {
                saveHighscore()
                env.onExitToMenu()
                return true
            }
            setPaused(true)
        }
        if (controls?.consumePress(GameButton.PAUSE) == true) {
            setPaused(!paused)
        }

        if (!paused) return false

        resumeArmingTime -= deltaTime
        // Read even when it cannot resume: the buffer is drained by reading it, and a pause spent
        // hoarding events would dump them all on the bat at once on the way back in.
        val tapped = input?.touchEvents?.any { it.type == TouchEvent.TOUCH_UP } == true
        if (tapped && resumeArmingTime <= 0f) setPaused(false)
        return true
    }

    private fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        if (value) {
            resumeArmingTime = RESUME_ARMING_SECONDS
            if (currentLevel.music.isPlaying) currentLevel.music.pause()
        } else {
            // Only the level theme: once the bat is dead the game over track owns playback, and
            // resuming would put two tracks on top of each other.
            val health = world.getComponent(batId, HealthComponent::class)
            if (health?.alive == true) startLevelMusic()
        }
    }

    private fun drawPauseOverlay() {
        g.apply {
            drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
            // No text measurement in the Graphics API, so these x offsets are eyeballed against
            // the 480px framebuffer rather than centred properly.
            drawString("PAUSED", 186, 140, 30, EngineColors.CYAN)
            drawString("Tap or press Esc to resume", 155, 175, 15, EngineColors.WHITE)
            drawString("Back or Q to quit", 185, 197, 15, EngineColors.WHITE)
        }
    }

    fun saveHighscore() {
        if (scoring.score > highscore) {
            highscore = scoring.score
        }

        // Deliberately not on screenScope: this runs as the bat dies, moments before the screen is
        // swapped out and disposed, and the write has to survive that.
        env.highscores.saveAsync(highscore)
    }

    /**
     * The banner a new wave or the boss arrives on, held for [WAVE_BANNER_SECONDS].
     *
     * Outlined rather than plain, because it lands over whatever the run happens to be drawing,
     * and centred by eye against the 480px framebuffer like the rest of the overlays here - the
     * Graphics API has no way to measure a string.
     */
    private fun drawBanner(text: String) {
        g.drawOutlinedString(
            text,
            game.frameBufferWidth / 2 - text.length * BANNER_CHAR_WIDTH / 2,
            game.frameBufferHeight / 3,
            BANNER_FONT_SIZE,
            EngineColors.YELLOW,
        )
    }

    /**
     * What the player gets for clearing the level: the run's total, and the way out.
     *
     * Drawn over the level rather than on a screen of its own, so the last thing they see is the
     * cave they beat with the wreckage of the boss still clearing off it.
     */
    private fun drawLevelCompleteOverlay() {
        g.apply {
            drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
            drawString("LEVEL COMPLETE", 120, 130, 30, EngineColors.YELLOW)
            drawString(currentLevel.name, 150, 160, 15, EngineColors.CYAN)
            drawString("Score: ${scoring.score}", 175, 185, 20, EngineColors.WHITE)
            drawString("Tap or press Enter to continue", 140, 215, 15, EngineColors.WHITE)
        }
    }

    /**
     * The level up dialog: what the bat just reached, and the three things it can become.
     *
     * Cards are drawn rather than composed, because this screen owns a 480x320 framebuffer and has
     * no text measurement to lay anything out with - every offset here is eyeballed against that
     * frame, as the other overlays are. A card is its own tap target, and carries no number: what
     * it does is the whole of what the player needs to read. The number keys still pick by
     * position for anyone on a keyboard, which is a shortcut rather than the advertised way in.
     */
    private fun drawPowerUpOffer() {
        g.apply {
            drawRect(0, 0, game.frameBufferWidth, game.frameBufferHeight, PAUSE_DIM)
            drawString("LEVEL ${progress.level}", 190, 70, 30, EngineColors.YELLOW)
            drawString("Choose an upgrade", 168, 100, 15, EngineColors.WHITE)

            offer.forEachIndexed { index, powerUp ->
                drawPowerUpCard(index, powerUp)
            }
        }
    }

    private fun drawPowerUpCard(index: Int, powerUp: PowerUp) {
        val left = cardLeft(index)
        g.apply {
            // A filled panel behind the text, then a cyan lip along the top, so a card reads as a
            // thing to press rather than as words floating over the run.
            drawRect(left, POWER_UP_CARD_TOP, POWER_UP_CARD_WIDTH, POWER_UP_CARD_HEIGHT, CARD_FILL)
            drawRect(left, POWER_UP_CARD_TOP, POWER_UP_CARD_WIDTH, 2, EngineColors.CYAN)

            drawString(powerUp.title, left + 8, POWER_UP_CARD_TOP + 26, 14, EngineColors.CYAN)
            // Wrapped by hand for the same reason the layout is eyeballed: nothing here can
            // measure a string, so the description is broken on whole words at a fixed width.
            wrapped(powerUp.description, CARD_TEXT_CHARS).forEachIndexed { line, text ->
                drawString(text, left + 8, POWER_UP_CARD_TOP + 48 + line * 14, 11, EngineColors.WHITE)
            }
        }
    }

    /** Greedy word wrap at [chars] per line, which is all the card layout needs. */
    private fun wrapped(text: String, chars: Int): List<String> {
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > chars) {
                lines += line.toString()
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    /**
     * The experience bar, across the very top edge.
     *
     * Up there because it is the one strip of the frame nothing else uses - the HUD text starts
     * below it and the bat never reaches it - and because a bar the player reads out of the corner
     * of their eye is the point: it says how close the next choice is without asking to be looked
     * at.
     */
    private fun drawExperienceBar() {
        val filled = (game.frameBufferWidth * progress.fraction).roundToInt()
        g.drawRect(0, 0, game.frameBufferWidth, XP_BAR_HEIGHT, XP_BAR_EMPTY)
        if (filled > 0) g.drawRect(0, 0, filled, XP_BAR_HEIGHT, EngineColors.CYAN)
    }

    override fun present(deltaTime: Float) {
        g.clear(EngineColors.BLACK)
        world.draw(g)
        drawStats()
        drawExperienceBar()
        if (levelNameDisplayTime > 0) {
            drawLevelName()
        }
        bannerText?.takeIf { bannerTime > 0 }?.let { drawBanner(it) }

        val health = world.getComponent(batId, HealthComponent::class)!!
        if (!health.alive) {
            g.drawPixmap(env.assets.graphics.death, 15, 15)
        }

        if (offer.isNotEmpty()) drawPowerUpOffer()
        if (levelComplete) drawLevelCompleteOverlay()
        if (paused) drawPauseOverlay()
    }

    /**
     * The text HUD. Health is deliberately not part of it: it rides under the bat, where the
     * player is already looking.
     */
    private fun drawStats() {
        g.apply {
            drawString("Score: ${scoring.score}", 5, 20, 15, EngineColors.CYAN)
            drawString("Highscore: $highscore", 5, 40, 15, EngineColors.CYAN)
            // Always shown, even at x1: a multiplier the player only sees once they have
            // earned it is a mechanic they never learn exists.
            val multiplier = scoring.multiplier
            val comboColor = if (multiplier > 1) EngineColors.YELLOW else EngineColors.CYAN
            drawString("Combo: x$multiplier", 5, 60, 15, comboColor)
            // How far into the level the player is, which is the only reading they get on how
            // much harder the next minute is about to be - and on how close the boss is.
            drawString(waveLabel(), 5, 80, 15, EngineColors.CYAN)
            // The other half of that race: how much stronger the bat has got while the cave was
            // getting harder. The bar across the top edge is the fine detail; this is the count.
            drawString("Level: ${progress.level}", 5, 100, 15, EngineColors.YELLOW)
        }
    }

    /**
     * The wave readout: which minute the player is in, or that the boss is here.
     *
     * Waves are numbered from one for the player, where the code indexes them from zero.
     */
    private fun waveLabel(): String = when {
        enmGen.bossSpawned -> "BOSS"
        else -> "Wave: ${enmGen.currentWave.index + 1}/${progression.bossWave}"
    }

    private fun drawLevelName() {
        g.drawString(currentLevel.name, game.frameBufferWidth / 4, game.frameBufferHeight / 2, 30, EngineColors.YELLOW)
    }

    /** Starts or resumes the level theme, if music is enabled. */
    private fun startLevelMusic() {
        if (!env.audioSettings.musicEnabled) return
        currentLevel.music.apply {
            isLooping = true
            play()
        }
    }

    /**
     * The host going away pauses the run outright, not just its music. The player is not at the
     * controls, and a game that carries on the moment the window comes back costs them a life
     * before they have looked at it.
     */
    override fun pause() {
        setPaused(true)
    }

    /**
     * Deliberately does not clear [paused]: coming back to the app should not drop the player
     * straight into a dodge. They resume when they are ready.
     */
    override fun resume() {
        // The run survives untouched - only playback needs restoring, and only if the player had
        // not paused by hand. After death the game over music owns playback, so leave it alone.
        if (paused) return
        val health = world.getComponent(batId, HealthComponent::class)
        if (health?.alive == true) {
            startLevelMusic()
        }
    }

    override fun dispose() {
        screenScope.cancel()
    }

    private companion object {
        /**
         * What leaves a blast behind when it dies. Shots are left off deliberately - one per
         * bullet would put an explosion on the screen every second the bat is firing - and so is
         * the bat, whose death already has its own artwork.
         */
        val EXPLODES_ON_DEATH = setOf(CollisionGroup.ENEMY, CollisionGroup.OBSTACLE)

        /** A power-up card's panel: dark enough to read white text on, over a dimmed run. */
        const val CARD_FILL = 0xE6101820.toInt()

        /** Roughly what fits on a card at 11px, counted rather than measured; see [wrapped]. */
        const val CARD_TEXT_CHARS = 22

        /** The unfilled part of the experience bar. */
        const val XP_BAR_EMPTY = 0x80000000.toInt()
    }
}
