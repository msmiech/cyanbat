package at.grueneis.game.framework.code

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class SensorHandler(context: Context) : SensorEventListener {
    var gravityX = 0f
        private set
    var gravityY = 0f
        private set
    var gravityZ = 0f
        private set

    override fun onAccuracyChanged(arg0: Sensor, arg1: Int) {}
    override fun onSensorChanged(ev: SensorEvent) {
        gravityX = ev.values[0]
        gravityY = ev.values[1]
        gravityZ = ev.values[2]
    }

    init {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).size > 0) {
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
}