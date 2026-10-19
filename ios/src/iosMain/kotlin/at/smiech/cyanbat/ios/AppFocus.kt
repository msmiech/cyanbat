package at.smiech.cyanbat.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification

/**
 * Whether the player is at the app: in front, and not under a call, the notification center or the
 * app switcher. The game surface pauses the run when they leave, as Android's activity does in
 * onPause; the app reads it off UIKit's own notifications rather than leave it to Compose's window
 * info, as the browser's page does off the browser's events.
 *
 * Make one per app: its observers last as long as the app does.
 */
internal class AppFocus {
    var isFocused by mutableStateOf(
        UIApplication.sharedApplication.applicationState ==
                UIApplicationState.UIApplicationStateActive
    )
        private set

    init {
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        center.addObserverForName(UIApplicationWillResignActiveNotification, null, main) { _ ->
            isFocused = false
        }
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, main) { _ ->
            isFocused = true
        }
    }
}
