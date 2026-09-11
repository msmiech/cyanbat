package at.smiech.cyanbat.progress

import at.smiech.cyanbat.util.XP_FIRST_LEVEL
import at.smiech.cyanbat.util.XP_LEVEL_STEP
import at.smiech.cyanbat.util.XP_PER_KILL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerProgressTest {

    private val progress = PlayerProgress()

    @Test
    fun `a run opens at level one with nothing earned`() {
        assertEquals(1, progress.level)
        assertEquals(0, progress.experience)
        assertEquals(0, progress.totalExperience)
        assertEquals(0f, progress.fraction)
    }

    @Test
    fun `experience short of the threshold buys nothing`() {
        assertEquals(0, progress.award(XP_FIRST_LEVEL - 1))

        assertEquals(1, progress.level)
        assertEquals(XP_FIRST_LEVEL - 1, progress.experience)
    }

    @Test
    fun `reaching the threshold exactly levels up and leaves nothing over`() {
        assertEquals(1, progress.award(XP_FIRST_LEVEL))

        assertEquals(2, progress.level)
        assertEquals(0, progress.experience)
    }

    /** Nothing earned should ever be rounded away; the remainder starts the next level. */
    @Test
    fun `experience past the threshold carries over`() {
        progress.award(XP_FIRST_LEVEL + 7)

        assertEquals(2, progress.level)
        assertEquals(7, progress.experience)
    }

    /**
     * A single kill late in a run can cross more than one threshold, and the player is owed a
     * power-up for each - so the award reports a count, not a flag.
     */
    @Test
    fun `one award can buy several levels at once`() {
        // Enough for level 2 and level 3, with change.
        val levels = progress.award(XP_FIRST_LEVEL + (XP_FIRST_LEVEL + XP_LEVEL_STEP) + 3)

        assertEquals(2, levels)
        assertEquals(3, progress.level)
        assertEquals(3, progress.experience)
    }

    @Test
    fun `each level costs more than the one before it`() {
        val costs = (1..5).map {
            val cost = progress.experienceForNextLevel
            progress.award(cost)
            cost
        }

        costs.zipWithNext { earlier, later ->
            assertEquals(earlier + XP_LEVEL_STEP, later)
        }
    }

    @Test
    fun `total experience counts the whole run, not the progress towards one level`() {
        progress.award(XP_FIRST_LEVEL + 10)
        progress.award(5)

        assertEquals(XP_FIRST_LEVEL + 15, progress.totalExperience)
        assertEquals(15, progress.experience)
    }

    @Test
    fun `the bar fills as the next level is approached`() {
        progress.award(progress.experienceForNextLevel / 2)

        assertTrue(progress.fraction in 0.4f..0.6f, "half a level read as ${progress.fraction}")
    }

    @Test
    fun `nothing is awarded for nothing`() {
        assertEquals(0, progress.award(0))
        assertEquals(0, progress.award(-50))
        assertEquals(0, progress.totalExperience)
    }

    @Test
    fun `reset puts the run back to level one`() {
        progress.award(1_000)
        progress.reset()

        assertEquals(1, progress.level)
        assertEquals(0, progress.experience)
        assertEquals(0, progress.totalExperience)
    }

    /** Kills pay more as the level goes on, so pressing on beats farming the opening minute. */
    @Test
    fun `a kill is worth more in a later wave`() {
        val byWave = (0..4).map { PlayerProgress.experienceForKill(it) }

        assertEquals(XP_PER_KILL, byWave.first())
        byWave.zipWithNext { earlier, later ->
            assertTrue(later > earlier, "wave payout did not climb: $earlier then $later")
        }
    }

    /**
     * The curve has to be reachable: an opening minute that cannot buy a single level would mean
     * the player never learns the mechanic exists.
     */
    @Test
    fun `the opening wave levels the bat inside a handful of kills`() {
        var kills = 0
        while (progress.level == 1) {
            progress.award(PlayerProgress.experienceForKill(0))
            kills++
        }

        assertTrue(kills in 2..8, "the first level up took $kills opening-wave kills")
    }
}
