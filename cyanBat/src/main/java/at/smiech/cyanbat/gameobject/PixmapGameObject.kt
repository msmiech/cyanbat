package at.smiech.cyanbat.gameobject

import android.graphics.Rect
import at.grueneis.game.framework.Pixmap

abstract class PixmapGameObject protected constructor(rect: Rect, var pixmap: Pixmap) : MovableGameObject(rect)
