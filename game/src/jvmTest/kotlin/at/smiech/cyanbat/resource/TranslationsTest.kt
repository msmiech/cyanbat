package at.smiech.cyanbat.resource

import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.pause_title
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every language says everything the English does, with the same blanks to fill in.
 *
 * Read off the files rather than through Compose, because Compose falls back on English for a
 * string a language lacks: a missing translation would put an English line in the middle of the
 * German, and nothing would fail.
 */
class TranslationsTest {

    /** The strings in [folder], by name; those marked untranslatable only where [translatable]. */
    private fun strings(folder: String, translatable: Boolean = false): Map<String, String> {
        val file = File(COMPOSE_RESOURCES, "$folder/strings.xml")
        assertTrue(file.isFile, "${file.path} is missing")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filterNot { translatable && it.attributes.getNamedItem("translatable")?.nodeValue == "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }

    private fun placeholders(text: String): Set<String> =
        PLACEHOLDER.findAll(text).map { it.value }.toSet()

    @Test
    fun `every language has every string and none that English lacks`() {
        // So that a scan of the wrong folder, finding nothing, cannot pass for a clean one.
        assertTrue(TRANSLATIONS.containsAll(listOf("values-de", "values-pl")), "found only $TRANSLATIONS")
        val english = strings("values", translatable = true).keys
        for (folder in TRANSLATIONS) {
            val keys = strings(folder).keys
            assertEquals(emptySet(), english - keys, "$folder is missing these")
            assertEquals(emptySet(), keys - english, "$folder has these, which English does not")
        }
    }

    @Test
    fun `every translation leaves the same blanks as the English`() {
        val english = strings("values")
        for (folder in TRANSLATIONS) {
            for ((name, translated) in strings(folder)) {
                assertEquals(
                    placeholders(english.getValue(name)),
                    placeholders(translated),
                    "$folder's $name: '$translated'",
                )
            }
        }
    }

    /** What a card fills in comes from the power-up, so every language has to ask for it all. */
    @Test
    fun `every power-up card asks for the numbers its power-up quotes`() {
        for (folder in listOf("values") + TRANSLATIONS) {
            val strings = strings(folder)
            for (powerUp in PowerUp.entries) {
                val wanted = (1..powerUp.numbers.size).map { "%$it\$d" }.toSet()
                assertEquals(
                    wanted,
                    placeholders(strings.getValue(powerUp.description.key)),
                    "$folder's ${powerUp.description.key}",
                )
            }
        }
    }

    @Test
    fun `a run reads its text in the language the platform is set to`() {
        assertEquals("PAUSED", textIn(Locale.US)[Res.string.pause_title])
        assertEquals("PAUSE", textIn(Locale.GERMANY)[Res.string.pause_title])
        assertEquals("PAUZA", textIn(Locale.forLanguageTag("pl-PL"))[Res.string.pause_title])
        // A language of another region still finds its own, and one the game does not speak
        // finds English.
        assertEquals("PAUSE", textIn(Locale.forLanguageTag("de-AT"))[Res.string.pause_title])
        assertEquals("PAUSED", textIn(Locale.JAPAN)[Res.string.pause_title])
    }

    private companion object {
        /** As GameText and Compose read them. */
        val PLACEHOLDER = Regex("""%(\d+)\$[ds]""")
    }
}
