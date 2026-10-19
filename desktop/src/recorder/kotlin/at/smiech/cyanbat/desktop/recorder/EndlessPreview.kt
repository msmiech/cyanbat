package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.desktop.DesktopGame
import at.smiech.cyanbat.ecs.EndlessScenerySystem
import at.smiech.cyanbat.ecs.WeatherSystem
import at.smiech.cyanbat.scenery.Biome
import at.smiech.cyanbat.scenery.EndlessDay
import at.smiech.cyanbat.scenery.EndlessScenery
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.EngineColors
import at.smiech.engine.ecs.World
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random

/**
 * Renders the endless stage's card for the stage select: four of its lands side by side, cut on a
 * slant, each at its own hour and in its own weather, with the bat and hostiles from every stage.
 *
 * The other stages' previews are composed from their art by tools/generate_stage_previews.py; this
 * stage's art is made as the game runs, so its preview is made by the same code, headless, from
 * fixed seeds, and comes out the same every time. Re-run it after changing how the land or the
 * weather is drawn:
 *
 *     ./gradlew :desktop:renderEndlessPreview
 *
 * Its one argument is where to write the picture.
 */
fun main(args: Array<String>) {
    val out = File(
        args.firstOrNull() ?: "game/src/commonMain/composeResources/drawable/stage5_preview.png"
    )
    val game = DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT)
    val slices = SLICES.map { (biome, hour) -> render(game, biome, hour) }

    val image = BufferedImage(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until FRAME_BUFFER_HEIGHT) {
        for (x in 0 until FRAME_BUFFER_WIDTH) {
            // Slanted cuts, so each land reads as a glimpse rather than a panel.
            val along = x + (y - FRAME_BUFFER_HEIGHT / 2f) * SLANT
            val slice = (along / (FRAME_BUFFER_WIDTH / SLICES.size.toFloat())).toInt()
                .coerceIn(0, SLICES.lastIndex)
            image.setRGB(x, y, slices[slice][y * FRAME_BUFFER_WIDTH + x])
        }
    }
    val graphics = image.createGraphics()

    // The bat, flying into it all, and something from every stage coming the other way.
    fun sprite(
        sheet: String,
        x: Int,
        y: Int,
        frameX: Int,
        width: Int,
        height: Int,
        scale: Int = 1
    ) {
        val art = ImageIO.read(DesktopGame::class.java.getResourceAsStream("/$sheet"))
        graphics.drawImage(
            art.getSubimage(frameX, 0, width, height),
            x,
            y,
            width * scale,
            height * scale,
            null
        )
    }
    sprite("cyanBat.png", 120, 158, BAT_FRAME_WIDTH, BAT_FRAME_WIDTH, BAT_HEIGHT)
    sprite("jungleEnemies.png", 300, 88, 0, ENEMY_FRAME_WIDTH, ENEMY_HEIGHT)
    sprite("jungleEnemies.png", 322, 104, ENEMY_FRAME_WIDTH, ENEMY_FRAME_WIDTH, ENEMY_HEIGHT)
    sprite("enemies.png", 470, 70, 2 * ENEMY_STRIP, ENEMY_FRAME_WIDTH, ENEMY_HEIGHT)
    sprite("desertEnemies.png", 415, 190, ENEMY_STRIP, ENEMY_FRAME_WIDTH, ENEMY_HEIGHT)
    sprite("lagoonEnemies.png", 540, 236, 4 * ENEMY_STRIP, ENEMY_FRAME_WIDTH, ENEMY_HEIGHT)
    graphics.dispose()
    out.parentFile?.mkdirs()
    ImageIO.write(image, "png", out)
    println("Wrote $out")
}

/** The lands shown, left to right, each at the hour of its day it is shown at. */
private val SLICES = listOf(
    Biome.PEAKS to 0.2f,
    Biome.VOLCANO to 0.6f,
    Biome.SWAMP to 0.82f,
    Biome.FOGGY_FOREST to 0.08f,
)

/** How far the cuts lean, in pixels across for each pixel down. */
private const val SLANT = 0.35f

/** How long each land is flown before it is shot, for the weather to fill the sky. */
private const val SETTLE_SECONDS = 12f

private const val BAT_FRAME_WIDTH = 45
private const val BAT_HEIGHT = 40
private const val ENEMY_FRAME_WIDTH = 32
private const val ENEMY_STRIP = 4 * ENEMY_FRAME_WIDTH
private const val ENEMY_HEIGHT = 29

/** [biome] at [hour] round its day, as a frame's ARGB pixels. */
private fun render(game: DesktopGame, biome: Biome, hour: Float): IntArray {
    val g = game.graphics
    val start = ((hour - EndlessDay.START + 1f) % 1f) * EndlessDay.SECONDS - SETTLE_SECONDS
    val scenery = EndlessScenery(
        g,
        FRAME_BUFFER_WIDTH,
        Random(biome.ordinal),
        seed = PREVIEW_SEED,
        first = biome,
        startSeconds = start
    )
    val world = World()
    world.addSystem(
        EndlessScenerySystem(
            scenery,
            g.newPixmap("lagoonMoon.png"),
            FRAME_BUFFER_WIDTH,
            FRAME_BUFFER_HEIGHT
        )
    )
    val weather =
        WeatherSystem(scenery, FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT, Random(biome.ordinal))
    world.addSystem(weather)
    world.addSystem(weather.front)
    var t = 0f
    while (t < SETTLE_SECONDS) {
        world.update(TICK_INITIAL, null)
        t += TICK_INITIAL
    }
    g.clear(EngineColors.BLACK)
    world.draw(g)
    return game.capture().copyOf()
}

private const val PREVIEW_SEED = 20261013
