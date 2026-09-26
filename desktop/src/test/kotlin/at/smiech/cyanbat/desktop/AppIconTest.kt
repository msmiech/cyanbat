package at.smiech.cyanbat.desktop

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The icons the desktop build ships, all of them written by tools/generate_icons.py.
 *
 * Nothing else looks at them until it is too late to notice one missing or malformed: the window's
 * are read as the app starts, and the installers' only when a release is packaged - which no pull
 * request does, so a broken one would first show up as a failed release. Each image inside the
 * .ico and the .icns is decoded, because a directory entry pointing at the wrong bytes is not an
 * error to anything but the icon it leaves blank.
 *
 * The sizes are spelled out, as in [SpriteSheetTest], rather than read off the code under test.
 */
class AppIconTest {

    @Test
    fun `the window icon comes at every size the platforms ask for`() {
        assertEquals(DESKTOP_SIZES, WindowIcon.images.map { it.width })
        for (image in WindowIcon.images) {
            assertEquals(image.width, image.height, "a window icon is not square")
        }
    }

    @Test
    fun `the Linux installer's icon is a 512px square`() {
        val image = ImageIO.read(packaged("cyanbat.png"))
        assertNotNull(image, "cyanbat.png did not decode as an image")
        assertEquals(512 to 512, image.width to image.height)
    }

    /** An .ico opens with a directory of its images: each one's size, 0 meaning 256, and offset. */
    @Test
    fun `the Windows installer's icon holds every size, each the size it says`() {
        val bytes = packaged("cyanbat.ico").readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, header.getShort(2).toInt(), "cyanbat.ico is not an icon file")
        val sizes = (0 until header.getShort(4).toInt()).map { index ->
            val entry = 6 + 16 * index
            val size = (bytes[entry].toInt() and 0xFF).takeIf { it != 0 } ?: 256
            val length = header.getInt(entry + 8)
            val image = ImageIO.read(ByteArrayInputStream(bytes, header.getInt(entry + 12), length))
            assertNotNull(image, "the ${size}px image in cyanbat.ico did not decode")
            assertEquals(size to size, image.width to image.height)
            size
        }
        assertEquals(DESKTOP_SIZES, sizes.sorted())
    }

    /** An .icns is a run of chunks, each tagged with the size of the image it holds. */
    @Test
    fun `the macOS installer's icon holds every size, each the size it says`() {
        val bytes = packaged("cyanbat.icns").readBytes()
        val buffer = ByteBuffer.wrap(bytes)
        assertEquals("icns", String(bytes, 0, 4, Charsets.US_ASCII), "cyanbat.icns is not icns")
        assertEquals(bytes.size, buffer.getInt(4), "cyanbat.icns is not the length its header says")

        val found = mutableMapOf<String, Int>()
        var at = 8
        while (at < bytes.size) {
            val type = String(bytes, at, 4, Charsets.US_ASCII)
            val length = buffer.getInt(at + 4)
            assertTrue(length >= 8, "chunk $type in cyanbat.icns has a length of $length")
            if (type in MAC_SIZES) {
                val image = ImageIO.read(ByteArrayInputStream(bytes, at + 8, length - 8))
                assertNotNull(image, "chunk $type in cyanbat.icns did not decode")
                assertEquals(image.width, image.height, "chunk $type in cyanbat.icns is not square")
                found[type] = image.width
            }
            at += length
        }
        assertEquals(MAC_SIZES, found)
    }

    private fun packaged(name: String): File {
        // Tests run in the module's directory, which build.gradle.kts resolves these against too.
        val file = File("icons", name)
        assertTrue(file.isFile, "desktop/icons/$name is missing - run tools/generate_icons.py")
        return file
    }

    private companion object {
        /** DESKTOP_SIZES in the generator: 20 and 40 are what Windows asks for at 125% scaling. */
        val DESKTOP_SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)

        /**
         * The chunk types that hold a PNG, and that PNG's size: 128, 256 and 512, then 512, 16, 32,
         * 128 and 256 at double density.
         */
        val MAC_SIZES = mapOf(
            "ic07" to 128, "ic08" to 256, "ic09" to 512, "ic10" to 1024,
            "ic11" to 32, "ic12" to 64, "ic13" to 256, "ic14" to 512,
        )
    }
}
