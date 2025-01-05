package at.smiech.cyanbat.service

import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

import android.util.Log

import at.grueneis.game.framework.Pixmap
import at.smiech.cyanbat.activities.CyanBatGameActivity
import at.smiech.cyanbat.gameobjects.GameObject
import at.smiech.cyanbat.gameobjects.impl.Obstacle
import at.smiech.cyanbat.screen.CyanBatBaseScreen

class ObstacleGenerator(private val gameObjects: MutableList<GameObject>) : Runnable {
    private val rnd = Random()
    private val running = AtomicBoolean(false)
    private var collisionDetector: CollisionDetector? = null
    private var generationInterval = OBSTACLE_GENERATION_INTERVAL

    override fun run() {
        this.running.set(true)
        try {
            while (running.get()) {
                Thread.sleep((rnd.nextInt(generationInterval) + generationInterval).toLong())
                generateObstacle()
            }
        } catch (e: InterruptedException) {
            this.running.set(false)
        }

    }

    private fun generateObstacle() {
        if (DEBUG)
            Log.d(TAG, "generateObstacle")
        val obstacleHeight: Int
        val obstaclePixmap: Pixmap
        if (rnd.nextBoolean())
        // Top or not?
        {
            obstaclePixmap = CyanBatGameActivity.topObstacles[rnd
                    .nextInt(CyanBatGameActivity.topObstacles.size)]!!
            obstacleHeight = 0

        } else {
            obstaclePixmap = CyanBatGameActivity.bottomObstacles[rnd
                    .nextInt(CyanBatGameActivity.bottomObstacles.size)]!!
            obstacleHeight = CyanBatBaseScreen.DISPLAY_WIDTH - obstaclePixmap.height
        }
        synchronized(gameObjects) {
            val obs = Obstacle(CyanBatBaseScreen.DISPLAY_HEIGHT,
                    obstacleHeight, obstaclePixmap)
            gameObjects.add(obs)
            if (collisionDetector != null) {
                collisionDetector!!.addObjectToCheck(obs)
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
        private val OBSTACLE_GENERATION_INTERVAL = 1000 // in ms
    }
}
