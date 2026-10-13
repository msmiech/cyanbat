package at.smiech.cyanbat.ios

import at.smiech.cyanbat.data.AppLanguage
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

/**
 * The game's language on iOS: the app's own `AppleLanguages`, which is where the system's Settings
 * app keeps an app's language too, so the game's Settings and the system's are one setting, as they
 * are on Android 13 and later. Compose's resources, the menu's and the run's, read the language off
 * `NSLocale.preferredLanguages`, which puts the app's own first.
 */
internal object IosLocale {
    private const val KEY = "AppleLanguages"

    /**
     * The language chosen for this app, here or in the system's Settings; [AppLanguage.SYSTEM]
     * when none is. Read from the app's own defaults only, since the shared defaults always hold
     * the system's languages under the same key.
     */
    fun chosen(): AppLanguage {
        val domain = NSBundle.mainBundle.bundleIdentifier ?: return AppLanguage.SYSTEM
        val own = NSUserDefaults.standardUserDefaults.persistentDomainForName(domain)?.get(KEY)
        return AppLanguage.fromTag((own as? List<*>)?.firstOrNull() as? String)
    }

    fun apply(language: AppLanguage) {
        val defaults = NSUserDefaults.standardUserDefaults
        val tag = language.tag
        if (tag == null) {
            defaults.removeObjectForKey(KEY)
        } else {
            defaults.setObject(listOf(tag), KEY)
        }
    }
}
