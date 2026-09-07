package at.smiech.cyanbat

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreTrackerTest {

    /** Small, round numbers so the arithmetic in each assertion is obvious. */
    private fun tracker() = ScoreTracker(
        pointsPerHit = 10,
        hitsPerMultiplierStep = 3,
        maxMultiplier = 4,
    )

    @Test
    fun `a new run starts empty and unmultiplied`() {
        val scoring = tracker()
        assertEquals(0, scoring.score)
        assertEquals(0, scoring.hitStreak)
        assertEquals(1, scoring.multiplier)
    }

    @Test
    fun `surviving still scores a point per tick`() {
        val scoring = tracker()
        repeat(5) { scoring.awardSurvivalTick() }
        assertEquals(5, scoring.score)
    }

    @Test
    fun `a kill pays the base rate while the streak is short`() {
        val scoring = tracker()
        scoring.registerEnemyDestroyed()
        assertEquals(10, scoring.score)
        assertEquals(1, scoring.hitStreak)
        assertEquals(1, scoring.multiplier)
    }

    @Test
    fun `the multiplier steps up every few kills`() {
        val scoring = tracker()
        repeat(2) { scoring.registerEnemyDestroyed() }
        assertEquals(1, scoring.multiplier, "two kills is not a step yet")

        scoring.registerEnemyDestroyed()
        assertEquals(2, scoring.multiplier, "the third kill earns the step")

        repeat(3) { scoring.registerEnemyDestroyed() }
        assertEquals(3, scoring.multiplier)
    }

    /** The kill that earns a step is paid at the new rate, not the old one. */
    @Test
    fun `a kill scores at the multiplier it just earned`() {
        val scoring = tracker()
        repeat(2) { scoring.registerEnemyDestroyed() }
        assertEquals(20, scoring.score)

        scoring.registerEnemyDestroyed()
        assertEquals(40, scoring.score, "third kill should pay 10 x2, not 10 x1")
    }

    @Test
    fun `the multiplier is capped`() {
        val scoring = tracker()
        repeat(100) { scoring.registerEnemyDestroyed() }
        assertEquals(4, scoring.multiplier)
    }

    @Test
    fun `being hit breaks the streak but not the score`() {
        val scoring = tracker()
        repeat(6) { scoring.registerEnemyDestroyed() }
        val banked = scoring.score
        assertEquals(3, scoring.multiplier)

        scoring.registerPlayerHit()

        assertEquals(banked, scoring.score, "points already earned must survive a hit")
        assertEquals(0, scoring.hitStreak)
        assertEquals(1, scoring.multiplier)
    }

    @Test
    fun `the streak rebuilds from scratch after a hit`() {
        val scoring = tracker()
        repeat(6) { scoring.registerEnemyDestroyed() }
        scoring.registerPlayerHit()

        repeat(2) { scoring.registerEnemyDestroyed() }
        assertEquals(1, scoring.multiplier, "two kills into a fresh streak is not a step")
        scoring.registerEnemyDestroyed()
        assertEquals(2, scoring.multiplier)
    }

    @Test
    fun `reset clears the run`() {
        val scoring = tracker()
        repeat(4) { scoring.registerEnemyDestroyed() }
        repeat(4) { scoring.awardSurvivalTick() }

        scoring.reset()

        assertEquals(0, scoring.score)
        assertEquals(0, scoring.hitStreak)
        assertEquals(1, scoring.multiplier)
    }

    /**
     * The shipped step is deliberately low. Watching real runs, a life tends to yield two or
     * three kills before the bat is clipped, so a higher step means the multiplier is never
     * seen at all.
     */
    @Test
    fun `the shipped tuning is reachable in a normal life`() {
        val scoring = ScoreTracker()
        assertEquals(1, scoring.multiplier)
        repeat(3) { scoring.registerEnemyDestroyed() }
        assertEquals(2, scoring.multiplier, "three kills should be the first step")
        repeat(100) { scoring.registerEnemyDestroyed() }
        assertEquals(8, scoring.multiplier, "and it should cap at eight")
    }
}
