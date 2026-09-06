package at.smiech.cyanbat.gameobject

import at.grueneis.game.framework.Pixmap
import at.grueneis.game.framework.math.Rect

abstract class PixmapGameObject protected constructor(rect: Rect, var pixmap: Pixmap) :
    MovableGameObject(rect)
