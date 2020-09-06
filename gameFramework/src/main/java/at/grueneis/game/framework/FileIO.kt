package at.grueneis.game.framework

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface FileIO {
    @Throws(IOException::class)
    fun readAsset(filename: String?): InputStream

    @Throws(IOException::class)
    fun readFile(filename: String): InputStream

    @Throws(IOException::class)
    fun writeFile(filename: String): OutputStream
}