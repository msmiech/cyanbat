package at.smiech.cyanbat.service

import at.smiech.cyanbat.util.ENEMY_GENERATION_SPREAD_DECAY
import at.smiech.cyanbat.util.INITIAL_ENEMY_GENERATION_INTERVAL
import at.smiech.cyanbat.util.MINIMUM_ENEMY_GENERATION_SPREAD
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnemySpreadTest {

    @Test
    fun `spread shrinks by the decay amount`() {
        assertEquals(4_916L, decayEnemySpread(5_000L, 84L))
    }

    @Test
    fun `spread never falls below the floor`() {
        assertEquals(MINIMUM_ENEMY_GENERATION_SPREAD, decayEnemySpread(MINIMUM_ENEMY_GENERATION_SPREAD + 10L, 84L))
        assertEquals(MINIMUM_ENEMY_GENERATION_SPREAD, decayEnemySpread(0L, 84L))
        assertEquals(MINIMUM_ENEMY_GENERATION_SPREAD, decayEnemySpread(Long.MIN_VALUE + 1L, 0L))
    }

    @Test
    fun `spread is never zero or negative`() {
        for (current in listOf(0L, 1L, 84L, 500L, 5_000L)) {
            for (decay in 0L until ENEMY_GENERATION_SPREAD_DECAY) {
                assertTrue(decayEnemySpread(current, decay) > 0L, "spread went non-positive for ($current, $decay)")
            }
        }
    }

    /**
     * Regression: the spread decayed without a floor, so after roughly 120 spawns it reached zero
     * and `Random.nextLong(bound)` threw, killing the game mid-run. Drive far more spawns than that
     * and assert the value stays a legal bound the whole way.
     */
    @Test
    fun `a long run never produces an illegal random bound`() {
        val random = Random(20260906)
        var spread = INITIAL_ENEMY_GENERATION_INTERVAL

        repeat(10_000) { spawn ->
            assertTrue(spread > 0L, "spread became $spread after $spawn spawns")
            // The call the game makes; throws IllegalArgumentException if the bound is <= 0.
            random.nextLong(spread)
            spread = decayEnemySpread(spread, random.nextLong(ENEMY_GENERATION_SPREAD_DECAY))
        }

        assertEquals(MINIMUM_ENEMY_GENERATION_SPREAD, spread, "a long run should settle on the floor")
    }

    @Test
    fun `decay is monotone until it reaches the floor`() {
        var spread = INITIAL_ENEMY_GENERATION_INTERVAL
        repeat(500) {
            val next = decayEnemySpread(spread, 84L)
            assertTrue(next <= spread, "spread grew from $spread to $next")
            spread = next
        }
    }
}
