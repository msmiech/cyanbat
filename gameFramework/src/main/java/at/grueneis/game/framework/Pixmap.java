package at.grueneis.game.framework;

import at.grueneis.game.framework.Graphics.PixmapFormat;

public interface Pixmap
{
	int getWidth();
	
	int getHeight();
	
	PixmapFormat getFormat();
	
	void dispose();
}
