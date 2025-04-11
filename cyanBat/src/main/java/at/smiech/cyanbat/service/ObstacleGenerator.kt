package at.smiech.cyanbat.service

import android.util.Log
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.gameobject.GameObject
import at.smiech.cyanbat.gameobject.impl.Obstacle
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class ObstacleGenerator(
    private val worldWidth: Int,
    private val worldHeight: Int,
    private val gameObjects: MutableList<GameObject>
) {
    private var collisionDetector: CollisionDetector? = null
    private var generationInterval = OBSTACLE_GENERATION_INTERVAL

    private var waitJob: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun generateObstacle() {
        if (waitJob?.isActive == true) {
            return
        }

        if (DEBUG) {
            Log.d(TAG, "generateObstacle")
        }

        waitJob = GlobalScope.launch {
            delay((Random.nextInt(generationInterval) + generationInterval).toLong())
        }

        var y = 0
        val obstaclePixmap = if (Random.nextBoolean())
        // Top or not?
        {
            CyanBatGameActivity.gameAssets.graphics.topObstacles[Random
                .nextInt(CyanBatGameActivity.gameAssets.graphics.topObstacles.size)]

        } else {
            CyanBatGameActivity.gameAssets.graphics.bottomObstacles[Random
                .nextInt(CyanBatGameActivity.gameAssets.graphics.bottomObstacles.size)]?.also {
                y = worldHeight - it.height
            }
        }

        obstaclePixmap?.let {
            Obstacle(
                worldWidth,
                y, it
            ).also {
                gameObjects.add(it)
                collisionDetector?.addObjectToCheck(it)
            }
        }
    }


    fun setCollisionDetection(collisionDetector: CollisionDetector) {
        this.collisionDetector = collisionDetector
    }

    companion object {
        private const val OBSTACLE_GENERATION_INTERVAL = 1000 // in ms
    }
}
