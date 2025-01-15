package at.smiech.cyanbat.ui

import android.graphics.Color
import android.util.Log
import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.activity.CyanBatGameActivity
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
import java.util.concurrent.CopyOnWriteArrayList

class GameScreen(public override val game: Game) : CyanBatBaseScreen(game) {
    val gameObjects = CopyOnWriteArrayList<GameObject>()
    private val bat = CyanBat(
        DISPLAY_HEIGHT / 3,
        DISPLAY_WIDTH / 2,
        CyanBat.DEFAULT_WIDTH,
        CyanBatGameActivity.gameAssets.graphics.bat.height,
        CyanBatGameActivity.gameAssets.graphics.bat,
        this
    )
    private var tickTime = 0f
    private var touchEvents: List<TouchEvent>? = null
    var score: Int = 0
    var highscore: Int = 0
    var tick = TICK_INITIAL
    var colChk = CollisionDetector(gameObjects)
    var enmGen = EnemyGenerator(gameObjects).apply { setCollisionDetection(colChk) }
    var obsGen = ObstacleGenerator(gameObjects).apply { setCollisionDetection(colChk) }
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
                0, 0, CyanBatGameActivity.gameAssets.graphics.background, gameObjects
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

    private fun readHighscore() {
        highscore = 0
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
        val toBeRemoved = gameObjects.filter { go -> go.scheduledForRemoval() }
        toBeRemoved.forEach { go ->
            when (go) {
                is Background -> Background.count -= 1
                is Shot -> Shot.count -= 1
            }
            gameObjects.remove(go)
        }
    }

    fun saveHighscore() {
        if (score > highscore) highscore = score

        // launch in coroutine scope
        /*
        prefs?.edit {
            it[intPreferencesKey("highscore")] = highscore
        }*/
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
}