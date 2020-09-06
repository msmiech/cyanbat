package at.grueneis.game.framework.code;

import android.graphics.Bitmap;
import at.grueneis.game.framework.Graphics.PixmapFormat;
import at.grueneis.game.framework.Pixmap;

public class AndroidPixmap implements Pixmap
{
	Bitmap bitmap;
	PixmapFormat format;
	
	public AndroidPixmap(Bitmap bitmap, PixmapFormat format)
	{
		this.bitmap = bitmap;
		this.format = format;
	}
	
	public int getWidth()
	{
		return bitmap.getWidth();
	}
	
	public int getHeight()
	{
		return bitmap.getHeight();
	}
	
	public PixmapFormat getFormat()
	{
		return format;
	}
	
	public void dispose()
	{
		bitmap.recycle();
	}
	
}
