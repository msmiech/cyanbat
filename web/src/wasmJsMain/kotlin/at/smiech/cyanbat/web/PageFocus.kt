package at.smiech.cyanbat.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Whether the player is at the page: shown rather than in a background tab, and in the window that
 * has the focus. The game surface pauses the run when they leave, as the desktop's does when its
 * window loses the focus; the page reads it off the browser's own events rather than leave it to
 * Compose's window info.
 *
 * A key or a click on the page counts as the player being there too, whatever the browser last
 * said, so a focus event it never sent cannot leave every run starting paused.
 *
 * Make one per page: its listeners last as long as the page does.
 */
internal class PageFocus {
    var isFocused by mutableStateOf(!isPageHidden() && document.hasFocus())
        private set

    init {
        window.addEventListener("blur", { isFocused = false })
        window.addEventListener("focus", { isFocused = !isPageHidden() })
        document.addEventListener("visibilitychange", {
            isFocused = !isPageHidden() && document.hasFocus()
        })
        // Caught on the way down, ahead of anything the game does with them.
        for (type in listOf("keydown", "pointerdown")) {
            window.addEventListener(type, { isFocused = !isPageHidden() }, true)
        }
    }
}

/** Whether the page is out of sight: in a background tab, or a minimized window. */
private fun isPageHidden(): Boolean = js("document.hidden")
