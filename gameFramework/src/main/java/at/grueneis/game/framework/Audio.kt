package at.grueneis.game.framework

interface Audio {
    fun newMusic(filename: String): Music
    fun newSound(filename: String): Sound
}