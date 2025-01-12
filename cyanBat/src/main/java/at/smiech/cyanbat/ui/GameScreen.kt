package at.smiech.cyanbat.ui

import android.graphics.Color
import android.util.Log
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

class GameScreen(public override val game: Game) : CyanBatBaseScreen(game) {
    val gameObjects: MutableList<GameObject> = ArrayList()
    private val bat = CyanBat(
        DISPLAY_HEIGHT / 3,
        DISPLAY_WIDTH / 2,
        CyanBat.DEFAULT_WIDTH,
        CyanBatGameActivity.bat.height,
        CyanBatGameActivity.bat,
        this
    )
    private var tickTime = 0f
    private var touchEvents: List<TouchEvent>? = null
    private var obstclGenThread: Thread? = null
    private var obstclGen: ObstacleGenerator? = null
    private var enmGenThread: Thread? = null
    private var enmGen: EnemyGenerator? = null
    var score: Int = 0
    var colChk: CollisionDetector
    private val musicPlayer = CyanBatGameActivity.musicPlayer
    private lateinit var g: Graphics

    init {
        if (DEBUG) Log.d(TAG, "init")
        game.graphics?.let { g = it }

        // Add the primary background
        gameObjects.add(
            Background(
                0, 0, CyanBatGameActivity.background, gameObjects
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
        if (DEBUG) Log.d(TAG, "updateGameObjects")
        Background.count = 0
        synchronized(gameObjects) {
            for (i in gameObjects.indices) {
                val go = gameObjects[i]

                if (bat.alive) colChk.checkCollisions()

                go.update(deltaTime, touchEvents!!)

            }

            // Check for removal
            val toBeRemoved = gameObjects.filter { go -> go.scheduledForRemoval() }
            toBeRemoved.forEach { go ->
                run {
                    // Let's give the garbage collector something to do:
                    if (go is Background) Background.count -= 1
                    if (go is Shot) Shot.count -= 1
                    gameObjects.remove(go)
                }
            }
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
        if (DEBUG) Log.d(TAG, "present")
        clearScreen()
        drawGameObjects()
        drawStats()
        if (!bat.alive) g.drawPixmap(CyanBatGameActivity.death, 15, 15)
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
        if (DEBUG) Log.d(TAG, "drawGameObjects")
        synchronized(gameObjects) {
            for (i in gameObjects.indices) {
                g.let { gameObjects[i].draw(it) }
            }
        }
    }

    override fun pause() {
        if (DEBUG) Log.d(TAG, "pause")
        CyanBatGameActivity.musicPlayer.stopMusic()
        interruptThreads()
    }

    fun interruptThreads() {
        if (DEBUG) Log.d(TAG, "interruptThreads")
        obstclGen?.stop()
        enmGen?.stop()
    }

    override fun resume() {
        if (DEBUG) Log.d(TAG, "resume")
        initStats()
        startThreads()
    }

    private fun startThreads() {
        if (DEBUG) Log.d(TAG, "startThreads")
        initThreads()
        obstclGenThread?.start()
        enmGenThread?.start()
    }

    private fun initThreads() {
        if (DEBUG) Log.d(TAG, "initThreads")
        if (obstclGenThread != null && obstclGenThread?.isAlive == true) {
            obstclGenThread?.interrupt()
        }

        obstclGen = ObstacleGenerator(gameObjects).apply {
            setCollisionDetection(colChk)
        }
        obstclGenThread = Thread(obstclGen)


        if (enmGenThread != null && enmGenThread!!.isAlive) {
            enmGenThread!!.interrupt()
        }
        enmGen = EnemyGenerator(gameObjects).apply { setCollisionDetection(colChk) }
        enmGenThread = Thread(enmGen)
    }

    companion object {
        private val TAG = CyanBatGameActivity.TAG

        val DEBUG = CyanBatGameActivity.DEBUG

        private val TICK_INITIAL = 0.019f
        internal var tick = TICK_INITIAL
        var highscore = 0
    }
}