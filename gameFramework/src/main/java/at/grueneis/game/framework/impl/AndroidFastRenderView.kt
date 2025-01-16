package at.grueneis.game.framework.impl

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.SurfaceView

/**
 * Implementation based on "Beginning Android Games".
 */
@SuppressLint("ViewConstructor")
class AndroidFastRenderView(var gameActivity: AndroidGameActivity, var framebuffer: Bitmap) :
    SurfaceView(gameActivity), Runnable {
    var renderThread: Thread? = null

    @Volatile
    var running = false
    fun resume() {
        running = true
        renderThread = Thread(this).apply { start() }
    }

    fun pause() {
        running = false
        while (true) {
            try {
                renderThread?.join()
                break
            } catch (e: InterruptedException) {
                // can't do anything about that at this point
            }
        }
    }

    override fun run() {
        val dstRec = Rect()
        var startTime = System.nanoTime()
        var deltaTime: Float
        var canvas: Canvas
        // gameloop
        while (running) {
            if (!holder.surface.isValid) continue
            deltaTime = (System.nanoTime() - startTime) / 1e9f
            startTime = System.nanoTime()
            gameActivity.currentScreen?.update(deltaTime)
            gameActivity.currentScreen?.present(deltaTime)
            canvas = holder.lockCanvas()
            canvas.getClipBounds(dstRec)
            canvas.drawBitmap(framebuffer, null, dstRec, null)
            holder.unlockCanvasAndPost(canvas)
        }
    }

}