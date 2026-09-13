"""Shared machinery for the sprite generators in this directory.

The game's art is generated rather than drawn by hand, so that it stays editable: the scripts next
to this one describe *what a thing is* - a skeleton with shapes hung off it - and this turns that
into flat, hard-edged pixel art.

Three rules hold across every sheet the game ships, and they are what make the sprites look like
they belong to one another:

* **Hard edges.** Shapes are supersampled and then thresholded, never blended. A sprite has as many
  colors as its palette and not one more - the artwork this replaced carried 2169 colors in 97x40
  pixels, most of them ghosts of some past resize.
* **Banded shading.** A region is lit by quantizing its own depth into a handful of tones, per
  column, so the gradient follows the shape rather than running flat across it.
* **A dark outline, outwards.** Every sprite is ringed in one near-black tone, which is what lets
  it read against both the pale and the dark parts of the cave.

Nothing here knows about any particular sprite. Sizes are passed in rather than read from a global,
because the sheets differ: the bat is 45x40 a frame, an enemy 32x29, and an obstacle is its own
odd size.
"""

from math import hypot

# Supersampling used to decide whether a pixel is inside a shape. Coverage is thresholded rather
# than blended: the point is accurate silhouettes, not soft ones.
SUBSAMPLES = 3
COVERAGE_THRESHOLD = 0.5

TRANSPARENT = "."
OUTLINE = "o"


# --- geometry ------------------------------------------------------------------------------------


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def capsule(p, q, r_start, r_end):
    """A segment with a radius that tapers from end to end - a limb, a tail, a finger bone."""
    px, py = p
    qx, qy = q
    dx, dy = qx - px, qy - py
    span = dx * dx + dy * dy

    def inside(x, y):
        t = 0.0 if span == 0 else max(0.0, min(1.0, ((x - px) * dx + (y - py) * dy) / span))
        return hypot(x - (px + dx * t), y - (py + dy * t)) <= r_start + (r_end - r_start) * t

    return inside


def polygon(points):
    def inside(x, y):
        hit = False
        j = len(points) - 1
        for i, (xi, yi) in enumerate(points):
            xj, yj = points[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                hit = not hit
            j = i
        return hit

    return inside


def union(*shapes):
    return lambda x, y: any(shape(x, y) for shape in shapes)


def intersection(*shapes):
    return lambda x, y: all(shape(x, y) for shape in shapes)


def without(shape, hole):
    return lambda x, y: shape(x, y) and not hole(x, y)


def offset(shape, dx, dy):
    return lambda x, y: shape(x - dx, y - dy)


def bezier(start, control, end, steps=8):
    """Points along a quadratic curve, for a scalloped or bowed edge between two points."""
    out = []
    for i in range(1, steps):
        t = i / steps
        u = 1 - t
        out.append(
            (
                u * u * start[0] + 2 * u * t * control[0] + t * t * end[0],
                u * u * start[1] + 2 * u * t * control[1] + t * t * end[1],
            )
        )
    return out


def distance_to_segments(point, segments):
    best = float("inf")
    x, y = point
    for (px, py), (qx, qy) in segments:
        dx, dy = qx - px, qy - py
        span = dx * dx + dy * dy
        t = 0.0 if span == 0 else max(0.0, min(1.0, ((x - px) * dx + (y - py) * dy) / span))
        best = min(best, hypot(x - (px + dx * t), y - (py + dy * t)))
    return best


# --- rasterizing ---------------------------------------------------------------------------------


def blank(width, height):
    return [[TRANSPARENT for _ in range(width)] for _ in range(height)]


def rasterize(shape, width, height):
    """A shape sampled onto a grid of this size, hard-edged."""
    filled = set()
    weight = 1.0 / (SUBSAMPLES * SUBSAMPLES)
    for y in range(height):
        for x in range(width):
            coverage = 0.0
            for sy in range(SUBSAMPLES):
                for sx in range(SUBSAMPLES):
                    px = x + (sx + 0.5) / SUBSAMPLES
                    py = y + (sy + 0.5) / SUBSAMPLES
                    if shape(px, py):
                        coverage += weight
            if coverage >= COVERAGE_THRESHOLD:
                filled.add((x, y))
    return filled


def neighbors(x, y):
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if dx or dy:
                yield x + dx, y + dy


def outline_against(grid, subject, others, color=OUTLINE):
    """Darkens every [subject] pixel that touches one of [others], so the two read apart."""
    edge = {p for p in subject if any(n in others for n in neighbors(*p))}
    for x, y in edge:
        grid[y][x] = color
    return edge


def outer_outline(grid, width, height, color=OUTLINE):
    """
    Rings the whole silhouette, outwards into pixels nothing else claimed.

    Outwards rather than inwards so the shapes above keep the size they were drawn at; an inward
    ring would eat a one-pixel-wide detail entirely.
    """
    filled = {
        (x, y)
        for y in range(height)
        for x in range(width)
        if grid[y][x] != TRANSPARENT
    }
    for y in range(height):
        for x in range(width):
            if grid[y][x] == TRANSPARENT and any(n in filled for n in neighbors(x, y)):
                grid[y][x] = color


def shade_bands(grid, pixels, bands, extent=None):
    """
    Bands a region from its own top edge to its own bottom edge, column by column.

    Per column rather than over the whole region, so the gradient follows the shape: on a body the
    belly stays on the belly where it is deep, instead of sliding up onto the muzzle where it is
    shallow.

    [bands] is a sequence of (threshold, material) read in order, where threshold is the depth
    below which that material applies, as 0..1.

    [extent] is the shape the bands are measured against, where that is wider than the pixels
    actually painted. A torso needs it: the head overlaps and claims its pixels first, and a torso
    banded against only what is left would put its dark back in the sliver under the jaw.
    """
    columns = {}
    for x, y in extent if extent is not None else pixels:
        low, high = columns.get(x, (y, y))
        columns[x] = (min(low, y), max(high, y))

    for x, y in pixels:
        top, bottom = columns[x]
        depth = 0.0 if bottom == top else (y - top) / (bottom - top)
        for threshold, material in bands:
            if depth < threshold:
                grid[y][x] = material
                break
        else:
            grid[y][x] = bands[-1][1]


def shade_bands_across(grid, pixels, bands):
    """
    [shade_bands] turned ninety degrees: banded row by row, from each row's left edge to its right.

    Which axis a thing is lit along is a property of the thing. A body is lit top to bottom, so its
    belly can be pale; a spire of rock is lit from one side, because banding a vertical shape down
    its length would shade it by *height* and make the tip read as a different material from the
    base rather than as the same rock turned away from the light.
    """
    rows = {}
    for x, y in pixels:
        low, high = rows.get(y, (x, x))
        rows[y] = (min(low, x), max(high, x))

    for x, y in pixels:
        left, right = rows[y]
        depth = 0.0 if right == left else (x - left) / (right - left)
        for threshold, material in bands:
            if depth < threshold:
                grid[y][x] = material
                break
        else:
            grid[y][x] = bands[-1][1]


# --- output --------------------------------------------------------------------------------------


def save_sheet(path, grids, palette, frame_width, frame_height):
    """Writes [grids] side by side as one sheet, laid out left to right."""
    from PIL import Image

    sheet = Image.new("RGBA", (frame_width * len(grids), frame_height), (0, 0, 0, 0))
    for index, grid in enumerate(grids):
        for y, row in enumerate(grid):
            for x, key in enumerate(row):
                if key != TRANSPARENT:
                    sheet.putpixel((index * frame_width + x, y), palette[key])
    sheet.save(path)
    return sheet


def save_single(path, grid, palette):
    """Writes one grid as its own image - an obstacle, which is not part of any sheet."""
    from PIL import Image

    height = len(grid)
    width = len(grid[0])
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, key in enumerate(row):
            if key != TRANSPARENT:
                image.putpixel((x, y), palette[key])
    image.save(path)
    return image
