package at.grueneis.game.framework.code;

import java.io.IOException;
import java.io.InputStream;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import at.grueneis.game.framework.Graphics;
import at.grueneis.game.framework.Pixmap;

public class AndroidGraphics implements Graphics {
	AssetManager assets;
	Bitmap frameBuffer;
	Canvas canvas;
	Paint paint;
	Typeface typeface;
	Rect srcRect = new Rect();
	Rect dstRect = new Rect();

	public AndroidGraphics(AssetManager assets, Bitmap frameBuffer) {
		this.assets = assets;
		this.frameBuffer = frameBuffer;
		canvas = new Canvas(frameBuffer);
		paint = new Paint();
		typeface = Typeface.create("Arial", Typeface.NORMAL);
		paint.setTypeface(typeface);
	}

	public Pixmap newPixmap(String filename, PixmapFormat format) {
		Bitmap.Config config = null; // Bitmap.Config
		if (format == PixmapFormat.RGB565)
			config = Bitmap.Config.RGB_565;
		else if (format == PixmapFormat.ARGB4444)
			config = Bitmap.Config.ARGB_4444;
		else
			config = Bitmap.Config.ARGB_8888;
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = config;

		InputStream in = null;
		Bitmap bitmap = null;
		try {
			in = assets.open(filename);
			bitmap = BitmapFactory.decodeStream(in);
			if (bitmap == null)
				throw new RuntimeException("Asset-Bitmap <" + filename
						+ "> not found!");
		} catch (IOException exc) {
			throw new RuntimeException("Asset-Bitmap <" + filename
					+ "> not found!");
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException exc) {
				}
			}
		}
		if (bitmap.getConfig() == Bitmap.Config.RGB_565)
			format = PixmapFormat.RGB565;
		else if (bitmap.getConfig() == Bitmap.Config.ARGB_4444)
			format = PixmapFormat.ARGB4444;
		else
			format = PixmapFormat.ARGB8888;
		return new AndroidPixmap(bitmap, format);
	}

	public void clear(int color) {
		canvas.drawRGB((color & 0xff0000) >> 16, (color & 0xff00) >> 8,
				(color & 0xff));
	}

	public void drawPixel(int x, int y, int color) {
		paint.setColor(color);
		canvas.drawPoint(x, y, paint);
	}

	public void drawLine(int xFrom, int yFrom, int xTo, int yTo, int color) {
		paint.setColor(color);
		canvas.drawLine(xFrom, yFrom, xTo, yTo, paint);
	}

	public void drawRect(int x, int y, int width, int height, int color) {
		paint.setColor(color);
		paint.setStyle(Style.FILL);
		canvas.drawRect(x, y, x + width, y + height, paint);
	}

	public void drawPixmap(Pixmap pixmap, int x, int y, int srcX, int srcY,
			int srcWidth, int srcHeight) {
		srcRect.left = srcX;
		srcRect.top = srcY;
		srcRect.right = srcX + srcWidth - 1;
		srcRect.bottom = srcY + srcHeight - 1;

		dstRect.left = x;
		dstRect.top = y;
		dstRect.right = x + srcWidth - 1;
		dstRect.bottom = y + srcHeight - 1;

		canvas.drawBitmap(((AndroidPixmap) pixmap).bitmap, srcRect, dstRect,
				null);
	}

	public void drawPixmap(Pixmap pixmap, int x, int y) {
		canvas.drawBitmap(((AndroidPixmap) pixmap).bitmap, x, y, null);
	}

	public void drawString(String s, int x, int y, int fontSize, int col) {
		paint.setTextSize(fontSize);
		paint.setColor(col);
		canvas.drawText(s, x, y, paint);
	}

	public int getWidth() {
		return frameBuffer.getWidth();
	}

	public int getHeight() {
		return frameBuffer.getHeight();
	}

	public void drawOval(int x, int y, int width, int height, int color) {
		paint.setColor(color);
		paint.setStyle(Style.FILL);
		canvas.drawOval(new RectF(x, y, x + width, y + height), paint);
	}
}
