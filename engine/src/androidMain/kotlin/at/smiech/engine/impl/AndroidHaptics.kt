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
 * [Haptics] for whatever the player is holding: the game controller they are playing with, when it
 * has rumble motors, and the device's own vibrator otherwise - for touch, a keyboard, or a pad
 * without motors, such as many of the controllers a phone clips into, through whose grip the
 * phone's own buzz is felt.
 *
 * Only one of the two buzzes. A player on a controller may have the phone on a table or a stand,
 * where it would rattle.
 *
 * Whether a controller's motors show up depends on the kernel having a force-feedback driver for
 * it. Android's common kernels have one for the DualSense and DualShock 4 (`hid-playstation`,
 * `hid-sony`) and for Xbox One and Series pads over Bluetooth (`hid-microsoft`).
 *
 * @param controllerInUse the id of the [InputDevice] the player last played with, or null when
 *   that was the touchscreen.
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
        // Looked up afresh every time, because the controller may have been switched off or
        // unpaired since it was last used; then the id finds nothing and the phone buzzes.
        val controller = controllerInUse()?.let(InputDevice::getDevice)
        if (controller == null || !controller.rumble(effect)) vibrator.vibrate(effect)
    }
}

/**
 * Runs [effect] on every motor a controller has at once, a pad's heavy one and its light one alike.
 * False when it has none, so the caller can buzz something else.
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
