package at.smiech.cyanbat.desktop.recorder

import java.awt.image.IndexColorModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The hand-written GIF writer against the JDK's own GIF reader.
 *
 * A broken LZW stream or a misplaced frame rectangle would not fail anything else: it only turns
 * the README's footage into noise, which nobody looks at until it is on the page. So each frame is
 * read back and composited the way a browser does, and has to come out as the frame that went in.
 */
class GifEncoderTest {

    private val width = 128
    private val height = 96
    private val palette = IntArray(200) { it * 0x010305 }

    /** A frame with nothing to compress, so the code table fills up and is cleared many times over. */
    private fun noise(seed: Int): ByteArray {
        val random = Random(seed)
        return ByteArray(width * height) { random.nextInt(palette.size).toByte() }
    }

    /** [frame]'s index at ([x], [y]) moved on by one, so it is certain to have changed. */
    private fun bump(frame: ByteArray, x: Int, y: Int) {
        frame[y * width + x] = (((frame[y * width + x].toInt() and 0xFF) + 1) % palette.size).toByte()
    }

    @Test
    fun `frames read back as they were written`() {
        val first = noise(1)
        // A small change, which is stored as the rectangle around it.
        val second = first.copyOf().also { frame ->
            for (y in 15 until 22) for (x in 20 until 30) bump(frame, x, y)
        }
        val third = noise(2)

        val frames = decode(encode(listOf(first to 4, second to 4, second to 3, third to 5)))

        // The repeated frame is not stored again; the one before it is held for longer instead.
        assertEquals(listOf(4, 7, 5), frames.map { it.second })
        assertContentEquals(first, frames[0].first)
        assertContentEquals(second, frames[1].first)
        assertContentEquals(third, frames[2].first)
    }

    @Test
    fun `a changed frame is stored as only the rectangle around its changes`() {
        val first = noise(3)
        val second = first.copyOf().also {
            bump(it, 50, 40)
            bump(it, 61, 44)
        }
        val bytes = encode(listOf(first to 4, second to 4))

        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val descriptor = child(reader.getImageMetadata(1), "ImageDescriptor")
        assertEquals(
            listOf(50, 40, 12, 5),
            listOf("imageLeftPosition", "imageTopPosition", "imageWidth", "imageHeight")
                .map { descriptor.getAttribute(it).toInt() },
        )
    }

    /**
     * The gameplay reel moves from one stage's palette to the next. A frame on another palette has
     * to show in its colors, even where its indices are the same as the frame's before it.
     */
    @Test
    fun `frames on another palette read back in its colors`() {
        val other = IntArray(220) { 0xFFFFFF - it * 0x010203 }
        val first = noise(4)
        val nudged = first.copyOf().also { bump(it, 70, 30) }
        val last = noise(5)
        val bytes = ByteArrayOutputStream().also { out ->
            GifEncoder(out, width, height, palette).apply {
                addFrame(first, 4)
                addFrame(first, 4, other)
                addFrame(nudged, 4, other)
                addFrame(last, 4, palette)
                finish()
            }
        }.toByteArray()

        val expected = listOf(first to palette, first to other, nudged to other, last to palette)
            .map { (frame, colors) -> IntArray(frame.size) { colors[frame[it].toInt() and 0xFF] } }
        val decoded = decodeColors(bytes)
        assertEquals(expected.size, decoded.size)
        expected.zip(decoded).forEach { (want, got) -> assertContentEquals(want, got) }
    }

    private fun encode(frames: List<Pair<ByteArray, Int>>): ByteArray {
        val out = ByteArrayOutputStream()
        val gif = GifEncoder(out, width, height, palette)
        for ((pixels, delay) in frames) gif.addFrame(pixels, delay)
        gif.finish()
        return out.toByteArray()
    }

    /** Every stored frame composited over the ones before it, as indices, with its delay. */
    private fun decode(bytes: ByteArray): List<Pair<ByteArray, Int>> {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val canvas = ByteArray(width * height)
        return (0 until reader.getNumImages(true)).map { i ->
            val image = reader.read(i)
            val metadata = reader.getImageMetadata(i)
            val descriptor = child(metadata, "ImageDescriptor")
            val control = child(metadata, "GraphicControlExtension")
            val left = descriptor.getAttribute("imageLeftPosition").toInt()
            val top = descriptor.getAttribute("imageTopPosition").toInt()
            val transparent = if (control.getAttribute("transparentColorFlag") == "TRUE") {
                control.getAttribute("transparentColorIndex").toInt()
            } else {
                -1
            }
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val index = image.raster.getSample(x, y, 0)
                    if (index != transparent) canvas[(top + y) * width + left + x] = index.toByte()
                }
            }
            canvas.copyOf() to control.getAttribute("delayTime").toInt()
        }
    }

    /** Every stored frame composited over the ones before it, as RGB, each in its own palette. */
    private fun decodeColors(bytes: ByteArray): List<IntArray> {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val canvas = IntArray(width * height)
        return (0 until reader.getNumImages(true)).map { i ->
            val image = reader.read(i)
            val colors = image.colorModel as IndexColorModel
            val metadata = reader.getImageMetadata(i)
            val descriptor = child(metadata, "ImageDescriptor")
            val control = child(metadata, "GraphicControlExtension")
            val left = descriptor.getAttribute("imageLeftPosition").toInt()
            val top = descriptor.getAttribute("imageTopPosition").toInt()
            val transparent = if (control.getAttribute("transparentColorFlag") == "TRUE") {
                control.getAttribute("transparentColorIndex").toInt()
            } else {
                -1
            }
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val index = image.raster.getSample(x, y, 0)
                    if (index != transparent) canvas[(top + y) * width + left + x] = colors.getRGB(index) and 0xFFFFFF
                }
            }
            canvas.copyOf()
        }
    }

    private fun child(metadata: javax.imageio.metadata.IIOMetadata, name: String): IIOMetadataNode {
        val root = metadata.getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
        return root.getElementsByTagName(name).item(0) as IIOMetadataNode
    }
}
