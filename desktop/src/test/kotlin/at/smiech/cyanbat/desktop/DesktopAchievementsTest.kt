package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.Achievement
import at.smiech.engine.FrameCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The desktop's achievements: earned in the preferences, pictured in files. */
class DesktopAchievementsTest {

    /** A node of the test's own, so it never reads or writes the game's real progress. */
    private val node = Preferences.userRoot().node("at/smiech/cyanbat-test-${UUID.randomUUID()}")

    /** A folder of the test's own for the pictures, for the same reason. */
    private val folder = Files.createTempDirectory("cyanbat-achievements").toFile()

    @AfterTest
    fun clean() {
        node.removeNode()
        folder.deleteRecursively()
    }

    @Test
    fun `an achievement and its picture are there at the next launch`() = runBlocking {
        val picture = object : FrameCapture {
            override val width = 640
            override val height = 360
            override fun encodePng() = byteArrayOf(9, 8, 7)
        }
        // The picture written as it is earned, rather than off the game's thread.
        val store = desktopAchievementStore(node, folder, CoroutineScope(Dispatchers.Unconfined))
        store.unlockAsync(listOf(Achievement.LEVEL_10), picture)

        val again = desktopAchievementStore(node, folder)
        assertEquals(setOf(Achievement.LEVEL_10), again.unlocked.first())
        assertContentEquals(byteArrayOf(9, 8, 7), again.screenshot(Achievement.LEVEL_10))
        assertEquals(listOf("level_10.png"), folder.list()!!.toList())
    }
}
