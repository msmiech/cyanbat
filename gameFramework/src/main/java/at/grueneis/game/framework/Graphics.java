package at.grueneis.game.framework;

public interface Graphics
{
	enum PixmapFormat {
		ARGB8888, ARGB4444, RGB565
	}

	Pixmap newPixmap(String filename, PixmapFormat format);

	void clear(int color);

	void drawPixel(int x, int y, int color);

	void drawLine(int xFrom, int yFrom, int xTo, int yTo, int color);

	void drawRect(int x, int y, int width, int height, int color);

	void drawOval(int x, int y, int width, int height, int color);

	void drawPixmap(Pixmap pixmap, int x, int y, int srcX, int srcY, int srcWidth, int srcHeight);

	void drawPixmap(Pixmap pixmap, int x, int y);

	void drawString(String s, int x, int y, int fontSize, int col);

	int getWidth();

	int getHeight();
}
