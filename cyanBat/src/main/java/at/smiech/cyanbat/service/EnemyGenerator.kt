package at.smiech.cyanbat.service

import android.util.Log
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.gameobject.GameObject
import at.smiech.cyanbat.gameobject.impl.Enemy
import at.smiech.cyanbat.util.DEBUG
import at.smiech.cyanbat.util.INITIAL_ENEMY_GENERATION_INTERVAL
import at.smiech.cyanbat.util.MINIMUM_ENEMY_GENERATION_INTERVAL
import at.smiech.cyanbat.util.TAG
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class EnemyGenerator(
    private val xSpawnPosition: Int,
    private val worldHeight: Int,
    private val gameObjects: MutableList<GameObject>
) {
    private val realEnemyHeight = CyanBatGameActivity.gameAssets.graphics.enemy.height
    private var collisionDetector: CollisionDetector? = null
    private var generationInterval = INITIAL_ENEMY_GENERATION_INTERVAL

    private var waitJob: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun generateEnemy() {
        if (waitJob?.isActive == true) {
            return
        }
        waitJob = GlobalScope.launch {
            delay(MINIMUM_ENEMY_GENERATION_INTERVAL + Random.nextLong(generationInterval))
        }
        generationInterval -= Random.nextLong(84L)

        if (DEBUG) {
            Log.d(TAG, "generateEnemy")
        }

        val en = Enemy(
            x = xSpawnPosition,
            y = 100 + Random
                .nextInt(worldHeight - 200), Enemy.realWidth, realEnemyHeight,
            CyanBatGameActivity.gameAssets.graphics.enemy, Random.nextInt(3)
        )
        gameObjects.add(en)
        collisionDetector?.addObjectToCheck(en)
    }

    fun setCollisionDetection(collisionDetector: CollisionDetector) {
        this.collisionDetector = collisionDetector
    }
}
