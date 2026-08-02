package at.grueneis.game.framework.impl

import android.content.res.AssetManager
import android.os.Environment
import at.grueneis.game.framework.FileIO
import java.io.*

class AndroidFileIO(val assets: AssetManager) : FileIO {
    var sdPath: String = Environment.getExternalStorageDirectory().absolutePath + File.separator

    @Throws(IOException::class)
    override fun readAsset(filename: String) = assets.open(filename)

    @Throws(IOException::class)
    override fun readFile(filename: String): InputStream {
        return FileInputStream(sdPath + filename)
    }

    @Throws(IOException::class)
    override fun writeFile(filename: String): OutputStream {
        return FileOutputStream(sdPath + filename)
    }
}