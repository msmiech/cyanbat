package at.smiech.cyanbat.data

/**
 * Whether the menu, screens and dialogs alike, is drawn light or dark. Chosen in Settings; until
 * then it follows the system. The game's own frame is not themed.
 */
enum class ThemeMode {
    /** Light or dark as the system is set, and changing when it does. */
    SYSTEM,

    /** Always dark. */
    DARK,

    /** Always light. */
    LIGHT;

    /** Whether the menu is drawn dark, given whether the system's own theme is dark. */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        DARK -> true
        LIGHT -> false
    }

    companion object {
        /** The theme until the player chooses one. */
        val DEFAULT = SYSTEM

        /**
         * The theme stored as [name], or [DEFAULT] for anything unrecognized, such
         * as a newer build's.
         */
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
