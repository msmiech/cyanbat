package at.smiech.cyanbat.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [PreferencesHighscoreStore], and the migrations that moved old highscores to their stages. */
class PreferencesHighscoreStoreTest {

    /** A node of the test's own, so it never reads or writes the game's real scores. */
    private val node = Preferences.userRoot().node("at/smiech/cyanbat-test-${UUID.randomUUID()}")

    @AfterTest
    fun removeNode() = node.removeNode()

    @Test
    fun `the old single highscore moves onto the cave`() = runBlocking {
        node.putInt("highscore", 4125)

        val store = PreferencesHighscoreStore(node)

        // Every release that kept one had the cave as its only stage, and the cave is stage 2 now.
        assertEquals(4125, store.read(2))
        assertEquals(0, store.read(1))
        assertNull(node.get("highscore", null))
    }

    @Test
    fun `a better cave score survives the move`() = runBlocking {
        node.putInt("highscore", 4125)
        node.putInt("highscore_stage_1", 9000)

        assertEquals(9000, PreferencesHighscoreStore(node).read(2))
    }

    @Test
    fun `the cave's and the forest's highscores follow their stages when the two swap places`() =
        runBlocking {
            node.putInt("highscore_stage_1", 4125)
            node.putInt("highscore_stage_2", 900)
            node.putInt("highscore_stage_3", 70)

            val store = PreferencesHighscoreStore(node)

            assertEquals(mapOf(1 to 900, 2 to 4125, 3 to 70), store.byStage.first())
            // Once: a store opened again does not swap them back.
            assertEquals(
                mapOf(1 to 900, 2 to 4125, 3 to 70),
                PreferencesHighscoreStore(node).byStage.first()
            )
        }

    @Test
    fun `a store that starts empty never has its scores swapped`() = runBlocking {
        PreferencesHighscoreStore(node).saveAsync(stageId = 1, value = 500)

        assertEquals(mapOf(1 to 500), PreferencesHighscoreStore(node).byStage.first())
    }

    @Test
    fun `each stage keeps its own highscore, and a lower score never replaces one`() = runBlocking {
        val store = PreferencesHighscoreStore(node)
        store.saveAsync(stageId = 1, value = 500)
        store.saveAsync(stageId = 2, value = 300)
        store.saveAsync(stageId = 1, value = 200)

        assertEquals(mapOf(1 to 500, 2 to 300), store.byStage.first())
        // Stored, not only mirrored: a store opened afterwards reads the same back off the keys.
        assertEquals(mapOf(1 to 500, 2 to 300), PreferencesHighscoreStore(node).byStage.first())
    }
}
