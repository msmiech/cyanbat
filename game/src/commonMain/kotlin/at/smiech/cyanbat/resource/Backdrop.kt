package at.smiech.cyanbat.resource

import at.smiech.cyanbat.scenery.Day
import at.smiech.engine.Pixmap

/** What a stage is flown in front of. */
sealed interface Backdrop {

    /**
     * One strip, tiled end to end and scrolled at the scenery's speed: the jungle, the cave. Laid
     * down and kept covering the frame by `BackgroundScrollingSystem`.
     */
    class Strip(val pixmap: Pixmap) : Backdrop

    /**
     * A sky that changes with the time of day across the stage, over bands of ground scrolling past
     * at their own depths: the desert, flown from noon into night, and the lagoon, from night to
     * noon. Drawn by `SkySystem`, off the stage clock.
     *
     * The sky, the sun, the moon and the stars are drawn rather than loaded - they are colors and
     * shapes that change continuously - while the ground is art, drawn once per keyframe of its
     * [day] and crossfaded between them.
     *
     * @param day what the light does across the stage.
     * @param layers back to front.
     * @param moon the moon, faded in or out as the night comes or goes.
     * @param horizonY where the sky's gradient ends, on the 640x360 frame: level with the far ground,
     *   which takes over from it.
     */
    class Sky(
        val day: Day,
        val layers: List<ParallaxLayer>,
        val moon: Pixmap,
        val horizonY: Int,
    ) : Backdrop
}

/**
 * One band of ground behind a [Backdrop.Sky], scrolling at its own depth.
 *
 * Its sheet holds the same strip once per keyframe of the sky's day, stacked top to bottom in the
 * day's order, and like every strip it is periodic across its width so it can be tiled end to end.
 *
 * @param top where the strip's top row sits on the 640x360 frame. Everything above the ground in it
 *   is transparent, so the sky shows through.
 * @param speed in framebuffer pixels a tick, like every velocity in the game. The nearest band moves
 *   with the obstacles standing on it; the farther ones move slower, which is what reads as depth.
 * @param ahead what the band turns into on the way to the stage's boss: from [aheadFrom] of the way
 *   through the day, every stretch of the strip that has not yet come into view is drawn from this
 *   sheet instead, so the new scenery scrolls in from the right as if it were being flown toward. The
 *   same size as [sheet], and drawn to meet it: the two share their first and last columns, so either
 *   follows on from the other without a seam.
 * @param water the rows of the frame where this band is open water, which a low sun lays a path of
 *   glints across; null for a band with none showing.
 */
class ParallaxLayer(
    val sheet: Pixmap,
    val top: Int,
    val speed: Float,
    val ahead: Pixmap? = null,
    val aheadFrom: Float = 1f,
    val water: IntRange? = null,
)
