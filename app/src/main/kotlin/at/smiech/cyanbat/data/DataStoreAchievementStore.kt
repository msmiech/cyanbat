package at.smiech.cyanbat.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.smiech.cyanbat.Achievement
import at.smiech.cyanbat.AchievementStore
import at.smiech.engine.FrameCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [AchievementStore] backed by Jetpack DataStore, in the same file as the highscores, with the
 * pictures as PNG files in [screenshots], the app's own folder for them.
 *
 * Each achievement earned is kept under its key with the name of its picture as the value, empty for
 * none, as the other platforms keep them; achievements earned together share one picture, kept once
 * under the first one's key. The picture is written before the achievement is, so a menu that sees
 * the achievement finds its picture.
 *
 * Owns a process-lifetime scope, like [DataStoreHighscoreStore], so an achievement earned as the
 * run ends still lands, picture and all, after the game activity has gone.
 */
class DataStoreAchievementStore(
    private val dataStore: DataStore<Preferences>,
    private val screenshots: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AchievementStore {

    override val unlocked: Flow<Set<Achievement>> = dataStore.data.map { preferences ->
        preferences.asMap().keys.mapNotNull { key ->
            key.name.takeIf { it.startsWith(KEY_PREFIX) }
                ?.let { Achievement.ofKey(it.removePrefix(KEY_PREFIX)) }
        }.toSet()
    }

    override fun unlockAsync(achievements: Collection<Achievement>, screenshot: FrameCapture?) {
        scope.launch {
            // Made before anything is known to be new: it is the slow part, and it has to happen
            // while the frame it was taken from is still held.
            val png = screenshot?.let { runCatching { it.encodePng() }.getOrNull() }
            dataStore.edit { preferences ->
                val new = achievements.distinct().filter { preferences[keyOf(it)] == null }
                if (new.isEmpty()) return@edit
                val picture = new.first().key
                val kept = png != null && runCatching { write(picture, png) }.isSuccess
                new.forEach { preferences[keyOf(it)] = if (kept) picture else "" }
            }
        }
    }

    override suspend fun screenshot(achievement: Achievement): ByteArray? {
        val picture = dataStore.data.first()[keyOf(achievement)]?.takeIf { it.isNotEmpty() }
            ?: return null
        return withContext(Dispatchers.IO) {
            File(screenshots, "$picture.png").takeIf { it.isFile }?.readBytes()
        }
    }

    /**
     * Writes [png] as the picture called [name], aside first and then moved into place, so an app
     * killed partway through leaves no half a picture under the name.
     */
    private fun write(name: String, png: ByteArray) {
        screenshots.mkdirs()
        val part = File(screenshots, "$name.png.part")
        part.writeBytes(png)
        check(part.renameTo(File(screenshots, "$name.png"))) { "Could not keep the picture $name" }
    }

    private companion object {
        /** An achievement is kept under this and its key, as on the other platforms. */
        const val KEY_PREFIX = "achievement_"

        fun keyOf(achievement: Achievement) = stringPreferencesKey(KEY_PREFIX + achievement.key)
    }
}
