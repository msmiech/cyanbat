package at.smiech.engine.impl

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal actual fun ImageBitmap.encodePng(): ByteArray {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    try {
        return image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("Skia encoded no PNG")
    } finally {
        image.close()
    }
}
