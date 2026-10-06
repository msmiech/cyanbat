package at.smiech.cyanbat.resource

import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale

/** Where the game's strings are, from the module the tests run in. */
internal val COMPOSE_RESOURCES = File("src/commonMain/composeResources")

/**
 * Each translation's folder, `values-de` and the like: every one there is, so a language added
 * later is checked with the rest without anyone having to list it here.
 */
internal val TRANSLATIONS: List<String> =
    COMPOSE_RESOURCES.listFiles { file -> file.name.matches(Regex("values-[a-z]{2,3}")) }!!
        .map { it.name }
        .sorted()

/** The languages the game speaks, as a platform set to each of them names it: English first. */
internal val LANGUAGES: List<Locale> =
    listOf(Locale.US) + TRANSLATIONS.map { Locale.forLanguageTag(it.removePrefix("values-")) }

/**
 * The game's text as a platform set to [locale] reads it. Compose reads the language off the JVM's
 * default, so this sets that for the load and puts it back after.
 */
internal fun textIn(locale: Locale): GameText {
    val before = Locale.getDefault()
    Locale.setDefault(locale)
    try {
        return runBlocking { GameText.load() }
    } finally {
        Locale.setDefault(before)
    }
}
