package at.grueneis.game.framework

object Color {
    fun convert(r: Int, g: Int, b: Int, a: Int): Int {
        return a and 0xff shl 24 or
                (r and 0xff shl 16) or
                (g and 0xff shl 8) or
                (b and 0xff)
    }
}