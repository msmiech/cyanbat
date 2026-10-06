package at.smiech.cyanbat.resource

import at.smiech.cyanbat.resources.Res
import at.smiech.cyanbat.resources.allStringResources
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * The words a run puts on its frame, in the player's language.
 *
 * They are the menu's Compose resources, so a language's one strings.xml holds all of the game's
 * text. But Compose hands a string out only to a composable or a coroutine, and a run draws its text
 * on the game loop, a frame at a time. So the host reads every string once, before the run starts
 * ([load]), and the screens look them up here as they draw. The game's logic names its text by
 * [StringResource] and never holds a string of its own, so nothing but the drawing knows which
 * language it is in.
 */
class GameText(private val strings: Map<StringResource, String>) {

    /** [resource] in this language. */
    operator fun get(resource: StringResource): String =
        strings[resource] ?: error("No text for ${resource.key}")

    /**
     * [resource] with its placeholders filled in from [args]: `%1$d` or `%1$s` is the first, and
     * so on. Read the way Compose's own `stringResource(resource, args)` reads them, so a string
     * means the same whichever of the two draws it: nothing else is a placeholder, and a `%` on its
     * own is a percent sign.
     */
    fun format(resource: StringResource, vararg args: Any): String {
        val template = get(resource)
        if ('%' !in template) return template
        return PLACEHOLDER.replace(template) { args[it.groupValues[1].toInt() - 1].toString() }
    }

    companion object {
        private val PLACEHOLDER = Regex("""%(\d+)\$[ds]""")

        /**
         * Every string, in the language the platform is set to - on Android the app's own, where
         * the player has picked one for it.
         *
         * Every one, rather than a list of the run's: a list is one more thing to keep in step, and
         * one it missed would fail only when that line was drawn. The environment is read once,
         * because reading it is what costs.
         */
        suspend fun load(): GameText {
            val environment = getSystemResourceEnvironment()
            return GameText(Res.allStringResources.values.associateWith { getString(environment, it) })
        }
    }
}
