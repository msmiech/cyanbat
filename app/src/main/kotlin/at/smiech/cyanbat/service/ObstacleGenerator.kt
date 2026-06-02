package at.smiech.cyanbat.service

import android.util.Log
import at.smiech.cyanbat.gameobject.GameObject
import at.smiech.cyanbat.gameobject.impl.Obstacle
import at.smiech.cyanbat.resource.Level
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.TAG
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class ObstacleGenerator(
    private val worldWidth: Int,
    private val worldHeight: Int,
    private val gameObjects: MutableList<GameObject>,
    var level: Level
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
            delay((Random.nextInt(generationInterval) + generationInterval).toLong().milliseconds)
        }

        var y = 0
        val obstaclePixmap = if (Random.nextBoolean())
        // Top or not?
        {
            level.topObstacles[Random.nextInt(level.topObstacles.size)]

        } else {
            level.bottomObstacles[Random.nextInt(level.bottomObstacles.size)]?.also {
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
