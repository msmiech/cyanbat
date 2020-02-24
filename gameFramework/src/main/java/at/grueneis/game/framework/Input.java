package at.grueneis.game.framework;

import java.util.List;

public interface Input
{
	class TouchEvent
	{
		public static final int TOUCH_DOWN = 0;
		public static final int TOUCH_UP = 1;
		public static final int TOUCH_DRAGGED = 2;
		public int type;
		public int x;
		public int y;
		public int pointer;
	}
	
	boolean isTouchDown(int pointer);
	
	int getTouchX(int pointer);
	
	int getTouchY(int pointer);
	
	float getAccelX();
	
	float getAccelY();
	
	float getAccelZ();
	
	List<TouchEvent> getTouchEvents();
	
	int getPointerCount();
	
}
