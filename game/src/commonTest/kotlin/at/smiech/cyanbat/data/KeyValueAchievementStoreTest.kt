package at.smiech.cyanbat.data

import at.smiech.cyanbat.Achievement
import at.smiech.engine.FrameCapture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyValueAchievementStoreTest {

    /** A picture whose file is [bytes], counting how often it is made into one. */
    private class Picture(private vararg val bytes: Byte) : FrameCapture {
        var encoded = 0
        override val width = 640
        override val height = 360
        override fun encodePng(): ByteArray {
            encoded++
            return byteArrayOf(*bytes)
        }
    }

    /** [ScreenshotStorage] in memory, counting the files written. */
    private class MemoryScreenshots : ScreenshotStorage {
        val files = HashMap<String, ByteArray>()
        override fun write(name: String, png: ByteArray) {
            files[name] = png
        }

        override fun read(name: String): ByteArray? = files[name]
    }

    private fun TestScope.store(
        storage: KeyValueStorage,
        screenshots: ScreenshotStorage,
    ) = KeyValueAchievementStore(storage, screenshots, scope = this)

    @Test
    fun anAchievementAndItsPictureAreReadBackByTheNextLaunch() = runTest {
        val storage = MemoryKeyValueStorage()
        val screenshots = MemoryScreenshots()
        store(storage, screenshots)
            .unlockAsync(listOf(Achievement.FIRST_FALL), Picture(1, 2, 3))
        testScheduler.advanceUntilIdle()

        val again = store(storage, screenshots)
        assertEquals(setOf(Achievement.FIRST_FALL), again.unlocked.first())
        assertContentEquals(byteArrayOf(1, 2, 3), again.screenshot(Achievement.FIRST_FALL))
        assertNull(again.screenshot(Achievement.MAXED_OUT))
    }

    @Test
    fun achievementsEarnedTogetherShareOnePicture() = runTest {
        val screenshots = MemoryScreenshots()
        val store = store(MemoryKeyValueStorage(), screenshots)
        val picture = Picture(7)
        store.unlockAsync(
            listOf(Achievement.STAGE_1_EASY, Achievement.STAGE_1_NORMAL, Achievement.UNTOUCHABLE),
            picture,
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, picture.encoded)
        assertEquals(1, screenshots.files.size)
        for (achievement in listOf(Achievement.STAGE_1_NORMAL, Achievement.UNTOUCHABLE)) {
            assertContentEquals(byteArrayOf(7), store.screenshot(achievement))
        }
    }

    @Test
    fun anAchievementKeepsThePictureItWasFirstEarnedWith() = runTest {
        val screenshots = MemoryScreenshots()
        val store = store(MemoryKeyValueStorage(), screenshots)
        store.unlockAsync(listOf(Achievement.LEVEL_10), Picture(1))
        testScheduler.advanceUntilIdle()

        val later = Picture(2)
        store.unlockAsync(listOf(Achievement.LEVEL_10, Achievement.LEVEL_20), later)
        testScheduler.advanceUntilIdle()

        assertContentEquals(byteArrayOf(1), store.screenshot(Achievement.LEVEL_10))
        assertContentEquals(byteArrayOf(2), store.screenshot(Achievement.LEVEL_20))

        val again = Picture(3)
        store.unlockAsync(listOf(Achievement.LEVEL_20), again)
        testScheduler.advanceUntilIdle()
        assertEquals(0, again.encoded, "nothing new was earned, so there is nothing to keep")
    }

    @Test
    fun anAchievementEarnedWithoutAPictureHasNone() = runTest {
        val store = store(MemoryKeyValueStorage(), MemoryScreenshots())
        store.unlockAsync(listOf(Achievement.STAGE_2), screenshot = null)
        testScheduler.advanceUntilIdle()

        assertEquals(setOf(Achievement.STAGE_2), store.unlocked.first())
        assertNull(store.screenshot(Achievement.STAGE_2))
    }

    @Test
    fun anAchievementThisVersionDoesNotKnowIsLeftAlone() = runTest {
        val storage = MemoryKeyValueStorage()
        storage.put("achievement_from_the_future", "")
        storage.put("achievement_first_fall", "")

        val store = store(storage, MemoryScreenshots())
        assertEquals(setOf(Achievement.FIRST_FALL), store.unlocked.first())
        assertTrue("achievement_from_the_future" in storage.keys)
    }

    @Test
    fun aPictureIsKeptInTheStorageAsText() = runTest {
        val storage = MemoryKeyValueStorage()
        val screenshots = KeyValueScreenshotStorage(storage)
        val png = ByteArray(300) { (it * 37).toByte() }
        screenshots.write("first_fall", png)

        assertContentEquals(png, KeyValueScreenshotStorage(storage).read("first_fall"))
        assertNull(screenshots.read("level_10"))
    }
}
