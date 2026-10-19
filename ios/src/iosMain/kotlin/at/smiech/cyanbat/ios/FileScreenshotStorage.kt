package at.smiech.cyanbat.ios

import at.smiech.cyanbat.data.ScreenshotStorage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * The achievements' pictures as PNG files in the app's Application Support, which the system backs
 * up with the app and never clears, unlike its caches. Too large for the user defaults the rest of
 * what the game remembers is kept in.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class FileScreenshotStorage(
    private val directory: String = applicationSupport() + "/achievements",
) : ScreenshotStorage {
    override fun write(name: String, png: ByteArray) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
        // Atomically: written aside and moved into place, so no half a picture is left under the
        // name.
        check(data.writeToFile("$directory/$name.png", atomically = true)) {
            "Could not keep the picture $name"
        }
    }

    override fun read(name: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile("$directory/$name.png") ?: return null
        val size = data.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return bytes
    }
}

/** The app's Application Support folder, made by the system the first time it is written to. */
private fun applicationSupport(): String =
    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .first() as String
