package at.grueneis.game.framework

import android.content.Context

interface Game {
    val input: Input?
    val fileIO: FileIO?
    val graphics: Graphics?
    val audio: Audio?
    fun setScreen(screen: Screen)
    val currentScreen: Screen?
    val startScreen: Screen?
    val context: Context
    val frameBufferWidth: Int
    val frameBufferHeight: Int
}