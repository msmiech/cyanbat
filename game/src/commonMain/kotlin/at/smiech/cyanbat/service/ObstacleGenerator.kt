package at.smiech.cyanbat.service

import at.smiech.cyanbat.resource.Stage
import at.smiech.engine.Pixmap
import kotlin.random.Random

/**
 * Places an obstacle at random intervals: something hanging from the top of the frame or standing
 * on the ground, from the stage's sets.
 *
 * Timed off the caller's fixed tick, as [EnemyGenerator] is, so a paused game pauses it.
 *
 * @param dayPosition how far through its day the stage is, for a stage whose ground changes on the way
 *   to its boss; see [at.smiech.cyanbat.resource.Approach].
 */
class ObstacleGenerator(
    private val worldWidth: Int,
    private val worldHeight: Int,
    private val factory: EntityFactory,
    private val stage: Stage,
    private val random: Random = Random.Default,
    private val dayPosition: () -> Float = { 0f },
) {
    /** Zero, so the first update places an obstacle at once. */
    private var timeUntilNextObstacle = 0f

    /** Advances the generator by one tick, placing an obstacle if one has come due. */
    fun update(deltaTime: Float) {
        timeUntilNextObstacle -= deltaTime
        if (timeUntilNextObstacle > 0f) return
        // Anywhere from one interval to two.
        timeUntilNextObstacle = OBSTACLE_INTERVAL_SECONDS * (1f + random.nextFloat())

        // A sheet drawn in several times of day holds each as a row, so an obstacle
        // is one row tall.
        val keyframes = stage.obstacleKeyframes
        var y = 0f
        // On a stage with an open sky a roll for the top places nothing, rather than a second
        // ground obstacle, so the ground keeps the usual density.
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
     * The set a ground obstacle is drawn from: the stage's own, or, on the way to its boss, the
     * approach's, as often as the approach's share says. The dice are only rolled once the approach
     * has begun, so the random sequence before it is unchanged.
     */
    private fun groundObstacles(): Array<Pixmap?> {
        val approach = stage.approach ?: return stage.bottomObstacles
        val share = approach.share(dayPosition())
        if (share <= 0f) return stage.bottomObstacles
        return if (random.nextFloat() < share) approach.bottomObstacles else stage.bottomObstacles
    }

    private companion object {
        /** The shortest gap between obstacles; each gap is one to two of these. */
        const val OBSTACLE_INTERVAL_SECONDS = 1f
    }
}
