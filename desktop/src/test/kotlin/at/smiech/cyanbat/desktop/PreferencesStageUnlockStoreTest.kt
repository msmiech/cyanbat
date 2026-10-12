package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [PreferencesStageUnlockStore]: the unlocks, the clears, and the clears kept before difficulties. */
class PreferencesStageUnlockStoreTest {

    /** A node of the test's own, so it never reads or writes the game's real progress. */
    private val node = Preferences.userRoot().node("at/smiech/cyanbat-test-${UUID.randomUUID()}")

    @AfterTest
    fun removeNode() = node.removeNode()

    @Test
    fun `a stage keeps the hardest difficulty it was cleared on across launches`() = runBlocking {
        val store = PreferencesStageUnlockStore(node)
        store.recordClearAsync(1, Difficulty.NORMAL)
        store.recordClearAsync(1, Difficulty.EASY)
        store.recordClearAsync(2, Difficulty.HARD)
        assertEquals(mapOf(1 to Difficulty.NORMAL, 2 to Difficulty.HARD), store.clears.first())

        assertEquals(
            mapOf(1 to Difficulty.NORMAL, 2 to Difficulty.HARD),
            PreferencesStageUnlockStore(node).clears.first(),
        )
    }

    @Test
    fun `the stages opened before there were difficulties count as cleared on Hard`() =
        runBlocking {
            node.putInt("highest_stage_unlocked", 3)

            val store = PreferencesStageUnlockStore(node)

            assertEquals(mapOf(1 to Difficulty.HARD, 2 to Difficulty.HARD), store.clears.first())
        }

    /**
     * Once only: a stage opened since by a clear on Easy was cleared on Easy, and a store read again
     * must not take its unlock for a clear on Hard.
     */
    @Test
    fun `the clears from before difficulties are taken once`() = runBlocking {
        val first = PreferencesStageUnlockStore(node)
        first.recordClearAsync(1, Difficulty.EASY)
        first.unlockAsync(2)

        assertEquals(mapOf(1 to Difficulty.EASY), PreferencesStageUnlockStore(node).clears.first())
    }
}
