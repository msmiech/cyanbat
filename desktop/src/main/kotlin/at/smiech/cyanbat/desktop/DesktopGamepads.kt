package at.smiech.cyanbat.desktop

import at.smiech.engine.InputMethod
import at.smiech.engine.impl.PadState
import org.lwjgl.sdl.SDLEvents.SDL_EVENT_FIRST
import org.lwjgl.sdl.SDLEvents.SDL_EVENT_LAST
import org.lwjgl.sdl.SDLEvents.SDL_FlushEvents
import org.lwjgl.sdl.SDLGamepad.SDL_CloseGamepad
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_BUTTON_BACK
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_BUTTON_EAST
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_BUTTON_START
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_TYPE_PS3
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_TYPE_PS4
import org.lwjgl.sdl.SDLGamepad.SDL_GAMEPAD_TYPE_PS5
import org.lwjgl.sdl.SDLGamepad.SDL_GamepadConnected
import org.lwjgl.sdl.SDLGamepad.SDL_GetGamepadAxis
import org.lwjgl.sdl.SDLGamepad.SDL_GetGamepadButton
import org.lwjgl.sdl.SDLGamepad.SDL_GetGamepadName
import org.lwjgl.sdl.SDLGamepad.SDL_GetGamepadType
import org.lwjgl.sdl.SDLGamepad.SDL_GetGamepads
import org.lwjgl.sdl.SDLGamepad.SDL_OpenGamepad
import org.lwjgl.sdl.SDLGamepad.SDL_RumbleGamepad
import org.lwjgl.sdl.SDLGamepad.SDL_SetGamepadEventsEnabled
import org.lwjgl.sdl.SDLGamepad.SDL_UpdateGamepads
import org.lwjgl.sdl.SDLInit.SDL_INIT_GAMEPAD
import org.lwjgl.sdl.SDLInit.SDL_Init
import org.lwjgl.sdl.SDLInit.SDL_Quit
import org.lwjgl.sdl.SDLJoystick.SDL_SetJoystickEventsEnabled
import org.lwjgl.sdl.SDLStdinc.SDL_free
import kotlin.math.abs

/**
 * The game controllers plugged into the computer, read through SDL 3 (by way of LWJGL), which knows
 * an Xbox pad, a DualSense and most others, and lays them all out alike.
 *
 * SDL 3 by LWJGL rather than Jamepad's SDL 2: Jamepad's Windows build leaves out SDL's HIDAPI
 * drivers, the ones that speak a PlayStation pad's own protocol, and without them a DualSense flies
 * the bat but cannot rumble. SDL 3 also says which kind of pad it is, rather than leaving it to its
 * name.
 *
 * Every connected pad counts, as one: a Steam Deck's own controls and a pad plugged into it both
 * fly the bat. Used from one thread only, the window's, which polls and rumbles.
 *
 * Started on the first poll; a computer SDL cannot be started on just has no pads.
 */
class DesktopGamepads {
    private var started = false
    private var running = false

    /** The pads open now, by SDL's id for each, in the order they were opened. */
    private val pads = LinkedHashMap<Int, Pad>()

    /** What the pads were doing at the last poll, merged. */
    private val merged = PadState()

    /** The pad last used, which the prompts stay with and [vibrate] rumbles; null for none yet. */
    private var lastUsed: Pad? = null

    /** Polls since the list of pads was last read for new ones. */
    private var sinceListed = LIST_EVERY_POLLS

    /** One open pad, and its kind, which its buttons are labeled by. */
    private class Pad(val handle: Long, val kind: InputMethod)

    /**
     * Reads every connected pad into one [PadState], or null when none is connected. The state is
     * this object's own, overwritten by the next poll.
     */
    fun poll(): PadState? {
        if (!start()) return null
        // Reads the pads afresh, and notices pads coming and going; their events are off, so
        // nothing else would.
        SDL_UpdateGamepads()
        // Nothing reads SDL's own event queue, so it is emptied before it can fill.
        SDL_FlushEvents(SDL_EVENT_FIRST, SDL_EVENT_LAST)
        dropDisconnected()
        if (++sinceListed >= LIST_EVERY_POLLS) {
            sinceListed = 0
            openNew()
        }
        if (pads.isEmpty()) return null

        var used: Pad? = null
        merged.clear()
        for (pad in pads.values) {
            if (read(pad.handle) && used == null) used = pad
        }
        val current = used ?: lastUsed ?: pads.values.first()
        lastUsed = current
        merged.method = current.kind
        return merged
    }

    /**
     * Adds [pad] to [merged]: any button either pad holds is held, and the stick pushed
     * further is the one that counts.
     *
     * @return whether the player is doing anything with it, which makes it the pad the prompts are
     *   for.
     */
    private fun read(pad: Long): Boolean {
        val x = axis(pad, SDL_GAMEPAD_AXIS_LEFTX)
        val y = axis(pad, SDL_GAMEPAD_AXIS_LEFTY)
        if (abs(x) > abs(merged.stickX)) merged.stickX = x
        if (abs(y) > abs(merged.stickY)) merged.stickY = y
        val left = SDL_GetGamepadButton(pad, SDL_GAMEPAD_BUTTON_DPAD_LEFT)
        val right = SDL_GetGamepadButton(pad, SDL_GAMEPAD_BUTTON_DPAD_RIGHT)
        val up = SDL_GetGamepadButton(pad, SDL_GAMEPAD_BUTTON_DPAD_UP)
        val down = SDL_GetGamepadButton(pad, SDL_GAMEPAD_BUTTON_DPAD_DOWN)
        val south = SDL_GetGamepadButton(pad, SDL_GAMEPAD_BUTTON_SOUTH)
        val east = SDL_GetGamepadButton(pad, SDL_GAMEPAD_BUTTON_EAST)
        val start = SDL_GetGamepadButton(pad, SDL_GAMEPAD_BUTTON_START)
        val select = SDL_GetGamepadButton(pad, SDL_GAMEPAD_BUTTON_BACK)
        merged.dpadLeft = merged.dpadLeft || left
        merged.dpadRight = merged.dpadRight || right
        merged.dpadUp = merged.dpadUp || up
        merged.dpadDown = merged.dpadDown || down
        merged.south = merged.south || south
        merged.east = merged.east || east
        merged.start = merged.start || start
        merged.select = merged.select || select
        return left || right || up || down || south || east || start || select ||
                abs(x) > ACTIVE_DEFLECTION || abs(y) > ACTIVE_DEFLECTION
    }

    /** One of [pad]'s axes, -1 to 1, y growing downward as the frame's does. */
    private fun axis(pad: Long, axis: Int): Float =
        (SDL_GetGamepadAxis(pad, axis) / Short.MAX_VALUE.toFloat()).coerceIn(-1f, 1f)

    /**
     * Rumbles the pad last used for [durationMillis]; a pad without motors stays still. Both
     * motors at once, heavy and light, as Android runs a controller's.
     */
    fun vibrate(durationMillis: Long) {
        val pad = lastUsed ?: return
        val duration = durationMillis.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        SDL_RumbleGamepad(pad.handle, RUMBLE_FULL, RUMBLE_FULL, duration)
    }

    /** Lets go of the pads and of SDL, as the game closes. */
    fun dispose() {
        pads.values.forEach { SDL_CloseGamepad(it.handle) }
        pads.clear()
        lastUsed = null
        if (running) SDL_Quit()
        running = false
    }

    private fun dropDisconnected() {
        val gone = pads.entries.filter { !SDL_GamepadConnected(it.value.handle) }
        for ((id, pad) in gone) {
            SDL_CloseGamepad(pad.handle)
            pads.remove(id)
            if (lastUsed === pad) lastUsed = null
        }
    }

    private fun openNew() {
        val ids = SDL_GetGamepads() ?: return
        try {
            for (i in 0 until ids.remaining()) {
                val id = ids.get(i)
                if (id in pads) continue
                val handle = SDL_OpenGamepad(id)
                if (handle != 0L) pads[id] = Pad(handle, kindOf(handle))
            }
        } finally {
            SDL_free(ids)
        }
    }

    /** A pad's kind by what SDL makes of it, and failing that by its name. */
    private fun kindOf(pad: Long): InputMethod = when (SDL_GetGamepadType(pad)) {
        SDL_GAMEPAD_TYPE_PS3, SDL_GAMEPAD_TYPE_PS4, SDL_GAMEPAD_TYPE_PS5 ->
            InputMethod.PLAYSTATION_PAD

        else -> InputMethod.padOf(SDL_GetGamepadName(pad))
    }

    private fun start(): Boolean {
        if (started) return running
        started = true
        running = try {
            // LWJGL reaches native memory through sun.misc.Unsafe unless told otherwise, which this
            // JDK warns of in the log, and a later one will refuse. Set before LWJGL first loads.
            if (System.getProperty(MEMORY_BACKEND) == null) {
                System.setProperty(MEMORY_BACKEND, "org.lwjgl.system.MemoryBackendFFM")
            }
            SDL_Init(SDL_INIT_GAMEPAD).also { ok ->
                if (ok) {
                    // Read by polling; queued, they would only pile up.
                    SDL_SetJoystickEventsEnabled(false)
                    SDL_SetGamepadEventsEnabled(false)
                } else {
                    System.err.println("CyanBat: game controllers unavailable - SDL would not start")
                }
            }
        } catch (exc: Throwable) {
            // No natives for this machine: the game is played without pads.
            System.err.println("CyanBat: game controllers unavailable - $exc")
            false
        }
        return running
    }

    private companion object {
        /** How often, in polls, the list of pads is read for new ones: about four times a second. */
        const val LIST_EVERY_POLLS = 30

        /** How far a stick has to lean for its pad to be taken as the one in the player's hands. */
        const val ACTIVE_DEFLECTION = 0.5f

        /**
         * Both motors' strength for a vibration: all of it, as SDL takes it, unsigned 16-bit in a
         * Short. A DualSense makes its rumble with the voice coils it plays haptics on, and a hit's
         * quarter second at four fifths of it was barely felt.
         */
        const val RUMBLE_FULL: Short = -1

        /** The system property LWJGL picks its native memory access by. */
        const val MEMORY_BACKEND = "org.lwjgl.system.memoryBackend"
    }
}
