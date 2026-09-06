package at.smiech.engine

interface Audio {
    fun newMusic(filename: String): Music
    fun newSound(filename: String): Sound
}
