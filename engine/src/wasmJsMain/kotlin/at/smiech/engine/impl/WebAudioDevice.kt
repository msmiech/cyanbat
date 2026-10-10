package at.smiech.engine.impl

import kotlinx.browser.document
import kotlinx.browser.window

/**
 * The page's sound output: one [AudioContext], which every [WebAudio] plays through, the menu's and
 * each run's. Make one per page.
 *
 * A browser keeps a page silent until the player does something on it, so the context starts
 * suspended, and every key and click tries to start it. It is suspended again while the page is
 * hidden, as an app's sound stops when it goes to the background: the run pauses then anyway, and a
 * hidden page's timers, which feed the music, are slowed to a crawl.
 *
 * It runs at the rate every sound of the game's is made at, so the music's chunks go out as they
 * are, with nothing resampled at their joins; the browser resamples the context's output as a
 * whole for the device.
 */
class WebAudioDevice {
    // A browser that refused the rate would resample every chunk on its own, which can be heard at
    // their joins, but that beats silence.
    internal val context: AudioContext =
        runCatching { AudioContext(audioContextOptions(SAMPLE_RATE)) }.getOrElse { AudioContext() }

    init {
        // Caught on the way down, since what the game does with an event may stop it there.
        for (type in GESTURES) window.addEventListener(type, { start() }, true)
        document.addEventListener("visibilitychange", {
            if (isPageHidden()) context.`suspend`() else start()
        })
    }

    private fun start() {
        if (context.state == "suspended" && !isPageHidden()) context.resume()
    }

    private companion object {
        /** The rate the game's music and effects are written at, by the scripts in tools/. */
        const val SAMPLE_RATE = 22_050

        /** The events a browser counts as the player's go-ahead to make sound. */
        val GESTURES = listOf("keydown", "mousedown", "pointerup", "touchend")
    }
}

/** Whether the page is out of sight: in a background tab, or a minimized window. */
private fun isPageHidden(): Boolean = js("document.hidden")
