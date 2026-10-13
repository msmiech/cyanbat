package at.smiech.engine.impl

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * The game's asset files, which the app carries in its bundle under `assets/`, as the Android app
 * carries them as assets and the desktop on its classpath (see iosApp/project.yml).
 */
@OptIn(ExperimentalForeignApi::class)
class IosAssets(private val root: String = NSBundle.mainBundle.resourcePath + "/assets") {
    /** The file [name], such as `music/menu.wav`, as it is on disk. */
    fun data(name: String): NSData =
        NSData.dataWithContentsOfFile("$root/$name") ?: error("Asset $name not found in the bundle")

    /** The file [name]'s bytes. */
    fun bytes(name: String): ByteArray {
        val data = data(name)
        val size = data.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return bytes
    }
}
