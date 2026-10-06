package at.smiech.engine.impl

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import at.smiech.engine.Dither
import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Lighting
import at.smiech.engine.Raster
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ComposeGraphics] drawn the way a host draws it - through [drawGameFrame], into a view of some
 * size - only into a picture rather than a window, and read back a pixel at a time.
 *
 * Unit tests cannot see a screen, but the pixel grid is a property that can be read off the pixels:
 * drawn at three times the frame's size, the frame must come out as three-by-three blocks of the
 * frame drawn at its own size, whatever it holds.
 */
class ComposeGraphicsTest {

    /**
     * The game right-aligns, centers and wraps text by these widths. A width that came up short would
     * run text off the frame or out of its card; one that came up long would leave it short of the
     * edge it was aligned to.
     */
    @Test
    fun `the measured width ends where the drawn text does`() {
        for ((text, size) in GAME_TEXT) {
            val graphics = graphics(320, 48)
            val penEnd = PEN_X + graphics.measureString(text, size)
            graphics.clear(EngineColors.BLACK)
            graphics.drawString(text, PEN_X, 36, size, EngineColors.WHITE)
            val frame = graphics.render(1f)

            // Antialiased now, so a column counts as inked where a pixel in it is at least half lit.
            val inked = (0 until frame.width).filter { x ->
                (0 until frame.height).any { y -> green(frame[x, y]) >= 128 }
            }
            assertTrue(inked.isNotEmpty(), "\"$text\" at ${size}px drew nothing")
            assertTrue(
                inked.last() < penEnd,
                "\"$text\" at ${size}px inks column ${inked.last()}, past its measured end at $penEnd",
            )
            // All that should be left over is the last glyph's own side bearing: a sliver of the
            // size, plus a pixel or two.
            assertTrue(
                penEnd - inked.last() <= size / 4 + 2,
                "\"$text\" at ${size}px stops at column ${inked.last()}, short of its measured end at $penEnd",
            )
        }
    }

    /**
     * The shapes come out exactly as [Raster] works them out, and not as Skia's own ovals and lines
     * would have them. Drawn translucent over black, so that a pixel blended twice, or an edge pixel
     * only partly covered, would show up as a second shade of gray.
     */
    @Test
    fun `ovals outlines and lines land on exactly the pixels Raster gives them`() {
        val gray = EngineColors.withAlpha(EngineColors.WHITE, 0.5f)
        val cases = listOf(
            Triple(
                "oval",
                lit { it.drawOval(5, 4, 37, 29, gray) },
                runs { Raster.oval(5, 4, 37, 29, it) }),
            Triple(
                "outline",
                lit { it.drawOvalOutline(5, 4, 37, 29, gray) },
                runs { Raster.ovalOutline(5, 4, 37, 29, it) },
            ),
            Triple(
                "line",
                lit { it.drawLine(3, 40, 58, 9, gray) },
                runs { Raster.line(3, 40, 58, 9, it) }),
        )
        for ((shape, drawn, expected) in cases) {
            assertEquals(expected, drawn.keys, "the $shape's pixels")
            assertEquals(1, drawn.values.toSet().size, "the $shape in more than one shade")
        }
    }

    /**
     * What keeps the game pixel art at any screen size: everything but text - sprites, scaled ones,
     * fades, flashes, shapes, and a turned sprite with its flash - drawn at three times the frame's
     * size is the frame at its own size with every pixel made a three-by-three block.
     *
     * The blocks are held to be exactly one color each; their colors only to within a step a channel
     * of the frame's, because Skia blends a translucent picture by another path when it is
     * magnified, and the two round a step apart.
     */
    @Test
    fun `at a whole-number scale every frame pixel is a solid block`() {
        val graphics = graphics(64, 48)
        graphics.drawScene()
        val small = graphics.render(1f)
        val large = graphics.render(3f)

        for (y in 0 until large.height) for (x in 0 until large.width) {
            val block = large[x - x % 3, y - y % 3]
            val actual = large[x, y]
            if (actual != block) {
                throw AssertionError(
                    "view pixel ($x, $y) is #%08X in a block of #%08X".format(
                        actual,
                        block
                    )
                )
            }
            val expected = small[x / 3, y / 3]
            if (!near(actual, expected)) {
                throw AssertionError(
                    "view pixel ($x, $y) is #%08X, but frame pixel (%d, %d) is #%08X"
                        .format(actual, x / 3, y / 3, expected),
                )
            }
        }
    }

    /**
     * At a scale that is not a whole number the blocks cannot all be the same size, but they are
     * still blocks: nearest-neighbor and aliased, so no pixel of the view is a color the frame does
     * not have. The scene is laid out so that nothing translucent overlaps anything but the
     * background, which leaves an edge pixel nothing to be but one side of the edge or the other.
     */
    @Test
    fun `at any scale the view holds no color the frame does not`() {
        val graphics = graphics(64, 48)
        graphics.drawScene()
        val frameColors = graphics.render(1f).colors()
        // Within a step a channel, for the reason the whole-number test gives.
        val stray =
            graphics.render(2.5f).colors().filter { color -> frameColors.none { near(it, color) } }
        assertTrue(
            stray.isEmpty(),
            "colors the frame never had: ${stray.take(8).map { "#%08X".format(it) }}"
        )
    }

    /** The deliberate `- 1`: a blit paints a column and a row short of the sprite it is given. */
    @Test
    fun `a blit leaves its last column and row undrawn`() {
        val graphics = graphics(16, 16, "white" to solid(4, 4, EngineColors.WHITE))
        graphics.clear(EngineColors.BLACK)
        graphics.drawPixmap(
            graphics.newPixmap("white"),
            2,
            2,
            0,
            0,
            4,
            4
        )
        val frame = graphics.render(1f)
        val lit = (0 until 16).flatMap { y -> (0 until 16).map { x -> x to y } }
            .filter { (x, y) -> frame[x, y] == EngineColors.WHITE }
            .toSet()
        assertEquals((2..4).flatMap { y -> (2..4).map { x -> x to y } }.toSet(), lit)
    }

    /**
     * A see-through sprite is the sprite with holes in it: where [Dither] keeps a pixel, the
     * sprite's own color, and everywhere else what was under it, untouched and unblended. The
     * pattern is the sprite's own, so it moves with the sprite rather than crawling over it.
     */
    @Test
    fun `a dithered sprite draws the pixels the dither keeps and leaves the rest as they were`() {
        for (corner in listOf(4 to 6, 7 to 3)) {
            val (left, top) = corner
            val plain = graphics(16, 16).run {
                clear(BACKGROUND)
                drawPixmap(newPixmap("sprite"), left, top, 0, 0, 6, 5)
                render(1f)
            }
            for (coverage in listOf(0.25f, 0.5f, 0.75f)) {
                val dithered = graphics(16, 16).run {
                    clear(BACKGROUND)
                    drawPixmapDithered(newPixmap("sprite"), left, top, 0, 0, 6, 5, 6, 5, coverage)
                    render(1f)
                }
                val level = Dither.level(coverage)
                var shown = 0
                for ((x, y) in dithered.pixels()) {
                    val inSprite = x - left in 0 until 5 && y - top in 0 until 4
                    val expected =
                        if (inSprite && Dither.keeps(
                                x - left,
                                y - top,
                                level
                            )
                        ) plain[x, y] else BACKGROUND
                    if (expected != BACKGROUND) shown++
                    assertEquals(
                        expected,
                        dithered[x, y],
                        "pixel ($x, $y) of the sprite at $corner, ${level}/${Dither.LEVELS} shown",
                    )
                }
                assertTrue(shown > 0, "nothing of the sprite showed at $level/${Dither.LEVELS}")
            }
        }
    }

    /** A flash over a turned sprite lights exactly the turned sprite's pixels, and nothing else. */
    @Test
    fun `a turned sprite's flash covers the turned sprite`() {
        fun frame(flash: Boolean): Picture {
            val graphics = graphics(48, 48, "sprite" to sprite())
            val sprite = graphics.newPixmap("sprite")
            graphics.clear(EngineColors.BLACK)
            graphics.drawPixmap(sprite, 10, 12, 0, 0, 6, 5, 18, 15, 33f)
            if (flash) graphics.drawPixmapSilhouette(
                sprite,
                10,
                12,
                0,
                0,
                6,
                5,
                18,
                15,
                EngineColors.WHITE,
                33f
            )
            return graphics.render(1f)
        }

        val plain = frame(flash = false)
        val flashed = frame(flash = true)
        val sprite =
            plain.pixels().filter { plain[it.first, it.second] != EngineColors.BLACK }.toSet()
        val lit =
            flashed.pixels().filter { flashed[it.first, it.second] == EngineColors.WHITE }.toSet()
        assertTrue(sprite.size > 100, "the turned sprite drew ${sprite.size} pixels")
        assertEquals(sprite, lit)
    }

    /**
     * Text is the one thing drawn at the view's resolution rather than on the grid: at three times
     * the frame's size its edges are shades between the ink and the background, which a magnified
     * block of a pixel could never be, and its outline rings it in the outline's color.
     */
    @Test
    fun `text is drawn smooth at the view's resolution with its outline around it`() {
        val graphics = graphics(160, 48)
        graphics.clear(EngineColors.BLACK)
        graphics.drawOutlinedString("Score: 1234", 10, 32, 20, EngineColors.WHITE, EngineColors.RED)
        val view = graphics.render(3f)
        val colors = view.colors()
        assertTrue(EngineColors.WHITE in colors, "no pixel of the fill")
        assertTrue(colors.any { red(it) > 200 && green(it) < 60 }, "no pixel of the red outline")
        // Antialiased: shades of the fill and of the outline, many more than the three inks.
        assertTrue(colors.size > 20, "only ${colors.size} colors: drawn aliased")
    }

    @Test
    fun `a clear starts the frame over`() {
        val graphics = graphics(16, 16)
        graphics.clear(EngineColors.BLACK)
        graphics.drawRect(0, 0, 16, 16, EngineColors.WHITE)
        graphics.clear(EngineColors.RED)
        assertEquals(setOf(EngineColors.RED), graphics.render(1f).colors())
    }

    /**
     * On a finer grid the frame keeps its pixels' size for everything placed in them - sprites and
     * rectangles - and only what is worked out on the grid gets finer: the view, at twice the grid,
     * is two-by-two blocks of grid pixels, and the sprite in it is just where and what it was.
     */
    @Test
    fun `a finer grid refines the shapes and leaves the sprites as they were`() {
        val coarse = graphics(64, 48).apply { drawScene() }
        val fine = graphics(64, 48).apply {
            gridScale = 2
            drawScene()
        }
        val coarseView = coarse.render(4f)
        val fineView = fine.render(4f)

        // Every grid pixel a whole two-by-two block of the view.
        for (y in 0 until fineView.height step 2) for (x in 0 until fineView.width step 2) {
            val pixel = fineView[x, y]
            assertTrue(
                fineView[x + 1, y] == pixel && fineView[x, y + 1] == pixel && fineView[x + 1, y + 1] == pixel,
                "the grid pixel at view ($x, $y) is not whole",
            )
        }
        // The sprite, unturned, comes out exactly as on the frame's own grid.
        for (y in SPRITE_Y * 4 until (SPRITE_Y + 4) * 4) for (x in SPRITE_X * 4 until (SPRITE_X + 5) * 4) {
            assertEquals(coarseView[x, y], fineView[x, y], "the sprite at view ($x, $y)")
        }
        // The oval is drawn on the finer grid, so its edge differs, but it stays in its box.
        val ovalColors = fineView.region(OVAL_X * 4, OVAL_Y * 4, OVAL_W * 4, OVAL_H * 4).toSet()
        assertTrue(ovalColors.size == 2, "the oval's box holds ${ovalColors.size} colors")
        assertTrue(
            fineView.region(OVAL_X * 4, OVAL_Y * 4, OVAL_W * 4, OVAL_H * 4) !=
                    coarseView.region(OVAL_X * 4, OVAL_Y * 4, OVAL_W * 4, OVAL_H * 4),
            "the oval came out the same on the finer grid",
        )
    }

    /**
     * The light multiplies what is under it: out where no light reaches a pixel is the dark times
     * what was drawn there, and at a light's full strength it is what was drawn there.
     */
    @Test
    fun `the light darkens the frame to the ambient and leaves it as drawn at full strength`() {
        val graphics = graphics(64, 48)
        graphics.clear(GRAY)
        graphics.drawLighting(Lighting().apply {
            begin(DARK, glow = 0f)
            add(32, 24, 10, EngineColors.WHITE, 1f)
        })
        val frame = graphics.render(1f)

        assertTrue(
            near(frame[2, 2], 0xFF202020.toInt()),
            "far from the light: #%08X".format(frame[2, 2])
        )
        assertTrue(near(frame[32, 24], GRAY), "in the light: #%08X".format(frame[32, 24]))
        // Out at the edge of its disc, part of the way between the two.
        val edge = green(frame[32 + 8, 24])
        assertTrue(edge in 0x21 until 0x80, "at the edge of the light: $edge")
    }

    @Test
    fun `a shadow keeps what it falls on in the dark`() {
        val graphics = graphics(64, 48)
        graphics.clear(GRAY)
        graphics.drawLighting(Lighting().apply {
            begin(DARK, glow = 0f)
            add(32, 24, 12, EngineColors.WHITE, 1f).apply {
                addShadowPoint(34f, 10f)
                addShadowPoint(50f, 10f)
                addShadowPoint(50f, 40f)
                addShadowPoint(34f, 40f)
                closeShadow()
            }
        })
        val frame = graphics.render(1f)

        assertTrue(near(frame[30, 24], GRAY), "beside the shadow: #%08X".format(frame[30, 24]))
        assertTrue(
            near(frame[35, 24], 0xFF202020.toInt()),
            "in the shadow: #%08X".format(frame[35, 24])
        )
        assertTrue(
            near(frame[33, 24], GRAY),
            "the pixel before the shadow's edge: #%08X".format(frame[33, 24])
        )
    }

    /**
     * The first light of a frame is laid down whole, the dark round it and all, its shadows painted
     * on in the dark's color, where every other light is laid over what is there with its shadows cut
     * out first. The two ways must come out the same, pixel for pixel.
     */
    @Test
    fun `the first light comes out as any other would`() {
        fun frame(behind: Boolean): Picture {
            val graphics = graphics(64, 48)
            graphics.clear(GRAY)
            graphics.drawLighting(Lighting().apply {
                begin(DARK, glow = 0f)
                // Off the picture altogether, so the light that counts is laid over the dark instead.
                if (behind) add(-1000, -1000, 10, EngineColors.WHITE, 1f)
                add(30, 22, 16, EngineColors.CYAN, 1f).apply {
                    addShadowPoint(36f, 10f)
                    addShadowPoint(60f, 4f)
                    addShadowPoint(60f, 34f)
                    closeShadow()
                }
                add(50, 40, 8, EngineColors.YELLOW, 0.7f)
            })
            return graphics.render(1f)
        }

        val first = frame(behind = false)
        val laidOver = frame(behind = true)
        for ((x, y) in first.pixels()) {
            assertEquals(laidOver[x, y], first[x, y], "pixel ($x, $y)")
        }
    }

    /** The light is worked out at the frame's size, so it comes out on the grid like the art. */
    @Test
    fun `at a whole-number scale the light is solid blocks too`() {
        val graphics = graphics(64, 48)
        graphics.drawScene()
        graphics.drawLighting(Lighting().apply {
            begin(DARK, glow = 0.2f)
            add(20, 20, 18, EngineColors.CYAN, 0.8f).apply {
                addShadowPoint(25f, 14f)
                addShadowPoint(45f, 2f)
                addShadowPoint(45f, 30f)
                closeShadow()
            }
            add(50, 30, 9, EngineColors.YELLOW, 1f)
        })
        val small = graphics.render(1f)
        val large = graphics.render(3f)

        for (y in 0 until large.height) for (x in 0 until large.width) {
            val block = large[x - x % 3, y - y % 3]
            assertEquals(block, large[x, y], "view pixel ($x, $y) is not its block's color")
            assertTrue(
                near(block, small[x / 3, y / 3]),
                "view pixel ($x, $y) against frame pixel (${x / 3}, ${y / 3})"
            )
        }
    }

    /**
     * A glint is added over the sprite on the side the light comes from, in the light's color, and
     * nowhere else.
     */
    @Test
    fun `a glint lights the edge of a sprite facing the light in the light's color`() {
        val graphics = graphics(48, 48, "disc" to disc(16, GRAY))
        val disc = graphics.newPixmap("disc")
        graphics.clear(EngineColors.BLACK)
        graphics.drawPixmap(disc, 16, 16, 0, 0, 16, 16)
        graphics.drawLighting(Lighting().apply {
            begin(EngineColors.WHITE, glow = 0f)
            // From the right, in pure cyan, which adds nothing to red.
            addGlint(disc, 0, 0, 16, 16, 16, 16, 16, 16, 0, EngineColors.CYAN, 1f)
        })
        val frame = graphics.render(1f)

        val glinting = frame.pixels().filter { (x, y) -> green(frame[x, y]) > green(GRAY) + 8 }
        assertTrue(glinting.isNotEmpty(), "nothing glints")
        assertTrue(
            glinting.all { (x, _) -> x >= 16 + 8 },
            "a glint on the left half: ${glinting.filter { it.first < 24 }}"
        )
        assertTrue(
            glinting.all { (x, y) -> red(frame[x, y]) == red(GRAY) },
            "the cyan glint changed red"
        )
    }

    /**
     * Everything the grid tests draw, laid out apart so that nothing translucent overlaps anything
     * but the background: a sprite, a magnified one, a fade, a flash, a turned sprite with its flash,
     * a translucent rectangle, a pixel, an oval, an outline, a line, and a dithered sprite and a
     * magnified one.
     */
    private fun ComposeGraphics.drawScene() {
        val sprite = newPixmap("sprite")
        clear(BACKGROUND)
        drawPixmap(sprite, SPRITE_X, SPRITE_Y, 0, 0, 6, 5)
        drawPixmap(sprite, 12, 2, 0, 0, 6, 5, 11, 9)
        drawPixmapFaded(sprite, 26, 3, 0, 0, 6, 5, 0.4f)
        drawPixmapSilhouette(
            sprite,
            34,
            3,
            0,
            0,
            6,
            5,
            6,
            5,
            EngineColors.withAlpha(EngineColors.RED, 0.5f)
        )
        drawPixmap(sprite, 44, 2, 0, 0, 6, 5, 12, 10, 30f)
        drawPixmapSilhouette(
            sprite,
            44,
            2,
            0,
            0,
            6,
            5,
            12,
            10,
            EngineColors.withAlpha(EngineColors.WHITE, 0.3f),
            30f
        )
        drawRect(2, 14, 7, 4, EngineColors.withAlpha(EngineColors.CYAN, 0.6f))
        drawPixel(12, 15, EngineColors.YELLOW)
        drawOval(OVAL_X, OVAL_Y, OVAL_W, OVAL_H, EngineColors.withAlpha(EngineColors.WHITE, 0.5f))
        drawOvalOutline(24, 21, 13, 11, EngineColors.YELLOW)
        drawLine(40, 26, 61, 44, EngineColors.RED)
        drawPixmapDithered(sprite, 4, 36, 0, 0, 6, 5, 6, 5, 0.5f)
        drawPixmapDithered(sprite, 14, 34, 0, 0, 6, 5, 11, 9, 0.75f)
    }

    /** Every pixel [draw] changes on a black 64 by 48 frame, with the color it leaves there. */
    private fun lit(draw: (Graphics) -> Unit): Map<Pair<Int, Int>, Int> {
        val graphics = graphics(64, 48)
        graphics.clear(EngineColors.BLACK)
        draw(graphics)
        val frame = graphics.render(1f)
        val lit = HashMap<Pair<Int, Int>, Int>()
        for ((x, y) in frame.pixels()) {
            val rgb = frame[x, y] and 0xFFFFFF
            if (rgb != 0) lit[x to y] = rgb
        }
        return lit
    }

    /** The pixels covered by the rectangles [draw] hands out. */
    private fun runs(draw: ((Int, Int, Int, Int) -> Unit) -> Unit): Set<Pair<Int, Int>> {
        val pixels = HashSet<Pair<Int, Int>>()
        draw { left, top, width, height ->
            for (y in top until top + height) for (x in left until left + width) pixels += x to y
        }
        return pixels
    }

    private fun graphics(
        width: Int,
        height: Int,
        vararg images: Pair<String, ImageBitmap>
    ): ComposeGraphics {
        val byName = mapOf("sprite" to sprite()) + images
        return ComposeGraphics(width, height, { byName.getValue(it) }, createFontFamilyResolver())
    }

    /** The frame drawn into a view [scale] times its size, as a host draws it into a window. */
    private fun ComposeGraphics.render(scale: Float): Picture {
        val viewWidth = (width * scale).roundToInt()
        val viewHeight = (height * scale).roundToInt()
        val image = ImageBitmap(viewWidth, viewHeight)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(image),
            Size(viewWidth.toFloat(), viewHeight.toFloat()),
        ) {
            drawGameFrame(this@render, FrameFit.fitted(width, height, viewWidth, viewHeight))
        }
        val pixels = IntArray(viewWidth * viewHeight)
        image.readPixels(pixels)
        return Picture(viewWidth, viewHeight, pixels)
    }

    /** A drawn picture's pixels, ARGB, a row at a time. */
    private class Picture(val width: Int, val height: Int, private val argb: IntArray) {
        operator fun get(x: Int, y: Int): Int = argb[y * width + x]
        fun colors(): Set<Int> = argb.toSet()
        fun pixels(): List<Pair<Int, Int>> =
            (0 until height).flatMap { y -> (0 until width).map { x -> x to y } }

        fun region(left: Int, top: Int, width: Int, height: Int): List<Int> =
            (top until top + height).flatMap { y ->
                (left until left + width).map { x ->
                    get(
                        x,
                        y
                    )
                }
            }
    }

    private companion object {
        const val PEN_X = 10
        const val SPRITE_X = 3
        const val SPRITE_Y = 4
        const val OVAL_X = 2
        const val OVAL_Y = 21
        const val OVAL_W = 15
        const val OVAL_H = 11

        val BACKGROUND = 0xFF203040.toInt()

        /** A mid gray to light, and a dark at a quarter of full light to light it with. */
        val GRAY = 0xFF808080.toInt()
        val DARK = 0xFF404040.toInt()

        /** One string of each kind the game lays out by its width, at the size it draws it. */
        val GAME_TEXT = listOf(
            "Level: 99" to 15,
            "00:00" to 18,
            "QUEEN ENRAGED" to 26,
            "+25 max health, healed" to 11,
        )

        fun red(argb: Int) = (argb shr 16) and 0xFF
        fun green(argb: Int) = (argb shr 8) and 0xFF

        /** Whether two colors are no more than a step apart in any channel. */
        fun near(a: Int, b: Int): Boolean =
            (0 until 32 step 8).all { shift -> kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= 1 }

        /**
         * A six by five sprite in which every pixel is a color of its own, opaque but for one clear
         * corner - and none of them white, so a white flash shows where it lands.
         */
        fun sprite(): ImageBitmap {
            val image = ImageBitmap(6, 5)
            val canvas = Canvas(image)
            val paint = Paint().apply { isAntiAlias = false }
            for (y in 0 until 5) for (x in 0 until 6) {
                if (x == 5 && y == 0) continue
                paint.color =
                    Color(0xFF000000.toInt() or ((x * 40 + 20) shl 16) or ((y * 45 + 10) shl 8) or ((x + y) * 20 + 30))
                canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
            }
            return image
        }

        /** A disc [size] across in [color], clear round it. */
        fun disc(size: Int, color: Int): ImageBitmap {
            val image = ImageBitmap(size, size)
            val canvas = Canvas(image)
            val paint = Paint().apply { isAntiAlias = false; this.color = Color(color) }
            Raster.oval(0, 0, size, size) { left, top, width, height ->
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    (left + width).toFloat(),
                    (top + height).toFloat(),
                    paint
                )
            }
            return image
        }

        fun solid(width: Int, height: Int, color: Int): ImageBitmap {
            val image = ImageBitmap(width, height)
            val paint = Paint().apply { isAntiAlias = false; this.color = Color(color) }
            Canvas(image).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            return image
        }
    }
}
