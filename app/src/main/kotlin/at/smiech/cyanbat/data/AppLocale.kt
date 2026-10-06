package at.smiech.cyanbat.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.PREFS_KEY_LANGUAGE
import at.smiech.cyanbat.dataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * The game's language on Android: the player's choice of it, kept, and put into effect.
 *
 * From Android 13 an app has a language of its own, which the system keeps, puts into effect, and
 * offers in its own settings as well (the app lists its languages for it; see generateLocaleConfig).
 * There the game's setting is that one: a choice is handed to the system and read back from it, so
 * the two are one setting and cannot disagree.
 *
 * Before 13 there is no such thing. The choice is kept in DataStore and put into effect here, on
 * the JVM's default locale, which Compose's resources and the run's text read the language off.
 * Android sets that default back to the system's at every configuration change, so each activity
 * puts the choice back in its onConfigurationChanged ([reapply]), before anything reads a string.
 *
 * A language reaches [language] only once it is in effect, because the menu redraws itself on
 * hearing of one and has to redraw in it: from 13, that is when the system's configuration change
 * for it arrives.
 */
internal object AppLocale {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private val perApp: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private val current = MutableStateFlow(AppLanguage.DEFAULT)

    /** The language in effect. */
    val language: StateFlow<AppLanguage> = current.asStateFlow()

    /**
     * From 13, where the choice is kept; the application's, so it outlives the activity that
     * started it. Settings, which sets a language, has no context of its own to find it by.
     */
    private var system: LocaleManager? = null

    /** Before 13, where the choice is kept. */
    private var store: DataStore<Preferences>? = null

    /** Whether the stored choice has been read, which before 13 happens once a process. */
    private var read = false

    /**
     * Puts the language into effect for an activity starting, before it reads a string. Blocking the
     * first time before 13, when it reads the choice out of DataStore: nothing can be drawn without it.
     */
    fun start(context: Context) {
        if (perApp) {
            val system = context.applicationContext.getSystemService(LocaleManager::class.java)
            this.system = system
            current.value = choiceIn(system)
            return
        }
        val store = context.dataStore.also { store = it }
        if (!read) {
            current.value =
                runBlocking { AppLanguage.fromName(store.data.first()[PREFS_KEY_LANGUAGE]) }
            read = true
        }
        putIntoEffect(current.value)
    }

    /** After a configuration change: before 13 the choice goes back on, from 13 it is read again. */
    fun reapply() {
        if (perApp) {
            system?.let { current.value = choiceIn(it) }
        } else {
            putIntoEffect(current.value)
        }
    }

    suspend fun set(language: AppLanguage) {
        if (perApp) {
            // The configuration change this sets off is what tells [language], through [reapply].
            system?.applicationLocales =
                language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        } else {
            store?.edit { it[PREFS_KEY_LANGUAGE] = language.name } ?: return
            putIntoEffect(language)
            current.value = language
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun choiceIn(system: LocaleManager): AppLanguage {
        val locales = system.applicationLocales
        return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].toLanguageTag())
    }

    /** Sets the JVM's default to [language], or back to the system's own for [AppLanguage.SYSTEM]. */
    private fun putIntoEffect(language: AppLanguage) {
        LocaleList.setDefault(
            language.tag?.let { LocaleList(Locale.forLanguageTag(it)) }
                ?: Resources.getSystem().configuration.locales
        )
    }
}
