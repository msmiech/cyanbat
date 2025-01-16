package at.smiech.cyanbat.ui.game

import android.graphics.Color
import android.util.Log
import androidx.datastore.preferences.core.edit
import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Screen
import at.smiech.cyanbat.PREFS_KEY_HIGH_SCORE
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.dataStore
import at.smiech.cyanbat.gameobject.GameObject
import at.smiech.cyanbat.gameobject.impl.Background
import at.smiech.cyanbat.gameobject.impl.CyanBat
import at.smiech.cyanbat.gameobject.impl.Shot
import at.smiech.cyanbat.service.CollisionDetector
import at.smiech.cyanbat.service.EnemyGenerator
import at.smiech.cyanbat.service.ObstacleGenerator
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG
import at.smiech.cyanbat.util.TICK_INITIAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class GameScreen(override val game: Game) : Screen {
    val gameObjects = CopyOnWriteArrayList<GameObject>()
    private val bat = CyanBat(
        x = game.frameBufferWidth / 3,
        y = game.frameBufferHeight / 2,
        height = CyanBatGameActivity.gameAssets.graphics.bat.height,
        pixmap = CyanBatGameActivity.gameAssets.graphics.bat,
        frameBufferWidth = game.frameBufferWidth,
        frameBufferHeight = game.frameBufferHeight,
        gameScreen = this
    )
    private var tickTime = 0f
    private var touchEvents: List<TouchEvent>? = null
    var score: Int = 0
    var highscore: Int = 0
    var tick = TICK_INITIAL
    var colChk = CollisionDetector(gameObjects)
    var enmGen = EnemyGenerator(
        xSpawnPosition = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        gameObjects
    ).apply { setCollisionDetection(colChk) }
    var obsGen = ObstacleGenerator(
        worldWidth = game.frameBufferWidth,
        worldHeight = game.frameBufferHeight,
        gameObjects
    ).apply { setCollisionDetection(colChk) }
    private lateinit var g: Graphics
    private var paused = false

    init {
        if (DEBUG) {
            Log.d(TAG, "init")
        }
        game.graphics?.let { g = it }

        // Add the primary background
        gameObjects.add(
            Background(
                0,
                0,
                CyanBatGameActivity.gameAssets.graphics.background,
                game.frameBufferWidth,
                gameObjects
            )
        )

        gameObjects.add(bat)
        colChk.addObjectToCheck(bat)

        Shot.count = 0

        CyanBatGameActivity.gameAssets.audio.gameTrack.apply {
            play()
            isLooping = true
        }
        initStats()
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
        if (DEBUG) Log.d(TAG, "update")
        touchEvents = game.input?.touchEvents
        tickTime += deltaTime
        while (tickTime > tick) {
            tickTime -= tick
            updateGameObjects(deltaTime)
            score++
        }
    }

    // a simple gameloop
    private fun updateGameObjects(deltaTime: Float) {
        if (DEBUG) Log.d(TAG, "updateGameObjects $deltaTime")
        Background.count = 0
        for (go in gameObjects) {
            if (bat.alive) colChk.checkCollisions()

            touchEvents?.let { go.update(deltaTime, it) }
        }
        enmGen.generateEnemy()
        obsGen.generateObstacle()

        // Check for removal
        val toBeRemoved = gameObjects.filter { go -> go.isScheduledForRemoval() }
        toBeRemoved.forEach { go ->
            when (go) {
                is Background -> Background.count -= 1
                is Shot -> Shot.count -= 1
            }
            gameObjects.remove(go)
        }
    }

    fun saveHighscore() {
        if (score > highscore) {
            highscore = score
        }

        // launch in coroutine scope
        GlobalScope.launch(Dispatchers.Main) {
            game.context.dataStore.edit {
                it[PREFS_KEY_HIGH_SCORE] = highscore
            }
        }
    }

    override fun present(deltaTime: Float) {
        if (DEBUG) {
            Log.d(TAG, "present")
        }
        clearScreen()
        drawGameObjects()
        drawStats()
        if (!bat.alive) {
            g.drawPixmap(CyanBatGameActivity.gameAssets.graphics.death, 15, 15)
        }
    }

    private fun drawStats() {
        if (DEBUG) Log.d(TAG, "drawStats")
        g.apply {
            drawString("Score: $score", 5, 20, 15, Color.CYAN)
            drawString("Highscore: $highscore", 5, 40, 15, Color.CYAN)
            drawString("Lives: " + bat.lives, 5, 60, 15, Color.CYAN)
        }

    }

    private fun clearScreen() {
        g.clear(Color.BLACK)
    }

    private fun drawGameObjects() {
        if (DEBUG) {
            Log.d(TAG, "drawGameObjects")
        }
        gameObjects.forEach { it.draw(g) }
    }

    override fun pause() {
        if (DEBUG) {
            Log.d(TAG, "pause")
        }
        CyanBatGameActivity.gameAssets.audio.gameTrack.apply {
            stop()
            isLooping = false
        }
        paused = true
    }

    override fun resume() {
        if (DEBUG) {
            Log.d(TAG, "resume")
        }
        initStats()
        paused = false
    }

    override fun dispose() {

    }
}