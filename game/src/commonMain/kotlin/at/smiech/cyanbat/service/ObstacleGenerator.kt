package at.smiech.cyanbat.service

import at.smiech.cyanbat.resource.Stage
import at.smiech.engine.Pixmap
import kotlin.random.Random

/**
 * Drops a stalactite or stalagmite into the cave at random intervals - or whatever hangs from the
 * top of the stage being flown and stands on its floor.
 *
 * Timed off the caller's fixed tick, as [EnemyGenerator] is, rather than off the wall clock. It
 * used to wait on a GlobalScope coroutine, whose countdown ran on while the game was paused and
 * outlived the screen that started it.
 *
 * @param dayPosition how far through its day the stage is, for a stage whose ground changes on the way
 *   to its boss; see [at.smiech.cyanbat.resource.Approach].
 */
class ObstacleGenerator(
    private val worldWidth: Int,
    private val worldHeight: Int,
    private val factory: EntityFactory,
    var stage: Stage,
    private val random: Random = Random.Default,
    private val dayPosition: () -> Float = { 0f },
) {
    /** Zero, so the first update places an obstacle straight away, as the old generator did. */
    private var timeUntilNextObstacle = 0f

    /** Advances the generator by one tick, placing an obstacle if one has come due. */
    fun update(deltaTime: Float) {
        timeUntilNextObstacle -= deltaTime
        if (timeUntilNextObstacle > 0f) return
        // Anywhere from one interval to two, the spread the old generator used.
        timeUntilNextObstacle = OBSTACLE_INTERVAL_SECONDS * (1f + random.nextFloat())

        // A sheet drawn in several times of day holds each as a row of its own, so an obstacle is
        // as tall as one row of it, not as the whole sheet.
        val keyframes = stage.obstacleKeyframes
        var y = 0f
        // A stage with an open sky has nothing to hang from the top, and a roll for the top places
        // nothing at all rather than a second obstacle on the ground: the ground keeps the density
        // every other stage has, and the sky is left clear.
        val obstaclePixmap = if (random.nextBoolean()) {
            stage.topObstacles.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
        } else {
            val ground = groundObstacles()
            ground[random.nextInt(ground.size)]?.also {
                y = worldHeight.toFloat() - it.height / keyframes
            }
        }

        obstaclePixmap?.let {
            factory.createObstacle(
                worldWidth.toFloat(),
                y,
                it.width.toFloat(),
                (it.height / keyframes).toFloat(),
                it,
                keyframed = keyframes > 1,
            )
        }
    }

    /**
     * What this obstacle on the ground is drawn from: the stage's own, or - on the way to its boss -
     * what the ground is turning into, as often as the approach has got. The dice are only rolled
     * once it has begun, so a stage without one places exactly what it always did.
     */
    private fun groundObstacles(): Array<Pixmap?> {
        val approach = stage.approach ?: return stage.bottomObstacles
        val share = approach.share(dayPosition())
        if (share <= 0f) return stage.bottomObstacles
        return if (random.nextFloat() < share) approach.bottomObstacles else stage.bottomObstacles
    }

    private companion object {
        const val OBSTACLE_INTERVAL_SECONDS = 1f
    }
}
