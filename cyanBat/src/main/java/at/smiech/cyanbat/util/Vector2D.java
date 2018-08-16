package at.smiech.cyanbat.util;

/**
 * Classic (movement) vector class in two dimensions
 */
public class Vector2D {
	public float x;
	public float y;

	public Vector2D() {
		x = 0f;
		y = 0f;
	}

	public Vector2D(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public void add(Vector2D val) {
		if(val == null) {
			throw new IllegalArgumentException("Passed 2d vector was null!");
		}
		x += val.x;
		y += val.y;
	}

	public void subtract(Vector2D val) {
		if(val == null) {
			throw new IllegalArgumentException("Passed 2d vector was null!");
		}
		x -= val.x;
		y -= val.y;
	}
}
