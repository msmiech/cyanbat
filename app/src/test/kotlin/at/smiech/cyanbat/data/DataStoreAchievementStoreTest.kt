package at.smiech.cyanbat.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import at.smiech.cyanbat.Achievement
import at.smiech.engine.FrameCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [DataStoreAchievementStore]: what is earned, and the pictures it was earned with. */
class DataStoreAchievementStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + Job())

    /** The parent of every write the store launches, so a test can wait for them to land. */
    private val writes = Job()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(folder.root, "test.preferences_pb")
        }
    }

    private val screenshots by lazy { File(folder.root, "achievements") }

    private fun store() =
        DataStoreAchievementStore(dataStore, screenshots, CoroutineScope(Dispatchers.IO + writes))

    /** A picture whose file is [bytes]. */
    private class Picture(private vararg val bytes: Byte) : FrameCapture {
        override val width = 640
        override val height = 360
        override fun encodePng(): ByteArray = byteArrayOf(*bytes)
    }

    /** Earns, and waits for the write: unlockAsync deliberately returns before it lands. */
    private suspend fun DataStoreAchievementStore.earn(
        vararg achievements: Achievement,
        picture: FrameCapture?,
    ) {
        unlockAsync(achievements.toList(), picture)
        writes.children.toList().joinAll()
    }

    @AfterTest
    fun close() {
        writes.cancel()
        dataStoreScope.cancel()
    }

    @Test
    fun `achievements earned together share the picture of the moment`() = runBlocking {
        store().earn(Achievement.STAGE_1_EASY, Achievement.UNTOUCHABLE, picture = Picture(4, 2))

        val again = store()
        assertEquals(
            setOf(Achievement.STAGE_1_EASY, Achievement.UNTOUCHABLE),
            again.unlocked.first(),
        )
        assertContentEquals(byteArrayOf(4, 2), again.screenshot(Achievement.UNTOUCHABLE))
        assertEquals(listOf("stage_1_easy.png"), screenshots.list()!!.toList())
    }

    @Test
    fun `an achievement keeps the picture it was first earned with`() = runBlocking {
        val store = store()
        store.earn(Achievement.FIRST_FALL, picture = Picture(1))
        store.earn(Achievement.FIRST_FALL, Achievement.MAXED_OUT, picture = Picture(2))

        assertContentEquals(byteArrayOf(1), store.screenshot(Achievement.FIRST_FALL))
        assertContentEquals(byteArrayOf(2), store.screenshot(Achievement.MAXED_OUT))
    }

    @Test
    fun `one earned without a picture has none`() = runBlocking {
        val store = store()
        store.earn(Achievement.STAGE_3, picture = null)

        assertEquals(setOf(Achievement.STAGE_3), store.unlocked.first())
        assertNull(store.screenshot(Achievement.STAGE_3))
        assertNull(store.screenshot(Achievement.LEVEL_10))
    }
}
