package at.grueneis.game.framework.code;

import java.util.List;

import android.view.View.OnTouchListener;
import at.grueneis.game.framework.Input.TouchEvent;

public interface TouchHandler extends OnTouchListener
{
	boolean isTouchDown(int pointer);
	
	int getTouchX(int pointer);
	
	int getTouchY(int pointer);
	
	List<TouchEvent> getTouchEvents();

	int getPointerCount();
}
