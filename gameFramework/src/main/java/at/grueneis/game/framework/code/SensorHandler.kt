package at.grueneis.game.framework.code;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class SensorHandler implements SensorEventListener
{
	private float gravityX = 0;
	private float gravityY = 0;
	private float gravityZ = 0;
	
	public SensorHandler(Context context)
	{
		SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		if (sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).size() > 0)
		{
			sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
		}
	}
	
	public void onAccuracyChanged(Sensor arg0, int arg1)
	{
	}
	
	public void onSensorChanged(SensorEvent ev)
	{
		gravityX = ev.values[0];
		gravityY = ev.values[1];
		gravityZ = ev.values[2];
	}
	
	public float getGravityX()
	{
		return gravityX;
	}
	
	public float getGravityY()
	{
		return gravityY;
	}
	
	public float getGravityZ()
	{
		return gravityZ;
	}
	
}
