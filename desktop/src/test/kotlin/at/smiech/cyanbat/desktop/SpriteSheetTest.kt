package at.smiech.cyanbat.desktop

import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The generated sheets against the layout the game addresses them with.
 *
 * These assets are produced by the scripts in `tools/`, which means they can be regenerated at a
 * different size while the code that walks them stays as it was. Nothing else would catch that:
 * `AnimationSystem` happily advances a source rectangle off the end of a sheet, and the result is
 * not a crash but a sprite that silently turns into empty space part way through its beat.
 *
 * The numbers here are deliberately spelled out rather than derived. A test that computed them the
 * same way the code does would agree with the code about a sheet that had changed underneath both.
 */
class SpriteSheetTest {

    private fun sizeOf(name: String): Pair<Int, Int> {
        val stream = javaClass.getResourceAsStream("/$name")
        assertNotNull(stream, "$name is missing - check the assets/ resources srcDir")
        val image = stream.use { ImageIO.read(it) }
        assertNotNull(image, "$name did not decode as an image")
        return image.width to image.height
    }

    /** Five frames of the bat going limp, the same 45x40 as the flap sheet it is swapped for. */
    @Test
    fun `the death sheet holds five frames`() {
        assertEquals(45 * 5 to 40, sizeOf("cyanBatDeath.png"))
    }

    /** Six frames of 45x40; see `EntityFactory.BAT_FRAME_*`. */
    @Test
    fun `the bat sheet holds six frames`() {
        assertEquals(45 * 6 to 40, sizeOf("cyanBat.png"))
    }

    /** Seven 40x40 frames of rock breaking; see `EntityFactory.SHATTER_FRAME_*`. */
    @Test
    fun `the shatter sheet holds seven frames`() {
        assertEquals(40 * 7 to 40, sizeOf("shatter.png"))
    }

    /** Eight 32x32 frames of one fireball; see `EntityFactory.EXPLOSION_FRAME_*`. */
    @Test
    fun `the explosion sheet holds eight frames`() {
        assertEquals(32 * 8 to 32, sizeOf("explosion.png"))
    }

    /**
     * Every frame has to carry something. The sheet this replaced had two frames that were entirely
     * empty, and the animation played straight through them - a blast that blinked out halfway and
     * came back. Nothing failed; it just looked wrong, which is exactly the kind of thing worth
     * pinning down once it has been fixed.
     */
    @Test
    fun `no frame of the explosion is empty`() {
        assertNoBlankFrames("explosion.png", frameWidth = 32, frames = 8)
    }

    /** Four 32x29 frames of each of the cave's three imps; see `EntityFactory.srcXOf`. */
    @Test
    fun `the cave's sheet holds four frames of each imp`() {
        assertEquals(32 * 4 * 3 to 29, sizeOf("enemies.png"))
    }

    /** Every beat of the bat's wings, every frame of it dying, and every one of the cave's imps. */
    @Test
    fun `no frame of the bat or the cave's creatures is empty`() {
        assertNoBlankFrames("cyanBat.png", frameWidth = 45, frames = 6)
        assertNoBlankFrames("cyanBatDeath.png", frameWidth = 45, frames = 5)
        assertNoBlankFrames("enemies.png", frameWidth = 32, frames = 4 * 3)
    }

    /** Every creature on the forest's sheet, and every beat of the Moth Queen's wings. */
    @Test
    fun `no frame of the forest's creatures is empty`() {
        assertNoBlankFrames("forestEnemies.png", frameWidth = 32, frames = 4 * 5)
        assertNoBlankFrames("forestBoss.png", frameWidth = 96, frames = 4)
    }

    private fun assertNoBlankFrames(name: String, frameWidth: Int, frames: Int) {
        val image = javaClass.getResourceAsStream("/$name")!!.use { ImageIO.read(it) }
        for (frame in 0 until frames) {
            var opaque = 0
            for (y in 0 until image.height) {
                for (x in frame * frameWidth until (frame + 1) * frameWidth) {
                    if ((image.getRGB(x, y) ushr 24) != 0) opaque++
                }
            }
            assertTrue(opaque > 0, "frame $frame of $name is blank")
        }
    }

    /**
     * Seven colorways of one 24x12 bolt: the player's, one per cave enemy type, then the forest's
     * spitter, wisp and Moth Queen; see `EnemySpecies.shotVariant`.
     */
    @Test
    fun `the shot sheet holds a colorway per shooter`() {
        assertEquals(24 * 7 to 12, sizeOf("shot.png"))
    }

    @Test
    fun `the cave strip is three framebuffers wide`() {
        assertEquals(480 * 3 to 320, sizeOf("background.png"))
    }

    @Test
    fun `the forest strip is three framebuffers wide`() {
        assertEquals(480 * 3 to 320, sizeOf("forestBackground.png"))
    }

    /**
     * The strip is tiled end to end as it scrolls, so its last column has to continue into its
     * first. Checked against how much the picture changes between *any* two neighboring columns,
     * because "the edges are similar" means nothing without knowing what similar looks like here.
     *
     * Worth testing rather than trusting: the generator builds every ridgeline out of noise that is
     * periodic across the width, and a change that broke that periodicity would still produce a
     * perfectly good-looking cave - with a seam in it that only shows up once it is moving.
     */
    @Test
    fun `the cave strip meets itself`() {
        assertTiles("background.png")
    }

    /** The same claim for the forest, whose trunks and branches wrap round the seam as well. */
    @Test
    fun `the forest strip meets itself`() {
        assertTiles("forestBackground.png")
    }

    private fun assertTiles(name: String) {
        val image = javaClass.getResourceAsStream("/$name")!!.use { ImageIO.read(it) }

        fun step(left: Int, right: Int) = (0 until image.height).sumOf { y ->
            val a = image.getRGB(left, y)
            val b = image.getRGB(right, y)
            var total = 0
            for (shift in intArrayOf(16, 8, 0)) {
                total += kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
            }
            total
        } / image.height.toDouble()

        val seam = step(image.width - 1, 0)
        val typical = (0 until image.width - 1 step 7).map { step(it, it + 1) }.average()

        assertTrue(
            seam <= typical * 4 + 1,
            "$name does not tile: edge step $seam against a typical $typical",
        )
    }

    /** Three types of four 32x29 frames, on an exact stride; see `EntityFactory.srcXOf`. */
    @Test
    fun `the enemy sheet holds three strips of four frames`() {
        assertEquals(32 * 4 * 3 to 29, sizeOf("enemies.png"))
    }

    /**
     * Five types of four 32x29 frames, the same frame as the cave's so `EnemyGenerator` can size
     * every enemy off either sheet the same way; see `EnemySpecies.strip`.
     */
    @Test
    fun `the forest's enemy sheet holds five strips of four frames`() {
        assertEquals(32 * 4 * 5 to 29, sizeOf("forestEnemies.png"))
    }

    /** Four 96x80 frames; see `MOTH_QUEEN_FRAME_WIDTH`. */
    @Test
    fun `the Moth Queen's sheet holds four frames`() {
        assertEquals(96 * 4 to 80, sizeOf("forestBoss.png"))
    }

    /**
     * The obstacles carry no frames, but their size *is* their hitbox - `createObstacle` takes the
     * collision rectangle straight off the pixmap, and `ObstacleGenerator` anchors a bottom one by
     * its own height. Redrawing one at a different size is a balance change wearing an art change's
     * clothes, so it should have to be deliberate.
     */
    @Test
    fun `the obstacles keep the footprints the cave was balanced around`() {
        assertEquals(41 to 46, sizeOf("topObstacle1.png"))
        assertEquals(38 to 57, sizeOf("topObstacle2.png"))
        assertEquals(76 to 50, sizeOf("bottomObstacle1.png"))
        assertEquals(96 to 54, sizeOf("bottomObstacle2.png"))
    }

    /** The forest keeps the cave's footprints, so its scenery is exactly as hard to fly through. */
    @Test
    fun `the forest's obstacles keep the cave's footprints`() {
        assertEquals(41 to 46, sizeOf("forestTopObstacle1.png"))
        assertEquals(38 to 57, sizeOf("forestTopObstacle2.png"))
        assertEquals(76 to 50, sizeOf("forestBottomObstacle1.png"))
        assertEquals(96 to 54, sizeOf("forestBottomObstacle2.png"))
    }

    /**
     * What separates the generated art from what it replaced. The old sheets carried hundreds to
     * thousands of colors apiece - anti-aliased fringes and resize artifacts - and the whole point
     * of generating them is that a sprite now has as many colors as its palette and no more.
     */
    @Test
    fun `the generated art is flat, not resampled`() {
        for (name in listOf(
            "cyanBat.png",
            "cyanBatDeath.png",
            "enemies.png",
            "shot.png",
            "background.png",
            "explosion.png",
            "shatter.png",
            "topObstacle1.png",
            "topObstacle2.png",
            "bottomObstacle1.png",
            "bottomObstacle2.png",
            "forestEnemies.png",
            "forestBoss.png",
            "forestBackground.png",
            "forestTopObstacle1.png",
            "forestTopObstacle2.png",
            "forestBottomObstacle1.png",
            "forestBottomObstacle2.png",
        )) {
            val image = javaClass.getResourceAsStream("/$name")!!.use { ImageIO.read(it) }
            val colors = buildSet {
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) add(image.getRGB(x, y))
                }
            }
            assertTrue(
                colors.size <= MAX_COLORS,
                "$name has ${colors.size} colors, which means it has been resampled rather than drawn",
            )
        }
    }

    private companion object {
        /**
         * Above the largest palette in `tools/` - the forest's enemy sheet, which carries five
         * creatures' ramps at about three dozen colors - and far below a resized photograph.
         */
        const val MAX_COLORS = 48
    }
}
