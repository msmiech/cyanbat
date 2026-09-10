package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.ScoreTracker
import at.smiech.cyanbat.ecs.BackgroundScrollingSystem
import at.smiech.cyanbat.service.EnemyGenerator
import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.service.ObstacleGenerator
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.HIT_VIBRATION_MILLIS
import at.smiech.cyanbat.util.PAUSE_DIM
import at.smiech.cyanbat.util.RESUME_ARMING_SECONDS
import at.smiech.cyanbat.util.SHOT_INTERVAL_SECONDS
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.EngineColors
import at.smiech.engine.Game
import at.smiech.engine.GameButton
import at.smiech.engine.Graphics
import at.smiech.engine.Input.TouchEvent
import at.smiech.engine.Screen
import at.smiech.engine.ecs.AnimationSystem
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.CollisionSystem
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
    
    var enmGen = EnemyGenerator(
        xSpawnPosition = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        env.assets.graphics.enemy,
    )
    var obsGen = ObstacleGenerator(
        worldWidth = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        currentLevel
    )
    
    private lateinit var g: Graphics
    private var levelNameDisplayTime = 3.0f

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
        // After the sprites: both draw on top of the run rather than into it.
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

    /** Spawns a shot at the shooter's leading edge, centred on it vertically. */
    private fun fireShot(shooterId: EntityId) {
        val transform = world.getComponent(shooterId, TransformComponent::class) ?: return
        val shot = env.assets.graphics.shot
        factory.createShot(
            x = transform.rect.right,
            y = transform.rect.centerY - shot.height / 2f,
            width = shot.width.toFloat(),
            height = shot.height.toFloat(),
            pixmap = shot,
            isPlayer = true,
        )
    }

    private fun handleCollision(id1: EntityId, id2: EntityId) {
        // Checked before the damage is applied, while both entities still have their components.
        if (isEnemyShotDown(id1, id2)) {
            scoring.registerEnemyDestroyed()
        }

        damage(id1)
        damage(id2)
        
        // Spawn explosion (at id2)
        val t2 = world.getComponent(id2, TransformComponent::class)
        t2?.let {
            factory.createExplosion(it.rect.left, it.rect.top, 25f, it.rect.height, env.assets.graphics.explosion)
        }
    }

    /**
     * True when this pair is one of the player's shots meeting an enemy, in either order - the
     * collision system reports pairs by entity id, not by role.
     *
     * Obstacles deliberately do not count: they are scenery a shot happens to clear, not a kill.
     */
    private fun isEnemyShotDown(id1: EntityId, id2: EntityId): Boolean {
        val groups = setOf(collisionGroupOf(id1), collisionGroupOf(id2))
        return groups == setOf(CollisionGroup.PLAYER_PROJECTILE, CollisionGroup.ENEMY)
    }

    private fun collisionGroupOf(id: EntityId): CollisionGroup? =
        world.getComponent(id, CollisionComponent::class)?.group

    /**
     * Hurts [id], and shows what it cost over an enemy that took the hit.
     *
     * Only enemies get a number. An obstacle is scenery being cleared rather than a target, and
     * what the bat itself has lost is already there to read on its health bar.
     */
    private fun damage(id: EntityId) {
        val dealt = applyDamage(id)
        if (dealt > 0 && collisionGroupOf(id) == CollisionGroup.ENEMY) {
            showDamageText(id, dealt)
        }
    }

    /**
     * Takes one hit's [DAMAGE_PER_HIT] off [targetId]'s health and kills it at zero, returning what
     * actually landed.
     *
     * Nothing lands on something with no health to lose, on something already dead, or on a player
     * still inside the cooldown that follows their last hit.
     */
    private fun applyDamage(targetId: EntityId): Int {
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
        val dealt = minOf(DAMAGE_PER_HIT, health.hitPoints)
        health.hitPoints -= dealt
        if (health.hitPoints <= 0) {
            health.hitPoints = 0
            health.alive = false
            if (control != null) endRun()
        }
        return dealt
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
        if (handlePauseControls(deltaTime)) return

        if (levelNameDisplayTime > 0) {
            levelNameDisplayTime -= deltaTime
        }

        tickTime += deltaTime
        while (tickTime > tick) {
            tickTime -= tick
            world.update(tick, game.input)
            scoring.awardSurvivalTick()
            
            enmGen.generateEnemy()
            obsGen.generateObstacle()
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

    override fun present(deltaTime: Float) {
        g.clear(EngineColors.BLACK)
        world.draw(g)
        drawStats()
        if (levelNameDisplayTime > 0) {
            drawLevelName()
        }
        
        val health = world.getComponent(batId, HealthComponent::class)!!
        if (!health.alive) {
            g.drawPixmap(env.assets.graphics.death, 15, 15)
        }

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
        }
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
}
