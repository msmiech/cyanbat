package at.smiech.engine.impl

import java.util.concurrent.TimeUnit

/**
 * Gets javax.sound past the moment the system's audio devices change: a headset plugged in, or a
 * controller with a speaker of its own, such as a DualSense, which Windows makes the default output
 * as it connects.
 *
 * On Windows the JDK lists its DirectSound devices once, and lists them again only when their
 * number changes. It reads each device's formats as it first makes it, and keeps it. One made while
 * a device was still arriving keeps the formats it could read then, which can be none at all, and
 * every line asked of it after that fails as "not supported", for as long as the game runs. So
 * whatever fails to open a line says so here, and the JDK's list is dropped, to be made again from
 * the devices as they are now.
 *
 * Dropping it reaches into the JDK, which the app's launcher allows with
 * `--add-opens java.desktop/com.sun.media.sound`. Without that, or on a JDK laid out otherwise, it
 * does nothing, and lines are still asked for again, which outlasts any change that settles by
 * itself.
 */
internal object SoundDevices {
    /** When the list was last dropped, from [System.nanoTime]; 0 for never. */
    @Volatile
    private var lastForgotten = 0L

    /**
     * A line could not be had: drop the JDK's devices, unless that was done a moment ago, since
     * every sound and piece of music meets the same change at once.
     */
    fun lineFailed() {
        val now = System.nanoTime()
        val last = lastForgotten
        if (last != 0L && now - last < TimeUnit.MILLISECONDS.toNanos(FORGET_INTERVAL_MILLIS)) return
        lastForgotten = now
        forgetDirectSoundDevices()
    }

    private fun forgetDirectSoundDevices() {
        try {
            val provider = Class.forName("com.sun.media.sound.DirectAudioDeviceProvider")
            val infos = provider.getDeclaredField("infos").apply { isAccessible = true }
            // Every AudioSystem call makes a provider, which lists the devices again whenever its
            // list's length is not their number. An empty list never is, while there are any, and
            // unlike null it cannot fail a call already reading it. The provider guards the list
            // with its class's lock.
            val empty = java.lang.reflect.Array.newInstance(infos.type.componentType, 0)
            synchronized(provider) { infos.set(null, empty) }
        } catch (_: ReflectiveOperationException) {
            // Not this JDK's layout, or not Windows: nothing to drop.
        } catch (_: RuntimeException) {
            // Not opened to the game (InaccessibleObjectException): lines are still retried.
        }
    }

    /**
     * The least time between two drops of the list, so that a change every sound meets at once
     * drops it once.
     */
    private const val FORGET_INTERVAL_MILLIS = 2_000L
}
