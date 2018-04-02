package at.msmiech.cyanbat.objects;

public class Vector2D { // A C# like vector-class, not to get confused with the
						// old java Collection
	public float x;
	public float y;

	/*
	 * others following...
	 */

	public Vector2D() {
		x = 0f;
		y = 0f;
	}

	public Vector2D(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public void add(Vector2D val) {
		x += val.x;
		y += val.y;
	}

	public void subtract(Vector2D val) {
		x -= val.x;
		y -= val.y;
	}
}
