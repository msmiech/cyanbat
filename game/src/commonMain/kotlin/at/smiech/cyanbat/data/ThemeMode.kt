package at.smiech.cyanbat.data

/**
 * Whether the menu - its screens and its dialogs - is drawn light or dark. The player's choice, in
 * Settings; until they make one it follows the system, as the rest of their apps do.
 *
 * The game itself is not themed. It draws its own frame, the same in either.
 */
enum class ThemeMode {
    /** Light or dark as the system is set, and changing when it does. */
    SYSTEM,
    DARK,
    LIGHT;

    /** Whether the menu is drawn dark, given whether the system's own theme is dark. */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        DARK -> true
        LIGHT -> false
    }

    companion object {
        val DEFAULT = SYSTEM

        /** The theme stored as [name], or [DEFAULT] for anything unrecognized - a newer build's, say. */
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
