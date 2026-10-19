package at.smiech.engine.impl

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The picture as a PNG file, by the platform's own encoder: Android's on Android, Skia's everywhere
 * else. Lossless, since what is kept is pixel art.
 */
internal expect fun ImageBitmap.encodePng(): ByteArray
