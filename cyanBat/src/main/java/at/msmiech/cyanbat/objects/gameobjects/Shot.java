package at.msmiech.cyanbat.objects.gameobjects;

import java.util.List;

import android.graphics.Rect;
import at.grueneis.game.framework.Input.TouchEvent;
import at.grueneis.game.framework.Pixmap;
import at.msmiech.cyanbat.interfaces.Collidable;
import at.msmiech.cyanbat.interfaces.GameObject;

public class Shot extends PixmapGameObject implements Collidable {

	public GameObject firedByObject; // For future use...
	public static int count = 0;

	public Shot(Rect rect, Pixmap pixmap, GameObject firedBy) {
		super(rect, pixmap);
		this.firedByObject = firedBy;
		count += 1;
		velocity.x = 2;
	}

	@Override
	public void update(float deltaTime, List<TouchEvent> touchEvents) {
		super.update(deltaTime, touchEvents);

	}

	public void hit() {
		removeMe = true;
	}
}
