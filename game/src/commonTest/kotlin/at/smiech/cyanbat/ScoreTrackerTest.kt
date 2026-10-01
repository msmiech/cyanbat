package at.smiech.cyanbat

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreTrackerTest {

    /** Small, round numbers so the arithmetic in each assertion is obvious. */
    private fun tracker() = ScoreTracker(
        pointsPerHit = 10,
        hitsPerMultiplierStep = 3,
    )

    @Test
    fun `a new run starts empty and unmultiplied`() {
        val scoring = tracker()
        assertEquals(0, scoring.score)
        assertEquals(0, scoring.hitStreak)
        assertEquals(1, scoring.multiplier)
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

    /**
     * An elite is a bigger prize, not a longer streak: the streak counts how long the bat has gone
     * untouched, and one kill is one kill however much it paid.
     */
    @Test
    fun `an elite pays several kills' worth and is still one kill to the streak`() {
        val scoring = ScoreTracker(pointsPerHit = 10, hitsPerMultiplierStep = 3, eliteFactor = 3)
        scoring.registerEnemyDestroyed()
        scoring.registerEnemyDestroyed(elite = true)

        assertEquals(10 + 30, scoring.score)
        assertEquals(2, scoring.hitStreak)

        // The third kill earns the step, and an elite is paid at it like anything else.
        scoring.registerEnemyDestroyed(elite = true)
        assertEquals(10 + 30 + 60, scoring.score)
    }

    @Test
    fun `the multiplier has no ceiling`() {
        val scoring = tracker()
        repeat(300) { scoring.registerEnemyDestroyed() }
        assertEquals(101, scoring.multiplier)
    }

    /** Nowhere near reachable in play, but with no cap nothing but the length of a streak says so. */
    @Test
    fun `the score stops at the largest there is rather than wrapping round`() {
        val scoring = ScoreTracker(pointsPerHit = Int.MAX_VALUE / 2, hitsPerMultiplierStep = 3)
        repeat(3) { scoring.registerEnemyDestroyed() }
        assertEquals(Int.MAX_VALUE, scoring.score)
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
        assertEquals(35, scoring.multiplier, "and it should keep climbing, past where it used to stop at eight")
    }

    // region the Bounty Hunter bonus

    @Test
    fun `the bonus multiplier scales what a kill is worth`() {
        val plain = ScoreTracker().apply { registerEnemyDestroyed() }.score
        val boosted = ScoreTracker().apply {
            bonusMultiplier = 2f
            registerEnemyDestroyed()
        }.score

        assertEquals(plain * 2, boosted)
    }

    // endregion
}
