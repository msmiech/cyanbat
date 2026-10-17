package at.smiech.cyanbat.ios

import at.smiech.cyanbat.data.KeyValueStorage
import platform.Foundation.NSUserDefaults

/**
 * The app's user defaults, which keep what the game remembers - settings, highscores, the stages
 * open, the achievements - in the app's own container, under the desktop's key names with a prefix of the game's.
 * The stores on it are shared with the browser (see KeyValueStores).
 *
 * The keys are read once, as the app starts, and kept in memory with every write: the defaults are
 * the app's alone, so nothing else changes them meanwhile.
 *
 * @param prefix put before every key, so the game's keep apart from the system's in the same
 *   defaults, such as the language (see [IosLocale]).
 */
internal class UserDefaultsStorage(private val prefix: String = "cyanbat.") : KeyValueStorage {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val values = HashMap<String, String>()

    init {
        for ((key, value) in defaults.dictionaryRepresentation()) {
            if (key is String && key.startsWith(prefix) && value is String) {
                values[key.removePrefix(prefix)] = value
            }
        }
    }

    override val keys: Set<String> get() = values.keys

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
        defaults.setObject(value, prefix + key)
    }
}
