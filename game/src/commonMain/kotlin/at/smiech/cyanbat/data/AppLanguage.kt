package at.smiech.cyanbat.data

/**
 * Which language the game is in: the player's choice, in Settings. Until they make one it follows
 * the system, as the rest of their apps do.
 *
 * Every language here has its strings in the shared resources, and every one there is here;
 * `TranslationsTest` holds the two together.
 *
 * @param tag the language's BCP 47 tag; null for [SYSTEM], which has none of its own.
 */
enum class AppLanguage(val tag: String?) {
    /** Whichever language the system is set to, and changing when it does. */
    SYSTEM(null),
    ENGLISH("en"),
    GERMAN("de"),
    POLISH("pl");

    companion object {
        val DEFAULT = SYSTEM

        /** The language stored as [name], or [DEFAULT] for anything unrecognized - a newer build's, say. */
        fun fromName(name: String?): AppLanguage =
            entries.firstOrNull { it.name == name } ?: DEFAULT

        /**
         * The language a locale's [tag] picks, by its language alone, so Austrian German is German;
         * [SYSTEM] for none, or for one the game does not speak.
         */
        fun fromTag(tag: String?): AppLanguage {
            val language =
                tag?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return SYSTEM
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}
