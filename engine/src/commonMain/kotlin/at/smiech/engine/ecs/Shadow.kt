package at.smiech.engine.ecs

import at.smiech.engine.Lighting
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** The shadow a convex outline casts from a point light. */
internal object Shadow {

    /**
     * Adds to [into] the shadow that the convex outline in [points], from index [from] until
     * [until], casts from a light at ([lightX], [lightY]) reaching [reach] pixels. Returns false,
     * adding nothing, when the light is inside the outline or on its edge.
     *
     * The outline is x, y pairs in frame pixels, wound with a positive signed area as
     * [ConvexHull] winds them. The edges facing away from the light form one chain
     * round its far side, between the two points the light's rays graze; the shadow
     * is everything beyond that chain, out along the two grazing rays and closed by
     * an arc well past the light's reach. The outline itself is outside the shadow,
     * so an occluder is lit on its near side and not darkened by its own shadow.
     *
     * Every shadow is wound the same way, opposite to its outline, so any number of them filled as
     * one path cover their union.
     */
    fun cast(
        points: FloatArray,
        from: Int,
        until: Int,
        lightX: Float,
        lightY: Float,
        reach: Float,
        into: Lighting.Light,
    ): Boolean {
        val corners = (until - from) / 2
        if (corners < 3) return false

        // The chain facing away from the light starts at the corner after the last edge facing it
        // and ends where the next edge facing it begins.
        var first = -1
        var last = -1
        var previous = facesLight(points, from, corners, corners - 1, lightX, lightY)
        for (corner in 0 until corners) {
            val current = facesLight(points, from, corners, corner, lightX, lightY)
            if (previous && !current) first = corner
            if (!previous && current) last = corner
            previous = current
        }
        // No edge facing it: the light is inside, or on the edge.
        if (first < 0 || last < 0) return false

        var corner = first
        while (true) {
            into.addShadowPoint(points[from + 2 * corner], points[from + 2 * corner + 1])
            if (corner == last) break
            corner = (corner + 1) % corners
        }

        // Back from the end of the chain to its start along an arc beyond the light's square. Steps
        // of at most an eighth of a turn keep each chord within a few percent of the radius, and
        // twice the reach clears the square's corners.
        val firstAngle = angle(points, from, first, lightX, lightY)
        val lastAngle = angle(points, from, last, lightX, lightY)
        var sweep = firstAngle - lastAngle
        if (sweep > PI_F) sweep -= TWO_PI_F
        if (sweep < -PI_F) sweep += TWO_PI_F
        val steps = maxOf(1, ceil(abs(sweep) / MAX_ARC_STEP).toInt())
        val far = reach * FAR_REACH
        for (step in 0..steps) {
            val at = lastAngle + sweep * step / steps
            into.addShadowPoint(lightX + cos(at) * far, lightY + sin(at) * far)
        }
        into.closeShadow()
        return true
    }

    /**
     * Whether the edge from corner [edge] to the next faces the light. An outline
     * wound with a positive area has its inside on the positive side of each edge,
     * so a light on the negative side faces it.
     */
    private fun facesLight(
        points: FloatArray,
        from: Int,
        corners: Int,
        edge: Int,
        lightX: Float,
        lightY: Float
    ): Boolean {
        val next = (edge + 1) % corners
        val ax = points[from + 2 * edge]
        val ay = points[from + 2 * edge + 1]
        val bx = points[from + 2 * next]
        val by = points[from + 2 * next + 1]
        return (bx - ax) * (lightY - ay) - (by - ay) * (lightX - ax) < 0f
    }

    /** The angle from the light to corner [corner], in radians. */
    private fun angle(
        points: FloatArray,
        from: Int,
        corner: Int,
        lightX: Float,
        lightY: Float
    ): Float =
        atan2(points[from + 2 * corner + 1] - lightY, points[from + 2 * corner] - lightX)

    private const val PI_F = PI.toFloat()
    private const val TWO_PI_F = (2.0 * PI).toFloat()
    private const val MAX_ARC_STEP = (PI / 4.0).toFloat()
    private const val FAR_REACH = 2f
}
