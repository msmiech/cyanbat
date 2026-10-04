package at.smiech.cyanbat.resource

import at.smiech.cyanbat.scenery.Daylight
import at.smiech.engine.Pixmap

/** What a stage is flown in front of. */
sealed interface Backdrop {

    /**
     * One strip, tiled end to end and scrolled at the scenery's speed: the jungle, the cave. Laid
     * down and kept covering the frame by `BackgroundScrollingSystem`.
     */
    class Strip(val pixmap: Pixmap) : Backdrop

    /**
     * A sky that turns from noon to night across the stage, over bands of ground scrolling past at
     * their own depths: the desert. Drawn by `NightfallSystem`, off the stage clock.
     *
     * The sky, the sun, the moon and the stars are drawn rather than loaded - they are colors and
     * shapes that change continuously - while the ground is art, drawn once per [Daylight] keyframe
     * and crossfaded between them.
     *
     * @param layers back to front.
     * @param moon a crescent with the rest of its disc in earthshine, faded in at dusk.
     */
    class Nightfall(val layers: List<ParallaxLayer>, val moon: Pixmap) : Backdrop
}

/**
 * One band of ground behind a [Backdrop.Nightfall], scrolling at its own depth.
 *
 * Its sheet holds the same strip once per [Daylight.KEYFRAMES] entry, stacked top to bottom from
 * noon to night, and like every strip it is periodic across its width so it can be tiled end to end.
 *
 * @param top where the strip's top row sits on the 640x360 frame. Everything above the ground in it
 *   is transparent, so the sky shows through.
 * @param speed in framebuffer pixels a tick, like every velocity in the game. The nearest band moves
 *   with the obstacles standing on it; the farther ones move slower, which is what reads as depth.
 */
class ParallaxLayer(val sheet: Pixmap, val top: Int, val speed: Float) {
    /** The height of one keyframe's strip. */
    val rowHeight: Int get() = sheet.height / Daylight.KEYFRAMES.size
}
