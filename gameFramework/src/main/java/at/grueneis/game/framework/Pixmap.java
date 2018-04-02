package at.grueneis.game.framework;

import at.grueneis.game.framework.Graphics.PixmapFormat;

public interface Pixmap
{
	public int getWidth();
	
	public int getHeight();
	
	public PixmapFormat getFormat();
	
	public void dispose();
}
