package at.smiech.cyanbat.gameobjects

import android.graphics.Rect
import at.grueneis.game.framework.Graphics
import at.grueneis.game.framework.Input.TouchEvent
import at.grueneis.game.framework.Pixmap

abstract class PixmapGameObject protected constructor(rect: Rect, var pixmap: Pixmap) : MovableGameObject(rect)
