package at.smiech.cyanbat.service

import android.util.Log
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.ENEMY_GENERATION_SPREAD_DECAY
import at.smiech.cyanbat.util.INITIAL_ENEMY_GENERATION_INTERVAL
import at.smiech.cyanbat.util.MINIMUM_ENEMY_GENERATION_INTERVAL
import at.smiech.cyanbat.util.MINIMUM_ENEMY_GENERATION_SPREAD
import at.smiech.cyanbat.util.TAG
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Narrows the random spread between enemy spawns, so enemies arrive faster as a run goes on.
 *
 * The result is the bound handed to `Random.nextLong`, which throws for anything <= 0 - the spread
 * used to decay without a floor and crashed the game after roughly 120 spawns. Clamping keeps the
 * ramp bottoming out instead of running off the end.
 */
internal fun decayEnemySpread(current: Long, decay: Long): Long =
    (current - decay).coerceAtLeast(MINIMUM_ENEMY_GENERATION_SPREAD)

class EnemyGenerator(
    private val xSpawnPosition: Int,
    private val worldHeight: Int,
    private val factory: EntityFactory
) {
    private val realEnemyHeight = CyanBatGameActivity.gameAssets.graphics.enemy.height
    private var generationInterval = INITIAL_ENEMY_GENERATION_INTERVAL
    private var waitJob: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun generateEnemy() {
        if (waitJob?.isActive == true) {
            return
        }
        waitJob = GlobalScope.launch {
            delay((MINIMUM_ENEMY_GENERATION_INTERVAL + Random.nextLong(generationInterval)).milliseconds)
        }
        generationInterval = decayEnemySpread(
            generationInterval,
            Random.nextLong(ENEMY_GENERATION_SPREAD_DECAY)
        )

        if (DEBUG) {
            Log.d(TAG, "generateEnemy")
        }

        factory.createEnemy(
            x = xSpawnPosition.toFloat(),
            y = 100f + Random.nextInt(worldHeight - 200),
            width = 28f, // Enemy.realWidth
            height = realEnemyHeight.toFloat(),
            pixmap = CyanBatGameActivity.gameAssets.graphics.enemy,
            type = Random.nextInt(3)
        )
    }
}
