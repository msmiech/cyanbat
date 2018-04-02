package at.grueneis.game.framework.code;

import java.util.List;

import android.content.Context;
import android.view.View;
import at.grueneis.game.framework.Input;

public class AndroidInput implements Input
{
	SensorHandler sensorHandler;
	TouchHandler touchHandler;
	
	public AndroidInput(Context context, View view, float scaleX, float scaleY)
	{
		sensorHandler = new SensorHandler(context);
		//keyHandler = new KeyboardHandler(view);
		//touchHandler = new SingleTouchHandler(view, scaleX, scaleY);
		touchHandler = new MultiTouchHandler(view, scaleX, scaleY);
	}
	
	public boolean isTouchDown(int pointer)
	{
		return touchHandler.isTouchDown(pointer);
	}
	
	public int getTouchX(int pointer)
	{
		return touchHandler.getTouchX(pointer);
	}
	
	public int getTouchY(int pointer)
	{
		return touchHandler.getTouchY(pointer);
	}
	
	public float getAccelX()
	{
		return sensorHandler.getGravityX();
	}
	
	public float getAccelY()
	{
		return sensorHandler.getGravityY();
	}
	
	public float getAccelZ()
	{
		return sensorHandler.getGravityZ();
	}
	
	public List<TouchEvent> getTouchEvents()
	{
		return touchHandler.getTouchEvents();
	}

	public int getPointerCount() {
		return touchHandler.getPointerCount();
	}
	
}
