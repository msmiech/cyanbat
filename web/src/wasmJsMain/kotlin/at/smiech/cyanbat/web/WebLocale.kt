package at.smiech.cyanbat.web

import at.smiech.cyanbat.data.AppLanguage
import kotlinx.browser.document

/**
 * The game's language in a browser. Compose's resources, the menu's and the run's, read it off the
 * browser's own languages, which nothing on a page can change; so the page puts the player's choice
 * in front of them, through a global its script reads (see index.html). Nothing else sets it.
 */
internal object WebLocale {
    fun apply(language: AppLanguage) {
        setChosenLanguage(language.tag)
        // For whatever reads the page aloud.
        document.documentElement?.setAttribute("lang", language.tag ?: browserLanguage())
    }
}

/** Puts [tag] in front of the browser's languages, or with null takes it away again. */
@Suppress("UNUSED_PARAMETER")
private fun setChosenLanguage(tag: String?): Unit = js("{ window.cyanbatLanguage = tag; }")

/** The browser's own first language, which the page's script leaves alone. */
private fun browserLanguage(): String = js("window.cyanbatBrowserLanguage()")
