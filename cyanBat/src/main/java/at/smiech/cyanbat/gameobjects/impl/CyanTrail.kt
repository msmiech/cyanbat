package at.smiech.cyanbat.gameobjects.impl

import android.graphics.Color
import android.graphics.Rect
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.smiech.cyanbat.gameobjects.MovableGameObject

class CyanTrail(rect: Rect) : MovableGameObject(rect) {
    private val TICK_INITIAL = 0.008f
    private var tickTime = 0f
    private val tick = TICK_INITIAL
    private var color = Color.CYAN

    override fun update(deltaTime: Float, touchEvents: List<TouchEvent>) {
        tickTime += deltaTime
        while (tickTime > tick) {
            tickTime -= tick
            if (Color.alpha(color) <= 1)
                removeMe = true
            color = Color.argb(Color.alpha(color) - 1, Color.red(color),
                    Color.green(color), Color.blue(color))
        }
        velocity.x = -2f
        super.update(deltaTime, touchEvents)
    }

    override fun draw(g: Graphics) {
        g.drawRect(rectangle.left, rectangle.top, rectangle.width(), rectangle.height(), color)
    }
}
