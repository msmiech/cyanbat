package at.smiech.cyanbat.service

import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

import android.util.Log

import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.impl.Enemy
import at.smiech.cyanbat.ui.CyanBatBaseScreen

class EnemyGenerator(private val gameObjects: MutableList<GameObject>) : Runnable {

    private val rnd = Random()
    private val running = AtomicBoolean(false)
    private val realEnemyHeight = CyanBatGameActivity.enemies.height
    private var collisionDetector: CollisionDetector? = null
    private var generationInterval = DEFAULT_GENERATION_INTERVAL
    private val startTime = System.currentTimeMillis()


    override fun run() {
        running.set(true)
        try {
            while (running.get()) {
                val elapsedTime = System.currentTimeMillis() - startTime
                val genSleepTime = Math.max(rnd.nextInt(generationInterval) * 10 - elapsedTime / 100, (generationInterval / 2).toLong())
                Thread.sleep(genSleepTime) // gets more difficult over time, i.e. gen sleeps less
                generateEnemy()
            }
        } catch (e: InterruptedException) {
            this.running.set(false)
        }

    }

    private fun generateEnemy() {
        if (DEBUG)
            Log.d(TAG, "generateObstacle")
        synchronized(gameObjects) {
            val en = Enemy(CyanBatBaseScreen.DISPLAY_HEIGHT, rnd
                    .nextInt(200) + 100, Enemy.realWidth, realEnemyHeight,
                    CyanBatGameActivity.enemies, rnd.nextInt(3))
            gameObjects.add(en)
            if (collisionDetector != null) {
                collisionDetector!!.addObjectToCheck(en)
            }
        }
    }

    fun setCollisionDetection(collisionDetector: CollisionDetector) {
        this.collisionDetector = collisionDetector
    }

    fun stop() {
        if (!running.get()) {
            Log.v(TAG, "Generator is not running!")
        }
        running.set(false)
    }

    companion object {
        private val DEBUG = false
        private val TAG = CyanBatGameActivity.TAG
        private val DEFAULT_GENERATION_INTERVAL = 500 // in ms
    }
}
