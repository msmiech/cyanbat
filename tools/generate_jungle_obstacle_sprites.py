# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the jungle's obstacles: two hanging from the canopy, two standing on the floor.

    uv run tools/generate_jungle_obstacle_sprites.py

The cave's obstacles are stalactites and stalagmites; the jungle's are pieces of the ruins it has
grown over, and what has grown over them:

* **A block caught in roots**: a dressed stone from some lintel, held up in the canopy by the roots
  of the strangler fig that pulled it down, a stepped cross carved in its face.
* **A chain**, hanging out of the canopy from whatever it once held up, with a broken slab still
  hooked on its end and moss trailing off it.
* **A wall** of the old masonry, fitted stones of every shape locked together without mortar, a
  trapezoidal niche in it and a bromeliad on its top.
* **A plinth**, stepped, with the stump of a pillar on it and ferns at its foot.

The cave's decision about brightness is kept, and for the reason it gives: an obstacle is a hazard
that costs the bat a third of its health, not scenery. So these are pale - weathered grey stone,
lichen-grey wood and a bright leaf green - against a backdrop that stays well under them, and they
are ringed in near-black so the silhouette holds wherever it lands. The carvings glow: they play the
cave's crystal veins, a little of the bat's cyan set into the scenery.

Sizes match the cave's four exactly. Obstacles take their collision box from the pixmap and the
generator anchors the bottom ones by their height, so the jungle keeps the cave's footprint and
difficulty from scenery.
"""

import pathlib
import random
import sys
from math import cos, pi, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (8, 14, 10, 255),
    "O": (18, 30, 22, 255),
    # stone: weathered and pale, a cool grey
    "s0": (82, 92, 90, 255),
    "s1": (120, 130, 126, 255),
    "s2": (162, 172, 166, 255),
    "s3": (204, 212, 204, 255),
    # wood, roots: lichen-grey, cool enough to stay out of the hostiles' warm range
    "r0": (84, 96, 86, 255),
    "r1": (122, 136, 120, 255),
    "r2": (164, 178, 158, 255),
    # leaves and moss
    "g0": (34, 90, 50, 255),
    "g1": (60, 138, 70, 255),
    "g2": (108, 190, 96, 255),
    "g3": (170, 230, 140, 255),
    # the glow of the carvings, the jungle's crystal
    "k0": (18, 66, 70, 255),
    "k1": (40, 150, 150, 255),
    "k2": (120, 230, 210, 255),
}

STONE_BANDS = ((0.22, "s3"), (0.55, "s2"), (0.84, "s1"), (1.01, "s0"))
LEAF_BANDS = ((0.3, "g3"), (0.6, "g2"), (0.85, "g1"), (1.01, "g0"))
MOSS_BANDS = ((0.4, "g2"), (1.01, "g1"))

OUT = pathlib.Path(__file__).resolve().parent.parent / "assets"

SEED = 20261004

# The stepped cross the Inca carved everywhere, the chakana: seven by seven, a cross of three
# steps a side round a hole at its heart.
CHAKANA = (
    "..###..",
    "..#.#..",
    "###.###",
    "#.....#",
    "###.###",
    "..#.#..",
    "..###..",
)


def bend(start, control, end, steps=12):
    return [start] + pa.bezier(start, control, end, steps) + [end]


def stroke(points, r_start, r_end=None):
    r_end = r_start if r_end is None else r_end
    count = len(points) - 1
    return pa.union(*(
        pa.capsule(a, b, r_start + (r_end - r_start) * i / count, r_start + (r_end - r_start) * (i + 1) / count)
        for i, (a, b) in enumerate(zip(points, points[1:]))
    ))


def fitted(grid, region, cells, rng, bands=STONE_BANDS):
    """
    Lays [region] in fitted stones, the old masonry's way: blocks of every shape - each pixel
    belongs to the stone whose heart is nearest, a little wider than tall - with a dark joint
    wherever two meet, and each stone lit across from the left on its own, so it reads as pillowed.
    """
    xs = [x for x, _ in region]
    ys = [y for _, y in region]
    seeds = [
        (rng.uniform(min(xs), max(xs) + 1), rng.uniform(min(ys), max(ys) + 1))
        for _ in range(cells)
    ]
    owner = {}
    for x, y in region:
        owner[(x, y)] = min(range(len(seeds)), key=lambda i: ((x - seeds[i][0]) * 0.75) ** 2 + (y - seeds[i][1]) ** 2)
    stones = {}
    for pixel, index in owner.items():
        stones.setdefault(index, set()).add(pixel)
    for pixels in stones.values():
        pa.shade_bands_across(grid, pixels, bands)
    joints = {
        (x, y) for (x, y), index in owner.items()
        if owner.get((x + 1, y), index) != index or owner.get((x, y + 1), index) != index
    }
    for x, y in joints:
        grid[y][x] = "O"
    return joints


def coursed(grid, region, rng, course=(5, 7), block=(9, 15)):
    """Lays [region] in courses of squared blocks, each course its own height, joints staggered."""
    xs = [x for x, _ in region]
    ys = [y for _, y in region]
    y = min(ys)
    while y <= max(ys):
        height = rng.randint(*course)
        x = min(xs) - rng.randint(0, block[0])
        while x <= max(xs):
            width = rng.randint(*block)
            stone = {(px, py) for px, py in region if x <= px < x + width and y <= py < y + height}
            if stone:
                pa.shade_bands_across(grid, stone, STONE_BANDS)
                for px, py in stone:
                    if px == x + width - 1 or py == y + height - 1:
                        grid[py][px] = "O"
            x += width
        y += height


def carve(grid, left, top, pattern=CHAKANA):
    """Carves [pattern] into the stone at ([left], [top]), glowing, with a dark rim round its cut."""
    cut = {
        (left + col, top + row)
        for row, line in enumerate(pattern)
        for col, ch in enumerate(line)
        if ch == "#"
    }
    for x, y in cut:
        grid[y][x] = "k2" if (x + y) % 3 else "k1"
    rim = {
        (x + dx, y + dy) for x, y in cut for dx, dy in ((1, 0), (0, 1), (1, 1))
    } - cut
    for x, y in rim:
        if grid[y][x] not in (".", "o"):
            grid[y][x] = "k0"


def moss_on(grid, top_of, rng, thickness=(1, 3)):
    """Moss along the top of a shape: the first pixel or few of every column of [top_of]."""
    columns = {}
    for x, y in top_of:
        columns[x] = min(columns.get(x, y), y)
    moss = set()
    for x, y in columns.items():
        for k in range(rng.randint(*thickness)):
            if (x, y + k) in top_of:
                moss.add((x, y + k))
    pa.shade_bands(grid, moss, MOSS_BANDS)
    return moss


def frond_leaf(base, angle, length, width):
    """A pointed leaf off [base] along [angle]: the bromeliad's and the fern's."""
    tip = (base[0] + cos(angle) * length, base[1] + sin(angle) * length)
    side = (cos(angle + pi / 2) * width, sin(angle + pi / 2) * width)
    mid = (base[0] + cos(angle) * length * 0.35, base[1] + sin(angle) * length * 0.35)
    return pa.polygon([base, (mid[0] + side[0], mid[1] + side[1]), tip, (mid[0] - side[0], mid[1] - side[1])])


def bromeliad(cx, cy, size):
    """A rosette of stiff pointed leaves fanning up and out of one heart."""
    leaves = []
    for index in range(7):
        angle = pi + (index + 0.5) / 7 * pi
        reach = size * (0.6 + 0.4 * sin(pi * (index + 0.5) / 7))
        leaves.append(frond_leaf((cx, cy), angle, reach, size * 0.18))
    return pa.union(*leaves)


def rooted_block(width, height, rng):
    """
    A dressed stone held up in the canopy by the roots that pulled it out of its wall: two roots
    coming down out of the canopy and round its sides, their tips dangling on below it, and a stepped
    cross carved in its face. The roots are darker than the stone and thinner than it, so the stone
    is what the eye lands on.
    """
    grid = pa.blank(width, height)
    top, bottom = height * 0.22, height * 0.78
    left, right = width * 0.06, width * 0.94
    # Tilted a little, as it hangs in the roots, and broken at one corner.
    block = pa.rasterize(pa.polygon([
        (left, top + 3), (right - 4, top), (right, bottom - 4), (right - 7, bottom - 1),
        (right - 9, bottom + 1), (left + 2, bottom + 2),
    ]), width, height)

    roots = [
        stroke(bend((width * 0.34, 0.0), (width * 0.02, height * 0.45), (width * 0.12, height - 1.5), steps=14), 2.0, 0.7),
        stroke(bend((width * 0.62, 0.0), (width * 1.0, height * 0.5), (width * 0.86, height - 3.0), steps=14), 2.0, 0.7),
        # A thinner one wrapped once across its face, low down.
        stroke(bend((width * 0.08, bottom - 6), (width * 0.5, bottom + 1), (width * 0.92, bottom - 10)), 0.9),
    ]
    wood = pa.rasterize(pa.union(
        *roots,
        # Where they come out of the canopy, a knot of them.
        pa.ellipse(width * 0.48, 0.0, width * 0.3, height * 0.07),
    ), width, height)

    leaves = pa.rasterize(pa.union(
        frond_leaf((width * 0.36, height * 0.06), pi * 0.8, 8, 2.6),
        frond_leaf((width * 0.62, height * 0.06), pi * 0.2, 9, 2.6),
    ), width, height)

    stone = block - wood - leaves
    fitted(grid, stone, cells=3, rng=rng)
    moss_on(grid, stone, rng)
    carve(grid, int(width * 0.5) - 3, int((top + bottom) / 2) - 4)
    pa.shade_bands_across(grid, wood - leaves, ((0.4, "r1"), (1.01, "r0")))
    pa.outline_against(grid, stone, wood, color="O")
    pa.shade_bands(grid, leaves, LEAF_BANDS)
    pa.outer_outline(grid, width, height)
    return grid


def chain(width, height, rng):
    """
    A great chain hanging out of the canopy, its links alternately face on and edge on, and on its
    end the broken slab it still holds - trapezoidal, like everything the old masons cut - with moss
    trailing off it and a stepped cross carved in it.
    """
    grid = pa.blank(width, height)
    cx = width * 0.5
    links_bottom = height * 0.52
    face = set()
    edge = set()
    holes = set()
    y = -4.0
    index = 0
    while y < links_bottom:
        if index % 2 == 0:
            face |= pa.rasterize(pa.ellipse(cx, y + 7, 5.6, 7.6), width, height)
            holes |= pa.rasterize(pa.ellipse(cx, y + 7, 2.2, 4.0), width, height)
        else:
            edge |= pa.rasterize(pa.capsule((cx, y + 1), (cx, y + 13), 2.0, 2.0), width, height)
        y += 9.5
        index += 1
    face -= holes
    edge -= face

    slab_top = links_bottom + 2
    slab = pa.rasterize(pa.polygon([
        (width * 0.16, slab_top), (width * 0.84, slab_top),
        (width * 0.98, height - 9), (width * 0.62, height - 7), (width * 0.55, height - 3),
        (width * 0.02, height - 8),
    ]), width, height)
    # The iron ring it hangs by, through a hole worn in its top.
    ring = pa.rasterize(pa.ellipse(cx, slab_top + 1, 4.0, 4.0), width, height) - \
        pa.rasterize(pa.ellipse(cx, slab_top + 1, 1.8, 1.8), width, height)

    trailing = []
    for x0, length in ((0.12, 0.9), (0.28, 0.6), (0.76, 0.75), (0.9, 0.5)):
        trailing.append(stroke(
            [(width * x0 + sin(t) * 1.2, height - 8 + t * (height * 0.16 * length) / 4) for t in range(5)],
            1.2, 0.5,
        ))
    moss_hang = pa.rasterize(pa.union(*trailing), width, height) - slab

    pa.shade_bands_across(grid, slab, STONE_BANDS)
    moss_on(grid, slab, rng, thickness=(1, 2))
    carve(grid, int(cx) - 4, int(slab_top + (height - 8 - slab_top) / 2) - 3)
    # The iron, weathered to the stone's grey: the edge-on links a shade darker, being in shadow.
    pa.shade_bands_across(grid, face | ring, ((0.3, "s2"), (0.7, "s1"), (1.01, "s0")))
    for x, y in edge - ring:
        grid[y][x] = "s0" if x > cx else "s1"
    pa.outline_against(grid, edge - ring, face | ring, color="O")
    pa.outline_against(grid, slab - ring, ring, color="O")
    pa.shade_bands(grid, moss_hang, MOSS_BANDS)
    pa.tear(grid, holes)
    pa.outer_outline(grid, width, height)
    return grid


def wall(width, height, rng):
    """
    A stretch of the old wall: stones of every shape fitted without mortar, its face leaning back as
    it rises, its top broken where stones have fallen, a trapezoidal niche in it, and a bromeliad
    rooted in the moss on top.
    """
    grid = pa.blank(width, height)
    bottom = height - 1.0
    top = height * 0.3
    # The broken top: whole stones gone, down to a step at one end.
    outline = [
        (width * 0.02, bottom + 1), (width * 0.07, top + 10), (width * 0.24, top + 9),
        (width * 0.27, top + 2), (width * 0.55, top), (width * 0.6, top + 6), (width * 0.8, top + 7),
        (width * 0.84, top + 16), (width * 0.95, top + 17), (width * 0.98, bottom + 1),
    ]
    face = pa.rasterize(pa.polygon(outline), width, height)
    niche_cx = width * 0.42
    niche = pa.rasterize(pa.polygon([
        (niche_cx - 6.5, bottom - 6), (niche_cx - 4.5, bottom - 25), (niche_cx + 4.5, bottom - 25), (niche_cx + 6.5, bottom - 6),
    ]), width, height)

    plant = pa.rasterize(bromeliad(width * 0.7, top + 8, 13), width, height) - face
    fern_leaves = pa.rasterize(pa.union(
        frond_leaf((width * 0.07, bottom), pi * 1.28, 15, 3.6),
        frond_leaf((width * 0.09, bottom), pi * 1.5, 13, 3.4),
        frond_leaf((width * 0.95, bottom), pi * 1.72, 15, 3.6),
        frond_leaf((width * 0.93, bottom), pi * 1.5, 12, 3.4),
    ), width, height)

    stones = face - niche
    fitted(grid, stones, cells=17, rng=rng)
    moss_on(grid, stones, rng)
    pa.shade_bands(grid, niche, ((0.25, "O"), (1.01, "o")))
    pa.outline_against(grid, stones, niche, color="O")
    carve(grid, int(niche_cx) - 3, int(bottom - 19))
    pa.shade_bands(grid, plant, LEAF_BANDS)
    pa.shade_bands(grid, fern_leaves - plant, LEAF_BANDS)
    pa.outline_against(grid, stones - fern_leaves, fern_leaves, color="O")
    pa.outer_outline(grid, width, height)
    return grid


def plinth(width, height, rng):
    """
    A stepped plinth of squared stone, and on it the stump of a pillar, broken off in a jagged slant,
    moss on its break and ferns grown up at its foot.
    """
    grid = pa.blank(width, height)
    bottom = height - 1.0
    lower = pa.rasterize(pa.polygon([
        (width * 0.02, bottom + 1), (width * 0.04, bottom - 12), (width * 0.96, bottom - 12), (width * 0.98, bottom + 1),
    ]), width, height)
    upper = pa.rasterize(pa.polygon([
        (width * 0.14, bottom - 11), (width * 0.16, bottom - 21), (width * 0.84, bottom - 21), (width * 0.86, bottom - 11),
    ]), width, height)
    cx = width * 0.5
    half = width * 0.13
    pillar = pa.rasterize(pa.polygon([
        (cx - half, bottom - 20), (cx - half, 9), (cx - half * 0.4, 3), (cx + half * 0.1, 8),
        (cx + half * 0.5, 0), (cx + half, 6), (cx + half, bottom - 20),
    ]), width, height)
    # The pillar's base, a collar a little wider than its shaft.
    collar = pa.rasterize(pa.polygon([
        (cx - half - 3, bottom - 20), (cx - half - 3, bottom - 25), (cx + half + 3, bottom - 25), (cx + half + 3, bottom - 20),
    ]), width, height)

    ferns = []
    for x0, lean in ((0.08, -1.0), (0.24, 1.0), (0.72, -1.0), (0.9, 1.0)):
        for k, spread in enumerate((0.25, 0.55, 0.85)):
            angle = -pi / 2 + lean * spread
            ferns.append(frond_leaf((width * x0, bottom - 11), angle, 16 - k * 2, 3.4))
    fern_leaves = pa.rasterize(pa.union(*ferns), width, height)

    coursed(grid, lower | upper, rng, course=(5, 6), block=(10, 16))
    shaft = pillar - collar
    coursed(grid, shaft, rng, course=(7, 9), block=(30, 30))
    pa.shade_bands_across(grid, collar, STONE_BANDS)
    # Flutes down the shaft, a darker line every few pixels, so it is a pillar and not a tower.
    for x, y in shaft:
        if (x - int(cx - half)) % 6 == 5 and grid[y][x] != "O":
            grid[y][x] = "s1" if x < cx else "s0"
    moss_on(grid, shaft, rng, thickness=(2, 3))
    moss_on(grid, upper - collar - pillar, rng, thickness=(1, 2))
    moss_on(grid, lower - upper, rng, thickness=(1, 2))
    pa.outline_against(grid, shaft, collar, color="O")
    pa.outline_against(grid, lower, upper, color="O")
    leaves = fern_leaves - pillar - collar
    pa.shade_bands(grid, leaves, LEAF_BANDS)
    pa.outline_against(grid, (lower | upper) - leaves, leaves, color="O")
    carve(grid, int(cx) - 3, int(bottom - 9) - 2)
    pa.outer_outline(grid, width, height)
    return grid


# name, draw, width, height - sizes held at the cave's
OBSTACLES = (
    ("jungleTopObstacle1.png", rooted_block, 41, 46),
    ("jungleTopObstacle2.png", chain, 38, 57),
    ("jungleBottomObstacle1.png", wall, 76, 50),
    ("jungleBottomObstacle2.png", plinth, 96, 54),
)


def main() -> int:
    rng = random.Random(SEED)
    for name, draw, width, height in OBSTACLES:
        grid = draw(width, height, rng)
        image = pa.save_single(OUT / name, grid, PALETTE)
        print(f"{OUT / name} ({image.width}x{image.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
