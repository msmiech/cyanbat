# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the lagoon's obstacles: what stands up out of the water, each drawn in four lights.

    uv run tools/generate_lagoon_obstacle_sprites.py

The bay is limestone, and its obstacles are the rock the sea has left standing:

* **lagoonObstacle1** - a needle of rock, narrow at its foot where the sea has eaten it, swelling
  above, jungle on its head.
* **lagoonObstacle2** - a pinnacle, sheer and streaked, with trees clinging to its ledges.
* **lagoonObstacle3** - an islet, overhanging all round its notched foot.
* **lagoonObstacle4** - a sea arch: two legs of rock and the sea running through between them.

On the way to the Naga the temple's stones take over from them (`Approach`):

* **templeObstacle1** - the end of a causeway's balustrade: a serpent rearing up off its post into a
  hood of seven heads.
* **templeObstacle2** - a lotus-bud tower, its tiers stepping in to a finial.
* **templeObstacle3** - a length of fallen gallery, its window full of stone balusters, roots over it.
* **templeObstacle4** - a gate: a dark doorway under a great carved face, the wall broken away
  either side.

Every one keeps one of the cave's four footprints, as the desert's do, because an obstacle's size is
its hit box: the lagoon is not made harder by its scenery. Each is drawn **four times, stacked top to
bottom**: night, dawn, sunrise and noon, as the backdrop is (see `generate_lagoon_background.py`),
and the game crossfades them in step with the sky. Lit from the right, by the sun coming up; at night
the palette turns the light round to the moon, going down on the left. The water at the foot of each
is the near water's own, with a ring of foam where it meets the stone.
"""

import pathlib
import random
import sys
from math import cos, pi, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa
from PIL import Image

KEYFRAMES = 4

# One color per keyframe: night, dawn, sunrise, noon.
PALETTE = {
    "o": ((10, 8, 22), (30, 14, 36), (40, 18, 34), (30, 24, 30)),
    "O": ((26, 20, 50), (70, 34, 80), (96, 44, 72), (70, 60, 64)),
    # limestone, from the face turned away from the sun to the face turned to it; at night the moon
    # is on the other side, so the ramp runs the other way
    "s0": ((84, 76, 130), (88, 46, 104), (134, 74, 120), (124, 132, 150)),
    "s1": ((62, 54, 104), (120, 62, 124), (178, 102, 138), (172, 172, 180)),
    "s2": ((44, 38, 84), (164, 86, 146), (232, 146, 148), (214, 210, 208)),
    "s3": ((32, 28, 66), (206, 120, 176), (252, 186, 170), (240, 236, 230)),
    "r": ((26, 20, 58), (82, 40, 98), (138, 70, 104), (150, 120, 112)),
    "rust": ((36, 24, 64), (140, 60, 110), (204, 104, 104), (198, 140, 104)),
    "n": ((14, 10, 34), (40, 22, 66), (62, 36, 82), (60, 66, 84)),
    # the jungle on it
    "j0": ((16, 20, 44), (40, 40, 92), (30, 66, 92), (16, 92, 80)),
    "j1": ((22, 28, 58), (58, 58, 116), (42, 104, 112), (30, 136, 100)),
    "j2": ((30, 34, 70), (96, 84, 144), (118, 160, 124), (96, 192, 112)),
    # the water at its foot, the near water's own, and the foam where it breaks on the stone
    "f0": ((14, 18, 46), (38, 36, 90), (42, 98, 162), (10, 144, 170)),
    "f1": ((80, 74, 140), (244, 160, 206), (255, 216, 196), (236, 254, 252)),
    # the temple's sandstone, the same way round
    "t0": ((80, 70, 128), (92, 46, 98), (142, 78, 104), (130, 108, 102)),
    "t1": ((58, 48, 98), (140, 70, 120), (204, 118, 128), (196, 170, 150)),
    "t2": ((42, 34, 80), (184, 96, 136), (246, 162, 140), (232, 210, 182)),
    "t3": ((30, 24, 64), (222, 136, 170), (255, 206, 176), (248, 236, 214)),
    "g": ((20, 16, 44), (70, 34, 80), (110, 56, 84), (110, 90, 86)),
    "v": ((8, 6, 20), (26, 14, 40), (40, 20, 50), (34, 28, 40)),
    "m": ((22, 26, 54), (70, 64, 110), (100, 120, 110), (76, 150, 96)),
    "root": ((56, 48, 100), (212, 158, 196), (252, 210, 196), (236, 230, 216)),
}

# Across each shape from its left edge to its right: the sun is on the right.
STONE_BANDS = ((0.22, "s0"), (0.5, "s1"), (0.8, "s2"), (1.01, "s3"))
SAND_BANDS = ((0.22, "t0"), (0.5, "t1"), (0.8, "t2"), (1.01, "t3"))
LEAF_BANDS = ((0.35, "j1"), (0.75, "j2"), (1.01, "j1"))

OUT = pathlib.Path(__file__).resolve().parent.parent / "assets"
SEED = 20261005


def paint(grid, pixels, key):
    for x, y in pixels:
        grid[y][x] = key


def water(width, height, rise=4):
    """The water lapping at the foot of it: a few rows of the near water across the bottom."""
    return pa.polygon([(-1, height + 1), (-1, height - rise), (width + 1, height - rise), (width + 1, height + 1)])


def finish(grid, width, height, solid, wet, *, bands=STONE_BANDS, carving=frozenset(), key="g"):
    """
    Shades the stone across from the light, cuts its carving or streaks in, lays the water over its
    foot with foam where the two meet, and rings it all in the outline every obstacle has.
    """
    pa.shade_bands_across(grid, solid - wet, bands)
    paint(grid, carving & (solid - wet), key)
    paint(grid, wet, "f0")
    foam = {(x, y) for x, y in wet if (x, y - 1) in solid and (x, y - 1) not in wet}
    foam |= {(x + dx, y) for x, y in foam for dx in (-1, 1) if (x + dx, y) in wet}
    paint(grid, foam, "f1")
    pa.outer_outline(grid, width, height)
    return grid


def jungle(grid, pixels, rng):
    """Leaves over the top of the rock, lit from the right, their lower edge darker."""
    pa.shade_bands_across(grid, pixels, LEAF_BANDS)
    for x, y in pixels:
        if (x, y + 1) not in pixels:
            grid[y][x] = "j0"
    # A few tufts standing up off the top.
    for x, y in sorted(pixels):
        if (x, y - 1) not in pixels and rng.random() < 0.15 and y > 0:
            grid[y - 1][x] = "j2"


def streaks(solid, rng, count, top=0, length=(4, 14)):
    """Dark runs down the rock where the rain goes, as a set of pixels within [solid]."""
    xs = sorted({x for x, _ in solid})
    out = set()
    for _ in range(count):
        x = rng.choice(xs)
        column = sorted(y for cx, y in solid if cx == x and y >= top)
        if not column:
            continue
        start = rng.choice(column)
        out |= {(x, y) for y in range(start, start + rng.randint(*length)) if (x, y) in solid}
    return out


# --- the bay -------------------------------------------------------------------------------------


def needle(rng):
    """41x46: a needle of rock, narrow at its foot and swelling above it, jungle on its head."""
    width, height = 41, 46
    grid = pa.blank(width, height)
    # Leaning to the right as it rises, the sea having eaten its foot back further on the left.
    rock = pa.rasterize(pa.polygon([
        (15, 46), (15, 38), (12, 30), (9, 22), (6, 14), (8, 8), (14, 5), (24, 4), (31, 6), (36, 11),
        (35, 18), (31, 25), (26, 32), (23, 39), (23, 46),
    ]), width, height)
    head = pa.rasterize(pa.union(
        pa.ellipse(21, 7, 13, 4.5), pa.ellipse(11, 9, 5, 4.2), pa.ellipse(31, 10, 5.5, 4.4), pa.ellipse(22, 3.5, 6, 3),
        pa.capsule((10, 10), (8, 19), 1.6, 1.0), pa.capsule((33, 12), (33, 20), 1.6, 1.0),
    ), width, height)
    wet = pa.rasterize(water(width, height), width, height) & pa.rasterize(pa.polygon([(9, 47), (12, 40), (30, 40), (33, 47)]), width, height)
    notch = {(x, y) for x, y in rock if height - 7 <= y < height - 4}
    finish(grid, width, height, rock | head, wet, carving=frozenset(streaks(rock, rng, 6, top=12)), key="r")
    paint(grid, notch - wet, "n")
    jungle(grid, head, rng)
    pa.outer_outline(grid, width, height)
    return grid


def pinnacle(rng):
    """38x57: a sheer pinnacle, streaked, with trees clinging to its ledges."""
    width, height = 38, 57
    grid = pa.blank(width, height)
    rock = pa.rasterize(pa.polygon([
        (6, 57), (7, 40), (9, 24), (13, 12), (17, 3), (21, 2), (24, 9), (27, 20), (30, 34), (32, 48), (32, 57),
    ]), width, height)
    ledges = pa.rasterize(pa.union(
        pa.ellipse(19, 5, 5, 3.4), pa.ellipse(11, 22, 4.5, 3), pa.ellipse(28, 31, 4, 2.6), pa.ellipse(8, 41, 4, 2.6),
    ), width, height)
    wet = pa.rasterize(water(width, height), width, height) & pa.rasterize(pa.polygon([(3, 58), (5, 50), (34, 50), (36, 58)]), width, height)
    stains = {(x, y) for x, y in rock if 24 < y < 34 and 15 < x < 21}
    finish(grid, width, height, rock | ledges, wet, carving=frozenset(streaks(rock, rng, 7, top=6, length=(6, 18))), key="r")
    paint(grid, stains - wet, "rust")
    paint(grid, {(x, y) for x, y in rock if height - 7 <= y < height - 4} - wet, "n")
    jungle(grid, ledges, rng)
    pa.outer_outline(grid, width, height)
    return grid


def islet(rng):
    """76x50: a mushroom of rock, its foot cut back all round by the sea, jungle over the top."""
    width, height = 76, 50
    grid = pa.blank(width, height)
    rock = pa.rasterize(pa.polygon([
        (24, 50), (22, 42), (14, 38), (6, 30), (5, 20), (10, 13), (22, 9), (38, 8), (54, 9), (66, 13),
        (71, 22), (70, 31), (62, 38), (54, 42), (52, 50),
    ]), width, height)
    top = pa.rasterize(pa.union(
        pa.ellipse(38, 12, 30, 6), pa.ellipse(18, 14, 11, 6), pa.ellipse(58, 15, 11, 6), pa.ellipse(40, 7, 12, 4),
    ), width, height) - pa.rasterize(pa.polygon([(0, 22), (76, 22), (76, 50), (0, 50)]), width, height)
    wet = pa.rasterize(water(width, height), width, height) & pa.rasterize(pa.polygon([(16, 51), (20, 44), (56, 44), (60, 51)]), width, height)
    # The overhang's shadow on the rock under it, where the sea has cut the foot back.
    undercut = {(x, y) for x, y in rock if 36 <= y <= 42}
    finish(grid, width, height, rock | top, wet, carving=frozenset(streaks(rock, rng, 9, top=16)), key="r")
    paint(grid, undercut - wet - top, "n")
    jungle(grid, top, rng)
    pa.outer_outline(grid, width, height)
    return grid


def arch(rng):
    """96x54: a sea arch, two legs of rock with the sea running through between them."""
    width, height = 96, 54
    grid = pa.blank(width, height)
    rock = pa.rasterize(pa.polygon([
        (4, 54), (6, 36), (10, 22), (18, 12), (32, 6), (52, 5), (70, 8), (84, 16), (90, 28), (92, 42), (92, 54),
    ]), width, height)
    hole = pa.rasterize(pa.polygon([
        (32, 55), (32, 42), (36, 32), (46, 27), (58, 28), (66, 34), (68, 44), (68, 55),
    ]), width, height)
    rock -= hole
    top = pa.rasterize(pa.union(
        pa.ellipse(48, 8, 32, 5), pa.ellipse(24, 13, 12, 6), pa.ellipse(74, 12, 13, 6), pa.ellipse(52, 4, 12, 3.4),
    ), width, height) - pa.rasterize(pa.polygon([(0, 20), (96, 20), (96, 54), (0, 54)]), width, height)
    wet = pa.rasterize(water(width, height), width, height) & (rock | hole | pa.rasterize(
        pa.polygon([(0, 55), (2, 49), (94, 49), (96, 55)]), width, height))
    finish(grid, width, height, rock | top, wet, carving=frozenset(streaks(rock, rng, 12, top=14)), key="r")
    # The arch's inside is in its own shadow.
    paint(grid, {(x, y) for x, y in rock if any((x + dx, y) in hole for dx in (-2, -1, 1, 2))} - wet, "n")
    jungle(grid, top, rng)
    # A strand of vines hanging into the arch.
    for vx in (44, 51, 57):
        for vy in range(28, 34 + (vx % 3) * 2):
            if (vx, vy) in hole:
                grid[vy][vx] = "j1"
    pa.outer_outline(grid, width, height)
    return grid


# --- the temple ----------------------------------------------------------------------------------


def naga_post(rng):
    """
    41x46: the end of a causeway's balustrade - the serpent rearing up off its post into a hood of
    seven heads, the way every causeway into the temple begins.
    """
    width, height = 41, 46
    grid = pa.blank(width, height)
    post = pa.rasterize(pa.polygon([(13, 46), (13, 30), (28, 30), (28, 46)]), width, height)
    neck = pa.rasterize(pa.capsule((22, 31), (20, 20), 5.0, 4.5), width, height)
    # The hood a fan rather than a disc: narrow at the neck, opening out to the heads round its rim.
    hood = pa.rasterize(pa.polygon([(15, 21), (6, 16), (8, 9), (14, 6), (20.5, 5), (27, 6), (33, 9), (35, 16), (26, 21)]), width, height)
    heads = set()
    eyes = set()
    for k in range(7):
        angle = pi * (0.02 + 0.96 * k / 6)
        hx = 20.5 + cos(angle) * 16.5
        hy = 17.0 - sin(angle) * 13.5
        heads |= pa.rasterize(pa.ellipse(hx, hy, 2.9, 2.6), width, height)
        # Its snout, poking out from the fan.
        heads |= pa.rasterize(pa.ellipse(hx + cos(angle) * 2.2, hy - sin(angle) * 1.6, 1.6, 1.4), width, height)
        eyes.add((int(hx - 1), int(hy)))
    solid = post | neck | hood | heads
    wet = pa.rasterize(water(width, height), width, height) & pa.rasterize(pa.polygon([(9, 47), (11, 41), (30, 41), (32, 47)]), width, height)
    carving = {(x, y) for x, y in post if y in (34, 38)} | {(20, y) for y in range(21, 30, 3)} | eyes
    # The hood's ribs, fanning out from the neck to each head.
    for k in range(6):
        angle = pi * (0.02 + 0.96 * (k + 0.5) / 6)
        for t in range(5, 15):
            carving.add((int(20.5 + cos(angle) * t), int(17.0 - sin(angle) * t * 0.8)))
    finish(grid, width, height, solid, wet, bands=SAND_BANDS, carving=frozenset(carving) & solid)
    paint(grid, {(x, y) for x, y in post if y == 30}, "m")
    pa.outer_outline(grid, width, height)
    return grid


def tower(rng):
    """38x57: a lotus-bud tower on its plinth, its tiers stepping in to a finial, moss on its ledges."""
    width, height = 38, 57
    grid = pa.blank(width, height)
    plinth = pa.rasterize(pa.polygon([(4, 57), (4, 46), (34, 46), (34, 57)]), width, height)
    body = set()
    tiers = set()
    for y in range(4, 47):
        t = (46 - y) / 42.0
        hw = 13.0 * (1.0 + 0.45 * sin(pi * t * 0.9)) * (1.0 - t) ** 0.55
        if (46 - y) % 5 == 0:
            hw -= 1.0
            tiers |= {(x, y) for x in range(width)}
        body |= {(x, y) for x in range(int(round(19 - hw)), int(round(19 + hw)) + 1)}
    finial = {(19, y) for y in range(0, 5)} | {(18, 3), (20, 3)}
    door = pa.rasterize(pa.polygon([(15, 46), (15, 36), (19, 32), (23, 36), (23, 46)]), width, height)
    solid = plinth | body | finial
    wet = pa.rasterize(water(width, height), width, height) & pa.rasterize(pa.polygon([(1, 58), (3, 52), (35, 52), (37, 58)]), width, height)
    carving = (tiers & body) | {(x, 50) for x in range(4, 35)}
    finish(grid, width, height, solid, wet, bands=SAND_BANDS, carving=frozenset(carving))
    paint(grid, door - wet, "v")
    moss = {(x, y) for x, y in body if (x, y - 1) not in body and y > 10 and (x * 7 + y) % 3 != 0}
    paint(grid, moss, "m")
    pa.outer_outline(grid, width, height)
    return grid


def gallery(rng):
    """76x50: a length of fallen gallery, its window full of balusters and a fig's roots over it."""
    width, height = 76, 50
    grid = pa.blank(width, height)
    wall = pa.rasterize(pa.polygon([
        (4, 50), (4, 16), (10, 10), (52, 10), (58, 16), (62, 24), (68, 26), (72, 36), (72, 50),
    ]), width, height)
    window = pa.rasterize(pa.polygon([(14, 22), (44, 22), (44, 36), (14, 36)]), width, height)
    balusters = {(x, y) for x, y in window if (x - 14) % 4 in (1, 2)}
    solid = wall
    wet = pa.rasterize(water(width, height), width, height) & pa.rasterize(pa.polygon([(0, 51), (2, 45), (74, 45), (76, 51)]), width, height)
    courses = {(x, y) for x, y in wall if (y - 10) % 7 == 0} | {(x, y) for x, y in wall if (x + ((y - 10) // 7) * 6) % 13 == 0 and y > 38}
    finish(grid, width, height, solid, wet, bands=SAND_BANDS, carving=frozenset(courses))
    paint(grid, window - balusters, "v")
    for x, y in balusters:
        grid[y][x] = "t2" if (x - 14) % 4 == 2 else "t1"
    # The fig's roots, poured down over the stones from the top of the wall.
    roots = set()
    for start, drift in ((50, 0.25), (56, -0.2), (62, 0.1), (30, -0.15)):
        x = float(start)
        for y in range(10, 46):
            roots |= {(int(x), y), (int(x) + 1, y)}
            x += drift + 0.45 * sin(y * 0.35 + start)
    paint(grid, roots & (wall - wet), "root")
    paint(grid, {(x, y) for x, y in wall if (x, y - 1) not in wall and 10 < x < 52}, "m")
    pa.outer_outline(grid, width, height)
    return grid


def gate(rng):
    """96x54: a gate - a dark doorway under a great carved face, the wall broken away either side."""
    width, height = 96, 54
    grid = pa.blank(width, height)
    wall = pa.rasterize(pa.polygon([
        (2, 54), (2, 36), (8, 30), (16, 33), (24, 26), (30, 26), (30, 54),
    ]), width, height) | pa.rasterize(pa.polygon([
        (66, 54), (66, 24), (74, 24), (80, 30), (88, 28), (94, 36), (94, 54),
    ]), width, height)
    tower = pa.rasterize(pa.polygon([(28, 54), (28, 14), (34, 8), (40, 4), (56, 4), (62, 8), (68, 14), (68, 54)]), width, height)
    finial = pa.rasterize(pa.polygon([(44, 4), (48, 0), (52, 4)]), width, height)
    door = pa.rasterize(pa.polygon([(40, 55), (40, 40), (43, 37), (48, 35), (53, 37), (56, 40), (56, 55)]), width, height)
    solid = wall | tower | finial
    wet = pa.rasterize(water(width, height), width, height) & pa.rasterize(pa.polygon([(0, 55), (2, 49), (94, 49), (96, 55)]), width, height)
    face = set()
    # The face: a brow, lowered eyes, a broad nose and a calm smile, carved over the doorway.
    face |= {(x, 12) for x in range(36, 61)}
    for side in (-1, 1):
        face |= {(48 + side * dx, 17) for dx in range(3, 9)}
        face |= {(48 + side * 3, 18), (48 + side * 8, 18)}
        face |= {(48 + side * 16, y) for y in range(15, 27)}
    face |= {(49, y) for y in range(15, 25)} | {(x, 25) for x in range(45, 53)}
    face |= {(x, 29 - (1 if abs(x - 48) > 5 else 0)) for x in range(41, 56)}
    face |= {(x, 31) for x in range(44, 53)}
    courses = {(x, y) for x, y in wall if (y - 26) % 6 == 0} | {(x, y) for x, y in tower if y in (8, 34)}
    finish(grid, width, height, solid, wet, bands=SAND_BANDS, carving=frozenset(face | courses))
    paint(grid, door - wet, "v")
    paint(grid, {(x, y) for x, y in solid if (x, y - 1) not in solid and y > 6 and (x % 5) != 0}, "m")
    pa.outer_outline(grid, width, height)
    return grid


def save_keyframes(path, grid):
    height = len(grid)
    width = len(grid[0])
    image = Image.new("RGBA", (width, height * KEYFRAMES), (0, 0, 0, 0))
    for frame in range(KEYFRAMES):
        for y, row in enumerate(grid):
            for x, key in enumerate(row):
                if key != pa.TRANSPARENT:
                    image.putpixel((x, frame * height + y), PALETTE[key][frame] + (255,))
    image.save(path)
    return image


def main() -> int:
    rng = random.Random(SEED)
    for prefix, builds in (("lagoonObstacle", (needle, pinnacle, islet, arch)),
                           ("templeObstacle", (naga_post, tower, gallery, gate))):
        for index, build in enumerate(builds, start=1):
            path = OUT / f"{prefix}{index}.png"
            image = save_keyframes(path, build(rng))
            print(f"{path} ({image.width}x{image.height // KEYFRAMES}, {KEYFRAMES} keyframes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
