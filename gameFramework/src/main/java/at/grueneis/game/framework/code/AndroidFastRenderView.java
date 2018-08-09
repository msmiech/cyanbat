package at.grueneis.game.framework.code;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class AndroidFastRenderView extends SurfaceView implements Runnable {
    AndroidGameActivity game;
    Bitmap framebuffer;
    Thread renderThread = null;
    SurfaceHolder holder;
    volatile boolean running = false;

    public AndroidFastRenderView(AndroidGameActivity game, Bitmap framebuffer) {
        super(game); // AndroidGameActivity is derived from Activity
        this.game = game;
        this.framebuffer = framebuffer;
        holder = getHolder();
    }

    public void resume() {
        running = true;
        renderThread = new Thread(this);
        renderThread.start();
    }

    public void pause() {
        running = false;
        while (true) {
            try {
                renderThread.join();
                break;
            } catch (InterruptedException e) {
                // can't do anything about that at this point
            }
        }
    }

    public void run() {
        Rect dstRec = new Rect();
        long startTime = System.nanoTime();
        float deltaTime;
        Canvas canvas;
        while (running) {
            if (!holder.getSurface().isValid())
                continue;
            deltaTime = (System.nanoTime() - startTime) / 1e9f;
            startTime = System.nanoTime();

            game.getCurrentScreen().update(deltaTime);
            game.getCurrentScreen().present(deltaTime);

            canvas = holder.lockCanvas();
            canvas.getClipBounds(dstRec);
            canvas.drawBitmap(framebuffer, null, dstRec, null);
            holder.unlockCanvasAndPost(canvas);
        }
    }

}
