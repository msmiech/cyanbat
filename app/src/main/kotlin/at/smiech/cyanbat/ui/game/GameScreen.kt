package at.smiech.cyanbat.ui.game

import android.graphics.Color
import android.os.VibrationEffect
import android.util.Log
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.PREFS_KEY_HIGH_SCORE
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.dataStore
import at.smiech.cyanbat.ecs.BackgroundScrollingSystem
import at.smiech.cyanbat.service.EnemyGenerator
import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.service.ObstacleGenerator
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.Game
import at.smiech.engine.Graphics
import at.smiech.engine.Screen
import at.smiech.engine.ecs.AnimationSystem
import at.smiech.engine.ecs.CollisionSystem
import at.smiech.engine.ecs.EnemyBehaviorSystem
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LifetimeSystem
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.PlayerInputSystem
import at.smiech.engine.ecs.RenderSystem
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameScreen(override val game: Game) : Screen {
    var currentLevel = CyanBatGameActivity.gameAssets.levels[0]
    
    private val world = World()
    private val factory = EntityFactory(world)
    
    private val batId: EntityId
    
    var score: Int = 0
    var highscore: Int = 0
    var tick = TICK_INITIAL
    private var tickTime = 0f
    
    var enmGen = EnemyGenerator(
        xSpawnPosition = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory
    )
    var obsGen = ObstacleGenerator(
        worldWidth = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        factory,
        currentLevel
    )
    
    private lateinit var g: Graphics
    private var levelNameDisplayTime = 3.0f

    init {
        if (DEBUG) {
            Log.d(TAG, "init")
        }
        game.graphics?.let { g = it }

        // Setup Systems
        world.addSystem(PlayerInputSystem(game.frameBufferWidth, game.frameBufferHeight))
        world.addSystem(MovementSystem())
        world.addSystem(BackgroundScrollingSystem(game.frameBufferWidth, factory))
        world.addSystem(EnemyBehaviorSystem())
        world.addSystem(AnimationSystem())
        world.addSystem(CollisionSystem { id1, id2 -> handleCollision(id1, id2) })
        world.addSystem(LifetimeSystem(game.frameBufferWidth))
        world.addSystem(RenderSystem())

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
            height = CyanBatGameActivity.gameAssets.graphics.bat.height.toFloat(),
            pixmap = CyanBatGameActivity.gameAssets.graphics.bat
        )

        currentLevel.music.apply {
            play()
            isLooping = true
        }
        initStats()
    }

    private fun handleCollision(id1: EntityId, id2: EntityId) {
        processDamage(id1)
        processDamage(id2)
        
        // Spawn explosion (at id2)
        val t2 = world.getComponent(id2, TransformComponent::class)
        t2?.let {
            factory.createExplosion(it.rect.left, it.rect.top, 25f, it.rect.height, CyanBatGameActivity.gameAssets.graphics.explosion)
        }
    }

    private fun processDamage(targetId: EntityId) {
        val health = world.getComponent(targetId, HealthComponent::class) ?: return
        
        if (world.hasComponent(targetId, PlayerControlComponent::class)) {
            val control = world.getComponent(targetId, PlayerControlComponent::class)!!
            if (control.hitCooldown <= 0f) {
                health.lives--
                control.hitCooldown = 0.5f // MAX_HIT_COOLDOWN
                
                // Vibrate on hit
                CyanBatGameActivity.gameAssets.vib.vibrate(
                    VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)
                )
                
                if (health.lives <= 0) {
                    health.lives = 0
                    health.alive = false
                    saveHighscore()
                    
                    // Game Over Music logic
                    if (CyanBatGameActivity.soundsEnabled) {
                        CyanBatGameActivity.gameAssets.audio.deathSound.play(100f)
                    }
                    currentLevel.music.apply {
                        stop()
                        isLooping = false
                    }
                    if (CyanBatGameActivity.musicEnabled) {
                        CyanBatGameActivity.gameAssets.audio.gameOverMusic.play()
                    }
                }
            }
        } else {
            health.alive = false
        }
    }

    private fun initStats() {
        score = 0
        readHighscore()
    }

    private fun readHighscore() = GlobalScope.launch(Dispatchers.Main) {
        game.context.dataStore.data.collectLatest {
            highscore = it[PREFS_KEY_HIGH_SCORE] ?: 0
        }
    }

    override fun update(deltaTime: Float) {
        if (levelNameDisplayTime > 0) {
            levelNameDisplayTime -= deltaTime
        }
        
        tickTime += deltaTime
        while (tickTime > tick) {
            tickTime -= tick
            world.update(tick, game.input)
            score++
            
            enmGen.generateEnemy()
            obsGen.generateObstacle()
        }
        
        val health = world.getComponent(batId, HealthComponent::class)!!
        if (!health.alive) {
            val transform = world.getComponent(batId, TransformComponent::class)!!
            if (transform.rect.top > game.frameBufferHeight) {
                game.setScreen(GameOverScreen(game))
            }
        }
    }

    fun saveHighscore() {
        if (score > highscore) {
            highscore = score
        }

        GlobalScope.launch(Dispatchers.Main) {
            game.context.dataStore.edit {
                it[PREFS_KEY_HIGH_SCORE] = highscore
            }
        }
    }

    override fun present(deltaTime: Float) {
        g.clear(Color.BLACK)
        world.draw(g)
        drawStats()
        if (levelNameDisplayTime > 0) {
            drawLevelName()
        }
        
        val health = world.getComponent(batId, HealthComponent::class)!!
        if (!health.alive) {
            g.drawPixmap(CyanBatGameActivity.gameAssets.graphics.death, 15, 15)
        }
    }

    private fun drawStats() {
        val health = world.getComponent(batId, HealthComponent::class)!!
        g.apply {
            drawString("Score: $score", 5, 20, 15, Color.CYAN)
            drawString("Highscore: $highscore", 5, 40, 15, Color.CYAN)
            drawString("Lives: ${health.lives}", 5, 60, 15, Color.CYAN)
        }
    }

    private fun drawLevelName() {
        g.drawString(currentLevel.name, game.frameBufferWidth / 4, game.frameBufferHeight / 2, 30, Color.YELLOW)
    }

    override fun pause() {
        currentLevel.music.apply {
            stop()
            isLooping = false
        }
    }

    override fun resume() {
        initStats()
    }
}
