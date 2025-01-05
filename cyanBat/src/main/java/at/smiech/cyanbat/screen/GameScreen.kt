package at.smiech.cyanbat.screen

import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import androidx.preference.PreferenceManager
import at.grueneis.game.framework.Game
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.impl.Background
import at.smiech.cyanbat.gameobjects.impl.CyanBat
import at.smiech.cyanbat.gameobjects.impl.Shot
import at.smiech.cyanbat.service.CollisionDetector
import at.smiech.cyanbat.service.EnemyGenerator
import at.smiech.cyanbat.service.ObstacleGenerator
import java.util.Random

class GameScreen(public override val game: Game) : CyanBatBaseScreen(game) {
    val gameObjects: MutableList<GameObject> = ArrayList()
    private val bat = CyanBat(
        DISPLAY_HEIGHT / 3,
        DISPLAY_WIDTH / 2, CyanBat.DEFAULT_WIDTH,
        CyanBatGameActivity.bat.height, CyanBatGameActivity.bat, this
    )
    private var tickTime = 0f
    private var touchEvents: List<TouchEvent>? = null
    private var g: Graphics? = null
    private var obstclGenThread: Thread? = null
    private var obstclGen: ObstacleGenerator? = null
    private var enmGenThread: Thread? = null
    private var enmGen: EnemyGenerator? = null
    var score: Int = 0
    var colChk: CollisionDetector
    private var prefs: SharedPreferences? = null
    private val musicPlayer = CyanBatGameActivity.musicPlayer

    init {
        if (DEBUG)
            Log.d(TAG, "init")
        g = super.game.graphics
        rnd = Random()

        // Add the primary background
        gameObjects.add(
            Background(
                0, 0, CyanBatGameActivity.background,
                gameObjects
            )
        )
        // Add the activity_main player character
        gameObjects.add(bat)

        colChk = CollisionDetector(gameObjects)
        colChk.addObjectToCheck(bat)
        Shot.count = 0
        musicPlayer.continueMusic()
        initStats()
        startThreads()
    }

    private fun initStats() {
        score = 0
        val ctx = super.game.context
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        readHighscore()
    }

    private fun readHighscore() {
        highscore = prefs!!.getInt("highscore", 0)
    }

    override fun update(deltaTime: Float) {
        if (DEBUG)
            Log.d(TAG, "update")
        touchEvents = game.input?.touchEvents
        tickTime += deltaTime
        while (tickTime > tick) {
            tickTime -= tick
            updateGameObjects(deltaTime)
            score++
        }
    }


    // a simple gameloop, essentially
    private fun updateGameObjects(deltaTime: Float) {
        if (DEBUG)
            Log.d(TAG, "updateGameObjects")
        Background.count = 0
        synchronized(gameObjects) {
            for (i in gameObjects.indices) {
                val go = gameObjects[i]

                if (bat.alive)
                    colChk.checkCollisions()

                go.update(deltaTime, touchEvents!!)

            }

            // Check for removal
            val toBeRemoved = gameObjects.filter { go -> go.scheduledForRemoval() }
            toBeRemoved.forEach { go ->
                run {
                    // Let's give the garbage collector something to do:
                    if (go is Background)
                        Background.count -= 1
                    if (go is Shot)
                        Shot.count -= 1
                    gameObjects.remove(go)
                }
            }
        }
    }

    fun saveHighscore() {
        if (score > highscore)
            highscore = score
        prefs?.edit()?.putInt("highscore", highscore)?.apply()
    }

    override fun present(deltaTime: Float) {
        if (DEBUG)
            Log.d(TAG, "present")
        clearScreen()
        drawGameObjects()
        drawStats()
        if (!bat.alive)
            g?.drawPixmap(CyanBatGameActivity.death, 15, 15)
    }

    private fun drawStats() {
        if (DEBUG)
            Log.d(TAG, "drawStats")
        g?.apply {
            drawString("Score: $score", 5, 20, 15, Color.CYAN)
            drawString("Highscore: $highscore", 5, 40, 15, Color.CYAN)
            drawString("Lives: " + bat.lives, 5, 60, 15, Color.CYAN)
        }

    }

    private fun clearScreen() {
        g?.clear(Color.BLACK)
    }

    private fun drawGameObjects() {
        if (DEBUG)
            Log.d(TAG, "drawGameObjects")
        synchronized(gameObjects) {
            for (i in gameObjects.indices) {
                gameObjects[i].draw(g!!)
            }
        }
    }

    override fun pause() {
        if (DEBUG)
            Log.d(TAG, "pause")
        CyanBatGameActivity.musicPlayer.stopMusic()
        interruptThreads()
    }

    fun interruptThreads() {
        if (DEBUG)
            Log.d(TAG, "interruptThreads")
        obstclGen!!.stop()
        enmGen!!.stop()
    }

    override fun resume() {
        if (DEBUG)
            Log.d(TAG, "resume")
        initStats()
        startThreads()
    }

    private fun startThreads() {
        if (DEBUG)
            Log.d(TAG, "startThreads")
        initThreads()
        obstclGenThread!!.start()
        enmGenThread!!.start()
    }

    private fun initThreads() {
        if (DEBUG)
            Log.d(TAG, "initThreads")
        if (obstclGenThread != null && obstclGenThread!!.isAlive) {
            obstclGenThread!!.interrupt()
        }

        obstclGen = ObstacleGenerator(gameObjects)
        obstclGen!!.setCollisionDetection(colChk)
        obstclGenThread = Thread(obstclGen)


        if (enmGenThread != null && enmGenThread!!.isAlive) {
            enmGenThread!!.interrupt()
        }
        enmGen = EnemyGenerator(gameObjects)
        enmGen!!.setCollisionDetection(colChk)
        enmGenThread = Thread(enmGen)
    }

    companion object {
        private val TAG = CyanBatGameActivity.TAG

        val DEBUG = CyanBatGameActivity.DEBUG

        internal val TICK_INITIAL = 0.019f
        internal val TICK_DECREAMENT_FACTOR = 0.9f
        internal var tick = TICK_INITIAL


        var rnd = Random()
        var highscore = 0
    }
}