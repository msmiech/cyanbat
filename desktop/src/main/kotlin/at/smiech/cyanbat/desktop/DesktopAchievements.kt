package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.data.KeyValueAchievementStore
import at.smiech.cyanbat.data.KeyValueStorage
import at.smiech.cyanbat.data.ScreenshotStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.prefs.Preferences

/**
 * The achievements, as the browser and iOS keep them: which are earned in the game's preferences,
 * next to the highscores, and their pictures as PNG files in [screenshots]. One instance is shared
 * between the menu and every run, as the other stores are, since its flow mirrors what it wrote.
 *
 * @param prefs the node to keep it in: the game's own unless a test hands it one of its own.
 * @param screenshots where the pictures go: the game's folder of them unless a test hands it one.
 * @param scope where the pictures are made into files: off the game's thread, unless a test wants
 *   them there at once.
 */
fun desktopAchievementStore(
    prefs: Preferences = Preferences.userRoot().node("at/smiech/cyanbat"),
    screenshots: File = File(gameDataDirectory(), "achievements"),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
): KeyValueAchievementStore =
    KeyValueAchievementStore(PreferencesStorage(prefs), FileScreenshotStorage(screenshots), scope)

/** A [KeyValueStorage] on a node of the JDK's preferences. */
internal class PreferencesStorage(private val prefs: Preferences) : KeyValueStorage {
    override val keys: Set<String> get() = prefs.keys().toSet()

    override fun get(key: String): String? = prefs.get(key, null)

    override fun put(key: String, value: String) = prefs.put(key, value)
}

/** [ScreenshotStorage] as PNG files in [directory], made the first time one is written. */
internal class FileScreenshotStorage(private val directory: File) : ScreenshotStorage {
    override fun write(name: String, png: ByteArray) {
        directory.mkdirs()
        // Written aside and moved into place, so a game closed partway through leaves no half a
        // picture behind under the name.
        val part = File(directory, "$name.png.part")
        part.writeBytes(png)
        val file = File(directory, "$name.png")
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    override fun read(name: String): ByteArray? =
        File(directory, "$name.png").takeIf { it.isFile }?.readBytes()
}

/**
 * Where the game keeps files of its own on this computer, as each system expects: the roaming
 * application data on Windows, Application Support on a Mac, and the XDG data folder elsewhere.
 */
internal fun gameDataDirectory(): File {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.startsWith("windows") ->
            File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "CyanBat")

        os.startsWith("mac") -> File(home, "Library/Application Support/CyanBat")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "cyanbat")
    }
}
