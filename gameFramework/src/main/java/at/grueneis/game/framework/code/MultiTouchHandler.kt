package at.grueneis.game.framework.code;

import java.util.ArrayList;
import java.util.List;

import android.view.MotionEvent;
import android.view.View;

import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.code.Pool.PoolObjectFactory;

public class MultiTouchHandler implements TouchHandler {
	private boolean[] isTouched = new boolean[20];
	private int[] touchX = new int[20];
	private int[] touchY = new int[20];
	private Pool<TouchEvent> touchEventPool;
	private List<TouchEvent> touchEvents = new ArrayList<>();
	private List<TouchEvent> touchEventsBuffer = new ArrayList<>();
	private float scaleX;
	private float scaleY;
	private int pointerCount;

	public MultiTouchHandler(View view, float scaleX, float scaleY) {
		PoolObjectFactory<TouchEvent> factory = TouchEvent::new;
		touchEventPool = new Pool<>(factory, 100);
		view.setOnTouchListener(this);

		this.scaleX = scaleX;
		this.scaleY = scaleY;
	}

	public boolean onTouch(View v, MotionEvent event) {
		synchronized (this) {
			int action = event.getAction() & MotionEvent.ACTION_MASK;
			int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
			int pointerId = event.getPointerId(pointerIndex);
			TouchEvent touchEvent;

			switch (action) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN:
				touchEvent = touchEventPool.newObject();
				touchEvent.type = TouchEvent.TOUCH_DOWN;
				touchEvent.pointer = pointerId;
				touchEvent.x = touchX[pointerId] = (int) (event
						.getX(pointerIndex) * scaleX);
				touchEvent.y = touchY[pointerId] = (int) (event
						.getY(pointerIndex) * scaleY);
				isTouched[pointerId] = true;
				touchEventsBuffer.add(touchEvent);
				break;

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
			case MotionEvent.ACTION_CANCEL:
				touchEvent = touchEventPool.newObject();
				touchEvent.type = TouchEvent.TOUCH_UP;
				touchEvent.pointer = pointerId;
				touchEvent.x = touchX[pointerId] = (int) (event
						.getX(pointerIndex) * scaleX);
				touchEvent.y = touchY[pointerId] = (int) (event
						.getY(pointerIndex) * scaleY);
				isTouched[pointerId] = false;
				touchEventsBuffer.add(touchEvent);
				break;

			case MotionEvent.ACTION_MOVE:
				int pointerCount = event.getPointerCount();
				for (int i = 0; i < pointerCount; i++) {
					pointerIndex = i;
					pointerId = event.getPointerId(pointerIndex);

					touchEvent = touchEventPool.newObject();
					touchEvent.type = TouchEvent.TOUCH_DRAGGED;
					touchEvent.pointer = pointerId;
					touchEvent.x = touchX[pointerId] = (int) (event
							.getX(pointerIndex) * scaleX);
					touchEvent.y = touchY[pointerId] = (int) (event
							.getY(pointerIndex) * scaleY);
					touchEventsBuffer.add(touchEvent);
				}
				break;
			}
			pointerCount = event.getPointerCount();
			return true;
		}
	}

	public boolean isTouchDown(int pointer) {
		synchronized (this) {
			if (pointer < 0 || pointer >= 20)
				return false;
			else
				return isTouched[pointer];
		}
	}

	public int getTouchX(int pointer) {
		synchronized (this) {
			if (pointer < 0 || pointer >= 20)
				return 0;
			else
				return touchX[pointer];
		}
	}

	public int getTouchY(int pointer) {
		synchronized (this) {
			if (pointer < 0 || pointer >= 20)
				return 0;
			else
				return touchY[pointer];
		}
	}

	public List<TouchEvent> getTouchEvents() {
		synchronized (this) {
			int len = touchEvents.size();
			for (int i = 0; i < len; i++)
				touchEventPool.free(touchEvents.get(i));
			touchEvents.clear();
			touchEvents.addAll(touchEventsBuffer);
			touchEventsBuffer.clear();
			return touchEvents;
		}
	}

	public int getPointerCount() {
		return pointerCount;
	}
}
