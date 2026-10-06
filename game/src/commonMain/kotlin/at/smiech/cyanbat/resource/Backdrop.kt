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
     * A sky that changes with the time of day across the stage, over bands of ground scrolling at
     * their own depths: the desert, flown from noon into night, and the lagoon, from night to noon.
     * Drawn by `SkySystem` from the stage clock.
     *
     * The sky, sun and stars are drawn in code, since they change continuously; the ground is art,
     * drawn once per keyframe of its [day] and crossfaded between them.
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
 * @param speed in framebuffer pixels per tick, like every velocity in the game. The nearest band
 *   moves with the obstacles standing on it; farther ones move slower, which reads as depth.
 * @param ahead what the band turns into on the way to the boss: from [aheadFrom] of the way through
 *   the day, every stretch not yet in view is drawn from this sheet, so the new scenery scrolls in
 *   from the right. The same size as [sheet], sharing its first and last columns so either follows
 *   the other without a seam.
 * @param water the frame rows where this band is open water, which a low sun lays a path of glints
 *   across; null for none.
 */
class ParallaxLayer(
    val sheet: Pixmap,
    val top: Int,
    val speed: Float,
    val ahead: Pixmap? = null,
    val aheadFrom: Float = 1f,
    val water: IntRange? = null,
)
