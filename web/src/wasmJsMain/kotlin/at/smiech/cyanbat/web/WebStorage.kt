package at.smiech.cyanbat.web

import kotlinx.browser.localStorage
import org.w3c.dom.Storage

/**
 * The page's local storage, which keeps what the game remembers - settings, highscores, the stages
 * open - for this site in this browser, under keys of the game's own.
 *
 * A browser can refuse a page its storage, as some do in a private window, or run out of room for
 * it. The game then plays on, remembering everything for as long as the page is open: every value
 * is also kept in memory, which is what is read back.
 *
 * @param prefix put before every key, so the game's keys keep apart from anything else on the site.
 */
internal class WebStorage(private val prefix: String = "cyanbat.") {
    private val storage: Storage? = try {
        localStorage
    } catch (_: Throwable) {
        null
    }
    private val values = HashMap<String, String>()

    init {
        storage?.let { stored ->
            for (index in 0 until stored.length) {
                val key = stored.key(index) ?: continue
                if (key.startsWith(prefix)) {
                    stored.getItem(key)?.let { values[key.removePrefix(prefix)] = it }
                }
            }
        }
    }

    /** Every key the game has stored. */
    val keys: Set<String> get() = values.keys

    fun get(key: String): String? = values[key]

    fun getInt(key: String, default: Int): Int = values[key]?.toIntOrNull() ?: default

    fun getBoolean(key: String, default: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: default

    fun put(key: String, value: Any) {
        values[key] = value.toString()
        try {
            storage?.setItem(prefix + key, value.toString())
        } catch (_: Throwable) {
            // Out of room, or refused since the page opened; it is in memory all the same.
        }
    }
}
