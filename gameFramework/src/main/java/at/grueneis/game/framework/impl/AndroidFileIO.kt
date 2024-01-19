package at.grueneis.game.framework.impl

import android.content.res.AssetManager
import android.os.Environment
import at.grueneis.game.framework.FileIO
import java.io.*

class AndroidFileIO(var assets: AssetManager) : FileIO {
    var sdPath: String
    @Throws(IOException::class)
    override fun readAsset(filename: String?): InputStream {
        return assets.open(filename!!)
    }

    @Throws(IOException::class)
    override fun readFile(filename: String): InputStream {
        return FileInputStream(sdPath + filename)
    }

    @Throws(IOException::class)
    override fun writeFile(filename: String): OutputStream {
        return FileOutputStream(sdPath + filename)
    }

    init {
        sdPath = Environment.getExternalStorageDirectory().absolutePath + File.separator
    }
}