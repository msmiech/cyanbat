package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.data.AppLanguage
import java.util.Locale

/**
 * The game's language on the desktop: the JVM's default locale, which Compose's resources, the
 * menu's and the run's, read the language off. Nothing on the desktop changes the default but this.
 */
internal object DesktopLocale {
    /** The default the JVM started with, the system's, to go back to for [AppLanguage.SYSTEM]. */
    private val system: Locale = Locale.getDefault()

    fun apply(language: AppLanguage) {
        Locale.setDefault(language.tag?.let(Locale::forLanguageTag) ?: system)
    }
}
