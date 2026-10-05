package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.ecs.ElitePalette
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.SHOT_BODY_COLORS
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

    /**
     * Five frames of the bat going limp, the same 45x40 as the flap sheet it is swapped for. One row
     * only, battered: the bat falls on it whatever its health was, and `WoundSystem` leaves the dead
     * on the row they have.
     */
    @Test
    fun `the death sheet holds five frames`() {
        assertEquals(45 * 5 to 40, sizeOf("cyanBatDeath.png"))
    }

    /** Six frames of 45x40, unhurt, wounded and battered; see `EntityFactory.BAT_FRAME_*`. */
    @Test
    fun `the bat sheet holds six frames in every state`() {
        assertEquals(45 * 6 to 40 * WOUND_ROWS, sizeOf("cyanBat.png"))
    }

    /** Six 14x14 frames of an orb, its glint going round it; see `ORB_FRAME` and `ORB_FRAME_COUNT`. */
    @Test
    fun `the orb sheet holds six frames`() {
        assertEquals(14 * 6 to 14, sizeOf("orb.png"))
        assertNoBlankFrames("orb.png", frameWidth = 14, frames = 6)
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
        assertEquals(32 * 4 * 3 to 29 * WOUND_ROWS, sizeOf("enemies.png"))
    }

    /**
     * Every beat of the bat's wings, every frame of it dying, and every one of the cave's imps, in
     * every state. A wounded row left empty would make a creature vanish the moment it was hurt.
     */
    @Test
    fun `no frame of the bat or the cave's creatures is empty`() {
        assertNoBlankFrames("cyanBat.png", frameWidth = 45, frames = 6, rows = WOUND_ROWS)
        assertNoBlankFrames("cyanBatDeath.png", frameWidth = 45, frames = 5)
        assertNoBlankFrames("enemies.png", frameWidth = 32, frames = 4 * 3, rows = WOUND_ROWS)
    }

    /** Every creature on the jungle's sheet, and every beat of the Moth Queen's wings. */
    @Test
    fun `no frame of the jungle's creatures is empty`() {
        assertNoBlankFrames("jungleEnemies.png", frameWidth = 32, frames = 4 * 5, rows = WOUND_ROWS)
        assertNoBlankFrames("jungleBoss.png", frameWidth = 96, frames = 4, rows = WOUND_ROWS)
    }

    /**
     * Every creature on the desert's sheet, and every part of the Sand Wyrm and its sand. The sand
     * is never hurt, so its four frames are only drawn in the top row.
     */
    @Test
    fun `no frame of the desert's creatures is empty`() {
        assertNoBlankFrames("desertEnemies.png", frameWidth = 32, frames = 4 * 5, rows = WOUND_ROWS)
        assertNoBlankFrames("desertBoss.png", frameWidth = 48, frames = 10, rowHeight = 48)
        assertNoBlankFrames("desertBoss.png", frameWidth = 48, frames = 6, rows = WOUND_ROWS)
    }

    /**
     * Every creature on the lagoon's sheet, and every part of the Naga and its water. The water is
     * never hurt, so its four frames are only drawn in the top row.
     */
    @Test
    fun `no frame of the lagoon's creatures is empty`() {
        assertNoBlankFrames("lagoonEnemies.png", frameWidth = 32, frames = 4 * 5, rows = WOUND_ROWS)
        assertNoBlankFrames("lagoonBoss.png", frameWidth = 64, frames = 11, rowHeight = 64)
        assertNoBlankFrames("lagoonBoss.png", frameWidth = 64, frames = 7, rows = WOUND_ROWS)
    }

    /**
     * A wound has to show. Every frame of every wounded row is checked against the same frame a row
     * up - the healthier picture - because a row that came out the same as it would be a mark the
     * player crosses without anything happening on screen.
     */
    @Test
    fun `every wound changes the picture`() {
        for ((name, frameWidth, frames) in listOf(
            Triple("cyanBat.png", 45, 6),
            Triple("enemies.png", 32, 4 * 3),
            Triple("jungleEnemies.png", 32, 4 * 5),
            Triple("desertEnemies.png", 32, 4 * 5),
            Triple("jungleBoss.png", 96, 4),
            Triple("desertBoss.png", 48, 6),
            Triple("lagoonEnemies.png", 32, 4 * 5),
            Triple("lagoonBoss.png", 64, 7),
        )) {
            val image = javaClass.getResourceAsStream("/$name")!!.use { ImageIO.read(it) }
            val rowHeight = image.height / WOUND_ROWS
            for (row in 1 until WOUND_ROWS) {
                for (frame in 0 until frames) {
                    val changed = (0 until rowHeight).sumOf { y ->
                        (frame * frameWidth until (frame + 1) * frameWidth).count { x ->
                            image.getRGB(x, row * rowHeight + y) != image.getRGB(x, (row - 1) * rowHeight + y)
                        }
                    }
                    assertTrue(changed > 0, "frame $frame of $name looks the same in row $row as in the row above")
                }
            }
        }
    }

    /**
     * @param rows how many rows of the sheet to check, from the top, each [rowHeight] tall - which
     *   is the sheet's height over [rows] unless it is given.
     */
    private fun assertNoBlankFrames(
        name: String,
        frameWidth: Int,
        frames: Int,
        rows: Int = 1,
        rowHeight: Int? = null,
    ) {
        val image = javaClass.getResourceAsStream("/$name")!!.use { ImageIO.read(it) }
        val height = rowHeight ?: (image.height / rows)
        for (row in 0 until rows) {
            for (frame in 0 until frames) {
                var opaque = 0
                for (y in row * height until (row + 1) * height) {
                    for (x in frame * frameWidth until (frame + 1) * frameWidth) {
                        if ((image.getRGB(x, y) ushr 24) != 0) opaque++
                    }
                }
                assertTrue(opaque > 0, "frame $frame of row $row of $name is blank")
            }
        }
    }

    /**
     * Fourteen colorways of one 24x12 bolt: the player's, one per cave enemy type, then the jungle's
     * spitter, wisp and Moth Queen, the Sand Wyrm's, one per elite palette, and the Naga's; see
     * `EnemySpecies.shotVariant`, `SAND_WYRM_SHOT_VARIANT`, `ElitePalette` and `NAGA_SHOT_VARIANT`.
     */
    @Test
    fun `the shot sheet holds a colorway per shooter`() {
        assertEquals(24 * 14 to 12, sizeOf("shot.png"))
    }

    /**
     * An elite's bolts are the color of its glow, and the two are declared apart - the glow in
     * `ElitePalette`, the bolts in `generate_shot_sprite.py`. This holds them together: the body of
     * each colorway, read off the sheet, is exactly its palette's rim.
     */
    /**
     * In the dark a shot gives off the light of its body's color, which the game declares apart from
     * the sheet, in `SHOT_BODY_COLORS`. This holds the two together, colorway by colorway.
     */
    @Test
    fun `every shot gives off the color of its bolt`() {
        val image = javaClass.getResourceAsStream("/shot.png").use { ImageIO.read(it) }
        assertEquals(image.width / 24, SHOT_BODY_COLORS.size, "a light color for every colorway")
        for (variant in SHOT_BODY_COLORS.indices) {
            val body = image.getRGB(variant * 24 + 12, 5)
            assertEquals(
                SHOT_BODY_COLORS[variant] and 0xFFFFFF,
                body and 0xFFFFFF,
                "colorway $variant's bolt is #%06X, its light #%06X"
                    .format(body and 0xFFFFFF, SHOT_BODY_COLORS[variant] and 0xFFFFFF),
            )
        }
    }

    @Test
    fun `every elite's bolts are the color of its glow`() {
        val image = javaClass.getResourceAsStream("/shot.png").use { ImageIO.read(it) }
        for (palette in ElitePalette.entries) {
            // The middle of the bolt's body, clear of its tail and its tip.
            val body = image.getRGB(palette.shotVariant * 24 + 12, 5)
            assertEquals(
                palette.aura.rim and 0xFFFFFF,
                body and 0xFFFFFF,
                "$palette's bolts are #%06X, its glow's rim #%06X"
                    .format(body and 0xFFFFFF, palette.aura.rim and 0xFFFFFF),
            )
        }
    }

    @Test
    fun `the cave strip is as tall as the frame`() {
        assertEquals(1440 to FRAME_BUFFER_HEIGHT, sizeOf("background.png"))
    }

    @Test
    fun `the jungle strip is as tall as the frame`() {
        assertEquals(1440 to FRAME_BUFFER_HEIGHT, sizeOf("jungleBackground.png"))
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

    /** The same claim for the jungle, whose trunks, fronds and vines wrap round the seam as well. */
    @Test
    fun `the jungle strip meets itself`() {
        assertTiles("jungleBackground.png")
    }

    /**
     * The desert's three bands, each four keyframes of one strip stacked from noon to night; see
     * `Daylight.KEYFRAMES` and `GameAssets`, which places them. The far and middle bands are two
     * framebuffers wide rather than three: they scroll slower, so they take no less time to repeat.
     */
    @Test
    fun `the desert's bands hold four keyframes of their strips`() {
        assertEquals(960 to 116 * KEYFRAMES, sizeOf("desertFar.png"))
        assertEquals(960 to 90 * KEYFRAMES, sizeOf("desertMid.png"))
        assertEquals(1440 to 57 * KEYFRAMES, sizeOf("desertNear.png"))
        assertEquals(24 to 24, sizeOf("desertMoon.png"))
    }

    /** Every band tiles, in every light: the temples, the palms and the stones wrap round too. */
    @Test
    fun `the desert's bands meet themselves`() {
        assertTiles("desertFar.png")
        assertTiles("desertMid.png")
        assertTiles("desertNear.png")
    }

    /** A keyframe left empty would crossfade the ground into nothing at that hour. */
    @Test
    fun `no keyframe of the desert's scenery is empty`() {
        for (name in DESERT_KEYFRAMED) {
            val image = javaClass.getResourceAsStream("/$name")!!.use { ImageIO.read(it) }
            val rowHeight = image.height / KEYFRAMES
            for (row in 0 until KEYFRAMES) {
                val opaque = (row * rowHeight until (row + 1) * rowHeight).sumOf { y ->
                    (0 until image.width).count { x -> (image.getRGB(x, y) ushr 24) != 0 }
                }
                assertTrue(opaque > 0, "keyframe $row of $name is blank")
            }
        }
    }

    /**
     * The lagoon's bands, each four keyframes of one strip stacked from night to noon, and the temple
     * strips that take over three of them; see `Daybreak.KEYFRAMES` and `GameAssets`, which places
     * them. A band and the strip that takes it over are the same size, as `SkySystem` requires.
     */
    @Test
    fun `the lagoon's bands hold four keyframes of their strips`() {
        assertEquals(1280 to 120 * KEYFRAMES, sizeOf("lagoonClouds.png"))
        assertEquals(960 to 128 * KEYFRAMES, sizeOf("lagoonSea.png"))
        assertEquals(960 to 72 * KEYFRAMES, sizeOf("lagoonFar.png"))
        assertEquals(960 to 72 * KEYFRAMES, sizeOf("lagoonFarTemple.png"))
        assertEquals(960 to 120 * KEYFRAMES, sizeOf("lagoonMid.png"))
        assertEquals(960 to 120 * KEYFRAMES, sizeOf("lagoonMidTemple.png"))
        assertEquals(1440 to 58 * KEYFRAMES, sizeOf("lagoonNear.png"))
        assertEquals(1440 to 58 * KEYFRAMES, sizeOf("lagoonNearTemple.png"))
        assertEquals(32 to 32, sizeOf("lagoonMoon.png"))
    }

    /** Every band tiles in every light: the islands, the ruins and the boats wrap round too. */
    @Test
    fun `the lagoon's bands meet themselves`() {
        for (name in LAGOON_BANDS) assertTiles(name)
    }

    /**
     * The game switches a band to the temple's strip a stretch at a time as it comes into view, so
     * the end of either has to follow on into the start of the other: both strips of a pair share
     * their first and their last columns, in every light.
     */
    @Test
    fun `the lagoon's bands meet the temple's strips that take them over`() {
        for ((band, ahead) in listOf(
            "lagoonFar.png" to "lagoonFarTemple.png",
            "lagoonMid.png" to "lagoonMidTemple.png",
            "lagoonNear.png" to "lagoonNearTemple.png",
        )) {
            val a = javaClass.getResourceAsStream("/$band")!!.use { ImageIO.read(it) }
            val b = javaClass.getResourceAsStream("/$ahead")!!.use { ImageIO.read(it) }
            for (x in intArrayOf(0, a.width - 1)) {
                for (y in 0 until a.height) {
                    assertEquals(a.getRGB(x, y), b.getRGB(x, y), "$band and $ahead differ at column $x, row $y")
                }
            }
        }
    }

    /** The lagoon keeps the cave's footprints, standing - the limestone, and the temple's stones after it. */
    @Test
    fun `the lagoon's obstacles keep the cave's footprints in every light`() {
        for (prefix in listOf("lagoonObstacle", "templeObstacle")) {
            assertEquals(41 to 46 * KEYFRAMES, sizeOf("${prefix}1.png"))
            assertEquals(38 to 57 * KEYFRAMES, sizeOf("${prefix}2.png"))
            assertEquals(76 to 50 * KEYFRAMES, sizeOf("${prefix}3.png"))
            assertEquals(96 to 54 * KEYFRAMES, sizeOf("${prefix}4.png"))
        }
    }

    /** Five types of four 32x29 frames, like every stage's; see `EnemySpecies.strip`. */
    @Test
    fun `the lagoon's enemy sheet holds five strips of four frames`() {
        assertEquals(32 * 4 * 5 to 29 * WOUND_ROWS, sizeOf("lagoonEnemies.png"))
    }

    /** The head twice, four sizes of body, the tail and four frames of water; see `NAGA_FRAME`. */
    @Test
    fun `the Naga's sheet holds its parts and its water`() {
        assertEquals(64 * 11 to 64 * WOUND_ROWS, sizeOf("lagoonBoss.png"))
    }

    @Test
    fun `no keyframe of the lagoon's scenery is empty`() {
        for (name in LAGOON_KEYFRAMED) {
            val image = javaClass.getResourceAsStream("/$name")!!.use { ImageIO.read(it) }
            val rowHeight = image.height / KEYFRAMES
            for (row in 0 until KEYFRAMES) {
                val opaque = (row * rowHeight until (row + 1) * rowHeight).sumOf { y ->
                    (0 until image.width).count { x -> (image.getRGB(x, y) ushr 24) != 0 }
                }
                assertTrue(opaque > 0, "keyframe $row of $name is blank")
            }
        }
    }

    @Test
    fun `the lagoon's scenery is flat in every light`() {
        for (name in LAGOON_KEYFRAMED) {
            val rowHeight = sizeOf(name).second / KEYFRAMES
            for (row in 0 until KEYFRAMES) assertFlat(name, row * rowHeight until (row + 1) * rowHeight)
        }
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
        assertEquals(32 * 4 * 3 to 29 * WOUND_ROWS, sizeOf("enemies.png"))
    }

    /**
     * Five types of four 32x29 frames, the same frame as the cave's so `EnemyGenerator` can size
     * every enemy off either sheet the same way; see `EnemySpecies.strip`.
     */
    @Test
    fun `the jungle's enemy sheet holds five strips of four frames`() {
        assertEquals(32 * 4 * 5 to 29 * WOUND_ROWS, sizeOf("jungleEnemies.png"))
    }

    /** Five types of four 32x29 frames, like the jungle's; see `EnemySpecies.strip`. */
    @Test
    fun `the desert's enemy sheet holds five strips of four frames`() {
        assertEquals(32 * 4 * 5 to 29 * WOUND_ROWS, sizeOf("desertEnemies.png"))
    }

    /** The head twice, three plates, the tail and four frames of sand; see `SAND_WYRM_FRAME`. */
    @Test
    fun `the Sand Wyrm's sheet holds its parts and its sand`() {
        assertEquals(48 * 10 to 48 * WOUND_ROWS, sizeOf("desertBoss.png"))
    }

    /** Four 96x80 frames; see `MOTH_QUEEN_FRAME_WIDTH`. */
    @Test
    fun `the Moth Queen's sheet holds four frames`() {
        assertEquals(96 * 4 to 80 * WOUND_ROWS, sizeOf("jungleBoss.png"))
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

    /**
     * The desert keeps the cave's four footprints too - all four standing, under its open sky -
     * each drawn in the four lights of its day, one above the other.
     */
    @Test
    fun `the desert's obstacles keep the cave's footprints in every light`() {
        assertEquals(41 to 46 * KEYFRAMES, sizeOf("desertObstacle1.png"))
        assertEquals(38 to 57 * KEYFRAMES, sizeOf("desertObstacle2.png"))
        assertEquals(76 to 50 * KEYFRAMES, sizeOf("desertObstacle3.png"))
        assertEquals(96 to 54 * KEYFRAMES, sizeOf("desertObstacle4.png"))
    }

    /** The jungle keeps the cave's footprints, so its scenery is exactly as hard to fly through. */
    @Test
    fun `the jungle's obstacles keep the cave's footprints`() {
        assertEquals(41 to 46, sizeOf("jungleTopObstacle1.png"))
        assertEquals(38 to 57, sizeOf("jungleTopObstacle2.png"))
        assertEquals(76 to 50, sizeOf("jungleBottomObstacle1.png"))
        assertEquals(96 to 54, sizeOf("jungleBottomObstacle2.png"))
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
            "orb.png",
            "background.png",
            "explosion.png",
            "shatter.png",
            "topObstacle1.png",
            "topObstacle2.png",
            "bottomObstacle1.png",
            "bottomObstacle2.png",
            "jungleEnemies.png",
            "jungleBoss.png",
            "jungleBackground.png",
            "jungleTopObstacle1.png",
            "jungleTopObstacle2.png",
            "jungleBottomObstacle1.png",
            "jungleBottomObstacle2.png",
            "desertEnemies.png",
            "desertBoss.png",
            "desertMoon.png",
            "lagoonEnemies.png",
            "lagoonBoss.png",
            "lagoonMoon.png",
        )) {
            assertFlat(name, 0 until sizeOf(name).second)
        }
    }

    /**
     * The same claim for the scenery drawn in four lights, made of each light on its own: a sheet
     * of four pictures carries four palettes, but each picture is as flat as any other.
     */
    @Test
    fun `the desert's scenery is flat in every light`() {
        for (name in DESERT_KEYFRAMED) {
            val rowHeight = sizeOf(name).second / KEYFRAMES
            for (row in 0 until KEYFRAMES) assertFlat(name, row * rowHeight until (row + 1) * rowHeight)
        }
    }

    private fun assertFlat(name: String, rows: IntRange) {
        val image = javaClass.getResourceAsStream("/$name")!!.use { ImageIO.read(it) }
        val colors = buildSet {
            for (y in rows) {
                for (x in 0 until image.width) add(image.getRGB(x, y))
            }
        }
        assertTrue(
            colors.size <= MAX_COLORS,
            "$name has ${colors.size} colors in rows $rows, which means it has been resampled rather than drawn",
        )
    }

    private companion object {
        /**
         * Above the largest palette in `tools/` - the jungle's enemy sheet, which carries five
         * creatures' ramps at about three dozen colors - and far below a resized photograph.
         */
        const val MAX_COLORS = 48

        /** Noon, the golden hour, sunset and night; see `Daylight.KEYFRAMES`. */
        const val KEYFRAMES = 4

        /** Unhurt, wounded and battered, top to bottom; see `WOUND_ROWS` and `WoundComponent`. */
        const val WOUND_ROWS = 3

        /** The lagoon's bands, which tile end to end. */
        val LAGOON_BANDS = listOf(
            "lagoonClouds.png", "lagoonSea.png", "lagoonFar.png", "lagoonFarTemple.png",
            "lagoonMid.png", "lagoonMidTemple.png", "lagoonNear.png", "lagoonNearTemple.png",
        )

        /** The lagoon's scenery, drawn once in each of the [KEYFRAMES] - night to noon - and stacked. */
        val LAGOON_KEYFRAMED = LAGOON_BANDS + (1..4).flatMap { listOf("lagoonObstacle$it.png", "templeObstacle$it.png") }

        /** The desert's scenery, drawn once in each of the [KEYFRAMES] and stacked. */
        val DESERT_KEYFRAMED = listOf(
            "desertFar.png", "desertMid.png", "desertNear.png",
            "desertObstacle1.png", "desertObstacle2.png", "desertObstacle3.png", "desertObstacle4.png",
        )
    }
}
