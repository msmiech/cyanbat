package at.smiech.cyanbat.data

import at.smiech.cyanbat.Achievement
import at.smiech.cyanbat.AchievementStore
import at.smiech.engine.FrameCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

/**
 * Where a host keeps the achievements' pictures: PNG files, each under a name of its own. A file
 * where the platform has them; the page's storage in a browser, which has none.
 */
interface ScreenshotStorage {
    /** Keeps [png] under [name], in place of anything kept there before. */
    fun write(name: String, png: ByteArray)

    /** What is kept under [name], or null for nothing. */
    fun read(name: String): ByteArray?
}

/**
 * [ScreenshotStorage] in a [KeyValueStorage], as text: for the browser, whose page has nowhere else
 * to keep a file. A picture of the frame is a few dozen kilobytes, and there are few achievements,
 * well inside what a browser gives a page.
 */
class KeyValueScreenshotStorage(private val storage: KeyValueStorage) : ScreenshotStorage {
    override fun write(name: String, png: ByteArray) =
        storage.put(KEY_PREFIX + name, Base64.encode(png))

    override fun read(name: String): ByteArray? =
        storage.get(KEY_PREFIX + name)?.let { runCatching { Base64.decode(it) }.getOrNull() }

    private companion object {
        const val KEY_PREFIX = "screenshot_"
    }
}

/**
 * [AchievementStore] on a [KeyValueStorage], next to the highscores, with the pictures in
 * [screenshots]: the desktop's, the browser's and iOS's.
 *
 * Each achievement earned is kept under its key, with the name of its picture as the value, empty for
 * none. Achievements earned together share a picture, kept once, under the first one's key.
 *
 * The achievement is kept at once, so the menu has it however soon the run is left. Its picture is
 * made into a file in [scope], which outlives the run, off the game's thread where the platform has
 * another: a PNG takes longer than a frame.
 */
class KeyValueAchievementStore(
    private val storage: KeyValueStorage,
    private val screenshots: ScreenshotStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AchievementStore {
    private val earned = MutableStateFlow(
        storage.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { Achievement.ofKey(it.removePrefix(KEY_PREFIX)) }
            .toSet()
    )

    override val unlocked: Flow<Set<Achievement>> = earned.asStateFlow()

    override fun unlockAsync(achievements: Collection<Achievement>, screenshot: FrameCapture?) {
        val new = achievements.distinct().filter { storage.get(KEY_PREFIX + it.key) == null }
        if (new.isEmpty()) return
        val picture = if (screenshot != null) new.first().key else ""
        new.forEach { storage.put(KEY_PREFIX + it.key, picture) }
        earned.value += new
        if (screenshot == null) return
        scope.launch {
            // An achievement whose picture could not be made is still earned, only without it.
            runCatching { screenshots.write(picture, screenshot.encodePng()) }
        }
    }

    override suspend fun screenshot(achievement: Achievement): ByteArray? {
        val picture = storage.get(KEY_PREFIX + achievement.key)?.takeIf { it.isNotEmpty() }
            ?: return null
        return withContext(Dispatchers.Default) { screenshots.read(picture) }
    }

    private companion object {
        /** An achievement is kept under this and its key. */
        const val KEY_PREFIX = "achievement_"
    }
}
