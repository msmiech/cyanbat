package at.smiech.cyanbat.resource

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.allStringResources
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * The text a run draws on its frame, in the player's language.
 *
 * These are the menu's Compose resources, so one strings.xml per language holds all
 * the game's text. Compose only hands strings to composables and coroutines, while
 * a run draws on the game loop, so the host reads every string once before the run
 * starts ([load]) and the screens look them up here. The game's logic names its
 * text by [StringResource], so only the drawing knows the language.
 */
class GameText(private val strings: Map<StringResource, String>) {

    /** [resource] in this language. */
    operator fun get(resource: StringResource): String =
        strings[resource] ?: error("No text for ${resource.key}")

    /**
     * [resource] with its placeholders filled from [args]: `%1$d` or `%1$s` is the first, and so
     * on. Parsed as Compose's `stringResource(resource, args)` parses them, so a string means the
     * same either way: nothing else is a placeholder, and a lone `%` is a percent sign.
     */
    fun format(resource: StringResource, vararg args: Any): String {
        val template = get(resource)
        if ('%' !in template) return template
        return PLACEHOLDER.replace(template) { args[it.groupValues[1].toInt() - 1].toString() }
    }

    companion object {
        private val PLACEHOLDER = Regex("""%(\d+)\$[ds]""")

        /**
         * Every string, in the language currently in effect.
         *
         * All of them rather than a list of the run's, which would be one more thing to keep in
         * step and would fail only when a missing line was drawn. The environment is read once,
         * because reading it is the costly part.
         */
        suspend fun load(): GameText {
            val environment = getSystemResourceEnvironment()
            return GameText(Res.allStringResources.values.associateWith {
                getString(
                    environment,
                    it
                )
            })
        }
    }
}
