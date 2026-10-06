package at.smiech.engine.impl

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import at.smiech.engine.Haptics

/**
 * [Haptics] for whatever the player is holding: the game controller in use when it has rumble
 * motors, otherwise the device's own vibrator. That covers touch, keyboards and motorless pads,
 * including many clip-on controllers, through whose grip the phone's buzz is felt.
 *
 * Only one of the two vibrates: a player on a controller may have the phone on a table, where it
 * would rattle.
 *
 * A controller's motors only show up if the kernel has a force-feedback driver for it. Android's
 * common kernels have one for the DualSense and DualShock 4 (`hid-playstation`, `hid-sony`) and for
 * Xbox One and Series pads over Bluetooth (`hid-microsoft`).
 *
 * @param controllerInUse the id of the [InputDevice] the player last used, or null for the
 *   touchscreen.
 */
class AndroidHaptics(
    context: Context,
    private val controllerInUse: () -> Int?,
) : Haptics {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun vibrate(durationMillis: Long) {
        val effect =
            VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
        // Looked up on every call, because the controller may have been switched off or unpaired
        // since; then the id finds nothing and the phone vibrates instead.
        val controller = controllerInUse()?.let(InputDevice::getDevice)
        if (controller == null || !controller.rumble(effect)) vibrator.vibrate(effect)
    }
}

/**
 * Runs [effect] on all of a controller's motors at once, heavy and light alike. Returns false when
 * it has none, so the caller can vibrate something else.
 */
private fun InputDevice.rumble(effect: VibrationEffect): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val motors = vibratorManager
        if (motors.vibratorIds.isEmpty()) return false
        motors.vibrate(CombinedVibration.createParallel(effect))
    } else {
        // Before Android 12 a device has one Vibrator, standing for all of its motors together.
        @Suppress("DEPRECATION")
        val motors = vibrator
        if (!motors.hasVibrator()) return false
        motors.vibrate(effect)
    }
    return true
}
