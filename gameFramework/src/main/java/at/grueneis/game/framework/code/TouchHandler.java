package at.grueneis.game.framework.code;

import java.util.List;

import android.view.View.OnTouchListener;
import at.grueneis.game.framework.Input.TouchEvent;

public interface TouchHandler extends OnTouchListener
{
	public boolean isTouchDown(int pointer);
	
	public int getTouchX(int pointer);
	
	public int getTouchY(int pointer);
	
	public List<TouchEvent> getTouchEvents();

	public int getPointerCount();
}
