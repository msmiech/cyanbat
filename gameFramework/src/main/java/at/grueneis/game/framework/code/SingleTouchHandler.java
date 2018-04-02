package at.grueneis.game.framework.code;

import java.util.ArrayList;
import java.util.List;

import android.view.MotionEvent;
import android.view.View;
import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.code.Pool.PoolObjectFactory;

public class SingleTouchHandler implements TouchHandler
{
	boolean isTouched;
	int touchX;
	int touchY;
	Pool<TouchEvent> touchEventPool;
	List<TouchEvent> touchEventBuffer = new ArrayList<TouchEvent>();
	List<TouchEvent> touchEvents = new ArrayList<TouchEvent>();
	float scaleX;
	float scaleY;
	
	public SingleTouchHandler(View view, float scaleX, float scaleY)
	{
		PoolObjectFactory<TouchEvent> factory = new PoolObjectFactory<TouchEvent>() {
			
			public TouchEvent createObject()
			{
				return new TouchEvent();
			}
		};
		this.scaleX = scaleX;
		this.scaleY = scaleY;
		touchEventPool = new Pool<TouchEvent>(factory, 100);
		view.setOnTouchListener(this);
	}
	
	public boolean onTouch(View v, MotionEvent event)
	{
		synchronized (this)
		{
			TouchEvent touchEvent = touchEventPool.newObject();
			switch (event.getAction())
			{
				case MotionEvent.ACTION_DOWN:
					touchEvent.type = TouchEvent.TOUCH_DOWN;
					isTouched = true;
					break;
				case MotionEvent.ACTION_MOVE:
					touchEvent.type = TouchEvent.TOUCH_DRAGGED;
					isTouched = true;
					break;
				case MotionEvent.ACTION_CANCEL:
				case MotionEvent.ACTION_UP:
					touchEvent.type = TouchEvent.TOUCH_UP;
					isTouched = false;
					break;
			}
			touchEvent.x = touchX = (int) (event.getX() * scaleX);
			touchEvent.y = touchY = (int) (event.getY() * scaleY);
			touchEventBuffer.add(touchEvent);
			return true;
		}
	}
	
	public boolean isTouchDown(int pointer)
	{
		synchronized (this)
		{
			if (pointer == 0) return isTouched;
			return false;
		}
	}
	
	public int getTouchX(int pointer)
	{
		synchronized (this)
		{
			return touchX;
		}
	}
	
	public int getTouchY(int pointer)
	{
		synchronized (this)
		{
			return touchY;
		}
	}
	
	public List<TouchEvent> getTouchEvents()
	{
		synchronized (this)
		{
			for (TouchEvent k : touchEvents)
			{
				touchEventPool.free(k);
			}
			touchEvents.clear();
			touchEvents.addAll(touchEventBuffer);
			touchEventBuffer.clear();
			return touchEvents;
		}
	}

	public int getPointerCount() {
		return 1;
	}
	
}
