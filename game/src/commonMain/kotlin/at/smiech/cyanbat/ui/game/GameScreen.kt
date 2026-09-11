package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.ScoreTracker
import at.smiech.cyanbat.ecs.BackgroundScrollingSystem
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
import at.smiech.cyanbat.util.RESUME_ARMING_SECONDS
import at.smiech.cyanbat.util.SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.TRAIL_SEGMENT_HEIGHT_FRACTION
import at.smiech.cyanbat.util.TRAIL_SEGMENT_WIDTH_FRACTION
import at.smiech.cyanbat.util.WAVE_BANNER_SECONDS
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.Graphics
import at.smiech.engine.Input.TouchEvent
import at.smiech.engine.Screen
import at.smiech.engine.drawOutlinedString
import at.smiech.engine.ecs.AnimationSystem
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
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.PlayerInputSystem
import at.smiech.engine.ecs.RenderSystem
import at.smiech.engine.ecs.TrailSystem
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponSystem
import at.smiech.engine.ecs.World
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
            shotIntervalSeconds = SHOT_INTERVAL_SECONDS,
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
        factory.createShot(
            x = if (isPlayer) transform.rect.right else transform.rect.left - shot.width,
            y = transform.rect.centerY - shot.height / 2f,
            width = shot.width.toFloat(),
            height = shot.height.toFloat(),
            pixmap = shot,
            isPlayer = isPlayer,
            damage = damageOf(shooterId),
        )
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

        // Both read before either side takes its hit: an entity that dies here still lands the
        // blow it arrived with, and reading afterwards would give the survivor a free pass.
        val damage1 = damageOf(id1)
        val damage2 = damageOf(id2)
        val died1 = damage(id1, damage2)
        val died2 = damage(id2, damage1)

        // A kill scores when the enemy actually dies rather than on every shot that lands: past
        // the opening wave they take more than one.
        if (isEnemyShotDown(group1, group2)) {
            val enemyDied = if (group1 == CollisionGroup.ENEMY) died1 else died2
            if (enemyDied) scoring.registerEnemyDestroyed()
        }
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
     * Hurts [id] for [amount], shows what it cost over an enemy that took the hit, and blows up
     * whatever the hit destroyed.
     *
     * Only enemies get a number. An obstacle is scenery being cleared rather than a target, and
     * what the bat itself has lost is already there to read on its health bar.
     *
     * @return true if this hit is what killed it.
     */
    private fun damage(id: EntityId, amount: Int): Boolean {
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

        // The bat is the only entity with a cooldown: without one a single obstacle would strip the
        // whole bar over the frames the two sprites spend overlapping.
        val control = world.getComponent(targetId, PlayerControlComponent::class)
        if (control != null) {
            if (control.hitCooldown > 0f) return 0
            control.hitCooldown = 0.5f // MAX_HIT_COOLDOWN
            scoring.registerPlayerHit()

            // Vibrate on hit
            env.haptics.vibrate(HIT_VIBRATION_MILLIS)
        }

        // Capped at what is left, so an overkill reports the damage the target could actually take.
        val dealt = minOf(amount, health.hitPoints)
        health.hitPoints -= dealt
        if (health.hitPoints <= 0) {
            health.hitPoints = 0
            health.alive = false
            if (control != null) endRun()
            if (targetId == enmGen.bossId) completeLevel()
        }
        return dealt
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

    override fun present(deltaTime: Float) {
        g.clear(EngineColors.BLACK)
        world.draw(g)
        drawStats()
        if (levelNameDisplayTime > 0) {
            drawLevelName()
        }
        bannerText?.takeIf { bannerTime > 0 }?.let { drawBanner(it) }

        val health = world.getComponent(batId, HealthComponent::class)!!
        if (!health.alive) {
            g.drawPixmap(env.assets.graphics.death, 15, 15)
        }

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
    }
}
