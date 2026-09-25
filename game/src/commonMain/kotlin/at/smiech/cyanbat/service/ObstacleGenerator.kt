package at.smiech.cyanbat.service

import at.smiech.cyanbat.resource.Level
import kotlin.random.Random

/**
 * Drops a stalactite or stalagmite into the cave at random intervals.
 *
 * Timed off the caller's fixed tick, as [EnemyGenerator] is, rather than off the wall clock. It
 * used to wait on a GlobalScope coroutine, whose countdown ran on while the game was paused and
 * outlived the screen that started it.
 */
class ObstacleGenerator(
    private val worldWidth: Int,
    private val worldHeight: Int,
    private val factory: EntityFactory,
    var level: Level,
    private val random: Random = Random.Default,
) {
    /** Zero, so the first update places an obstacle straight away, as the old generator did. */
    private var timeUntilNextObstacle = 0f

    /** Advances the generator by one tick, placing an obstacle if one has come due. */
    fun update(deltaTime: Float) {
        timeUntilNextObstacle -= deltaTime
        if (timeUntilNextObstacle > 0f) return
        // Anywhere from one interval to two, the spread the old generator used.
        timeUntilNextObstacle = OBSTACLE_INTERVAL_SECONDS * (1f + random.nextFloat())

        var y = 0f
        val obstaclePixmap = if (random.nextBoolean()) {
            level.topObstacles[random.nextInt(level.topObstacles.size)]
        } else {
            level.bottomObstacles[random.nextInt(level.bottomObstacles.size)]?.also {
                y = worldHeight.toFloat() - it.height
            }
        }

        obstaclePixmap?.let {
            factory.createObstacle(
                worldWidth.toFloat(),
                y,
                it.width.toFloat(),
                it.height.toFloat(),
                it
            )
        }
    }

    private companion object {
        const val OBSTACLE_INTERVAL_SECONDS = 1f
    }
}
