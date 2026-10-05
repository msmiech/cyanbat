# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the bands the lagoon stage scrolls past, and the moon that sets over it.

    uv run tools/generate_lagoon_background.py

The lagoon is flown across a bay of limestone islands from night into noon, toward a temple standing
in the water. Its sky is drawn by the game (`Daybreak`), so what stands under it is bands, as in the
desert, back to front:

* **lagoonClouds.png** - clouds high up, barely moving: violet before the dawn, lit pink and peach
  from below as the sun comes up, white over lavender by noon.
* **lagoonSea.png** - the open sea from the horizon to the bottom of the frame, its waves longer and
  wider apart the nearer they are.
* **lagoonFar.png** - limestone towers far off on the horizon, pale with the distance.
* **lagoonMid.png** - nearer towers: sheer cliffs streaked with rust, jungle on their tops, the sea
  cut into their feet, and their reflections under them.
* **lagoonNear.png** - the water the obstacles stand in, with rocks breaking it and longtail boats
  moored in it, garlands on their prows.

On the way to the Naga three of them turn into the temple's: **lagoonFarTemple.png**, its towers on
the horizon over the moat; **lagoonMidTemple.png**, its ruins - a face tower, a gate, galleries and
a strangler fig - on the shore; and **lagoonNearTemple.png**, its moat, with a serpent balustrade
along it and lotus on the water. The game switches a band to its temple strip a stretch at a time,
as each comes into view from the right (`ParallaxLayer.ahead`), so each pair is drawn to meet: the
first and last [SEAM] columns of every strip hold nothing but the band's own water and haze, which
both strips of a pair draw the same.

Every strip is drawn **four times, stacked top to bottom**: night, dawn, sunrise and noon, the
keyframes `Daybreak.KEYFRAMES` names. As in the desert the shapes are drawn once, as materials, and
each keyframe is only a palette for them, so the four can never drift apart.

The light is the sun's, which the player can see: it rises on the right, so the faces turned right
are lit. At night the palette turns that round - the moon is going down on the left.

The colors are the dawn's neon - violet, magenta, peach - over turquoise water and teal jungle. Like
every band, each strip is **periodic** across its width, and the seam is checked at the end.
"""

import pathlib
import random
from math import cos, pi, sin

from PIL import Image

OUT = pathlib.Path(__file__).resolve().parent.parent / "assets"
SEED = 20261004

# Columns at each end of every strip kept free of anything but the band's own water and haze, so a
# strip and the temple strip that takes over from it meet without a seam.
SEAM = 28

TRANSPARENT = None

# --- palettes ------------------------------------------------------------------------------------
#
# One entry per material, one color per keyframe: night, dawn, sunrise, noon.

CLOUDS = {
    # the lit crown, the body, the shaded underside, and the rim the low sun lights under it
    "crown": ((62, 48, 112), (236, 148, 200), (255, 214, 186), (255, 252, 255)),
    "body": ((44, 34, 88), (176, 92, 168), (250, 164, 176), (240, 232, 250)),
    "shade": ((32, 26, 70), (118, 62, 140), (206, 112, 160), (210, 196, 236)),
    "under": ((26, 22, 60), (250, 106, 150), (255, 140, 120), (252, 200, 226)),
    "streak": ((40, 32, 80), (220, 110, 170), (255, 176, 160), (246, 222, 244)),
}

SEA = {
    "haze": ((58, 34, 96), (224, 92, 150), (255, 168, 132), (214, 240, 244)),
    "s0": ((38, 26, 78), (156, 66, 142), (234, 122, 152), (128, 222, 222)),
    "s1": ((30, 24, 70), (112, 54, 130), (178, 98, 160), (72, 204, 212)),
    "s2": ((24, 22, 62), (76, 44, 114), (116, 90, 168), (30, 186, 198)),
    "s3": ((18, 20, 54), (52, 40, 102), (66, 96, 170), (16, 164, 184)),
    "s4": ((14, 18, 46), (38, 36, 90), (42, 98, 162), (10, 144, 170)),
    "crest": ((72, 66, 132), (240, 142, 192), (255, 206, 182), (232, 252, 250)),
    "trough": ((10, 12, 34), (30, 26, 72), (36, 62, 122), (8, 112, 142)),
}

FAR = {
    "shade": ((44, 30, 82), (124, 62, 132), (176, 104, 150), (150, 182, 200)),
    "body": ((38, 26, 74), (142, 72, 142), (204, 122, 156), (176, 204, 216)),
    "lit": ((32, 22, 66), (176, 90, 152), (240, 156, 150), (206, 224, 230)),
    "leaf": ((34, 28, 70), (98, 66, 132), (120, 110, 146), (110, 172, 164)),
    "leaf_lit": ((30, 24, 62), (126, 82, 146), (170, 140, 146), (148, 200, 180)),
    "refl": ((30, 22, 66), (128, 58, 132), (196, 104, 146), (120, 196, 206)),
    # the temple's stone, warmer than the limestone, and its towers' gilded tips
    "stone": ((40, 28, 76), (150, 78, 140), (220, 136, 150), (196, 196, 204)),
    "stone_lit": ((34, 24, 68), (190, 100, 152), (252, 172, 150), (226, 220, 220)),
    "tip": ((52, 40, 92), (226, 150, 160), (255, 214, 150), (244, 226, 190)),
}

MID = {
    # limestone, from the face turned away from the sun to the face turned to it; at night the moon is
    # on the other side, so the ramp runs the other way
    "shade": ((52, 40, 96), (88, 46, 104), (134, 74, 120), (124, 132, 150)),
    "body": ((40, 30, 80), (120, 62, 124), (178, 102, 138), (172, 172, 180)),
    "lit": ((30, 24, 66), (164, 86, 146), (232, 146, 148), (222, 218, 214)),
    "streak": ((26, 20, 58), (82, 40, 98), (138, 70, 104), (150, 120, 112)),
    "rust": ((34, 22, 62), (130, 56, 108), (198, 98, 102), (196, 138, 104)),
    "notch": ((14, 10, 34), (40, 22, 66), (62, 36, 82), (60, 66, 84)),
    "leaf_dark": ((16, 20, 44), (40, 40, 92), (30, 66, 92), (16, 92, 80)),
    "leaf": ((22, 28, 58), (58, 58, 116), (42, 104, 112), (30, 136, 100)),
    "leaf_lit": ((30, 34, 70), (96, 84, 144), (118, 160, 124), (96, 192, 112)),
    "refl": ((24, 22, 62), (96, 50, 120), (150, 88, 146), (70, 168, 176)),
    "foam": ((80, 74, 138), (242, 162, 204), (255, 214, 196), (240, 252, 250)),
    # the temple's sandstone, its shadowed doorways, and the fig's pale roots
    "stone": ((44, 32, 82), (140, 70, 120), (204, 118, 128), (196, 170, 150)),
    "stone_lit": ((32, 24, 68), (184, 96, 136), (246, 162, 140), (232, 210, 182)),
    "stone_dark": ((24, 18, 52), (90, 44, 96), (140, 76, 104), (130, 108, 102)),
    "void": ((10, 8, 24), (30, 16, 46), (48, 24, 58), (38, 32, 44)),
    "root": ((56, 48, 100), (212, 158, 196), (252, 210, 196), (236, 230, 216)),
    "moss": ((22, 26, 54), (70, 64, 110), (100, 120, 110), (76, 150, 96)),
}

NEAR = {
    "w0": ((14, 18, 46), (38, 36, 90), (42, 98, 162), (10, 144, 170)),
    "w1": ((12, 16, 42), (34, 32, 84), (36, 92, 156), (8, 132, 160)),
    "w2": ((10, 14, 38), (30, 30, 78), (32, 86, 150), (6, 120, 150)),
    "w3": ((8, 12, 34), (26, 26, 70), (28, 78, 140), (6, 108, 140)),
    "crest": ((80, 74, 140), (244, 160, 206), (255, 216, 196), (236, 254, 252)),
    "trough": ((8, 10, 30), (32, 26, 76), (30, 60, 120), (6, 108, 138)),
    "rock": ((40, 32, 74), (104, 56, 112), (150, 90, 124), (126, 120, 128)),
    "rock_lit": ((30, 26, 64), (150, 82, 136), (214, 136, 140), (190, 180, 176)),
    "hull": ((26, 18, 40), (84, 38, 64), (130, 64, 66), (120, 70, 46)),
    "hull_lit": ((34, 24, 52), (128, 62, 84), (186, 104, 84), (174, 110, 64)),
    "paint": ((40, 40, 92), (120, 104, 196), (150, 160, 226), (64, 140, 214)),
    "garland_r": ((60, 24, 52), (236, 60, 110), (255, 84, 96), (232, 44, 70)),
    "garland_y": ((70, 60, 70), (250, 180, 120), (255, 222, 120), (255, 214, 64)),
    "garland_b": ((40, 40, 96), (124, 112, 220), (110, 160, 232), (40, 120, 220)),
    "canopy": ((34, 28, 66), (110, 60, 110), (170, 104, 112), (186, 70, 64)),
    # the moat's balustrade: a stone serpent on posts, and lotus on the water
    "stone": ((44, 34, 82), (146, 74, 126), (212, 126, 132), (198, 172, 150)),
    "stone_lit": ((32, 26, 70), (192, 102, 142), (250, 170, 144), (236, 214, 184)),
    "stone_dark": ((24, 20, 54), (92, 46, 98), (142, 78, 104), (132, 110, 102)),
    "pad": ((20, 30, 46), (70, 70, 110), (88, 110, 110), (44, 150, 92)),
    "pad_lit": ((26, 36, 56), (104, 96, 130), (124, 150, 118), (92, 190, 110)),
    "lotus": ((84, 60, 110), (252, 130, 186), (255, 160, 190), (255, 132, 186)),
    "lotus_lit": ((110, 86, 140), (255, 196, 226), (255, 220, 226), (255, 206, 230)),
}

MOON = {
    "m0": (186, 170, 220, 255),
    "m1": (222, 210, 244, 255),
    "m2": (248, 242, 255, 255),
    "c": (168, 152, 206, 255),
    "e": (120, 100, 168, 255),
}


def keyframe_count(palette):
    counts = {len(ramp) for ramp in palette.values()}
    assert len(counts) == 1, "every material needs a color for every keyframe"
    return counts.pop()


# --- shared shapes -------------------------------------------------------------------------------


def wrapped_noise(width, control_points, rng):
    """Smooth noise exactly periodic over [width]; see the cave generator for why it must be."""
    points = [rng.random() for _ in range(control_points)]

    def at(x):
        t = x / width * control_points
        index = int(t) % control_points
        frac = t - int(t)
        a = points[index]
        b = points[(index + 1) % control_points]
        frac = frac * frac * (3.0 - 2.0 * frac)
        return a + (b - a) * frac

    return at


def blank(width, height, fill=TRANSPARENT):
    return [[fill] * width for _ in range(height)]


def put(grid, x, y, material):
    if 0 <= y < len(grid) and 0 <= x < len(grid[0]):
        grid[y][x] = material


def spread(width, count, rng, least=SEAM + 8):
    """
    [count] centers spread along a strip on a jittered grid, kept [least] columns clear of either end
    so nothing reaches into the seam.
    """
    span = width - 2 * least
    step = span / count
    return [least + step * (i + 0.5) + rng.uniform(-0.3, 0.3) * step for i in range(count)]


# --- limestone -----------------------------------------------------------------------------------


def tower_top(u, height, bumps):
    """
    The height of a limestone tower across its width, [u] from -1 at its left foot to 1 at its right:
    sheer walls with rounded shoulders, and a crown that is never level - a few knolls of rock under
    the jungle - which is the shape the sea and the rain leave standing in the bay.
    """
    wall = max(0.0, 1.0 - abs(u) ** 5) ** 0.22
    knolls = sum(a * 2.718 ** (-((u - c) / w) ** 2) for c, w, a in bumps)
    return height * wall * (0.8 + knolls)


def tower(grid, cx, base, height, half_width, rng, *, jungle=0.26, reflect=0, far=False):
    """
    One limestone tower standing in the water at [base], [cx] across: sheer walls streaked dark where
    the rain runs down them and stained with rust under the jungle, jungle draped over its crown and
    trailing down its walls, the sea's notch cut into its foot, and its reflection under it.

    Lit from the right, by the sun: the right third of it "lit", the left quarter in "shade". In the
    far band, haze has taken the detail - no streaks, no stains, no notch.
    """
    width = len(grid[0])
    noise = wrapped_noise(width, max(6, int(half_width * 1.5)), rng)
    bumps = [(rng.uniform(-0.6, 0.6), rng.uniform(0.2, 0.45), rng.uniform(0.04, 0.2)) for _ in range(3)]
    # Where the rain has run down the rock, and where rust has stained it under the jungle.
    streaks = {}
    for _ in range(int(half_width / 2)):
        streaks[int(cx + rng.uniform(-half_width * 0.9, half_width * 0.9))] = (rng.uniform(0.0, 0.5), rng.uniform(0.3, 0.9))
    stains = {int(cx + rng.uniform(-half_width * 0.6, half_width * 0.8)): rng.uniform(8, 26) for _ in range(int(half_width / 5) + 1)}
    tops = {}
    for x in range(int(cx - half_width) - 1, int(cx + half_width) + 2):
        u = (x - cx) / half_width
        if abs(u) > 1.0:
            continue
        top = base - tower_top(u, height, bumps) - 2.0 * (noise(x % width) - 0.5)
        tops[x] = top
        # The jungle on the crown, trailing down the walls in places as vines.
        cap = height * jungle * (0.6 + 0.8 * noise((x * 7) % width))
        # Its lower edge scalloped into clumps of leaves.
        cap += 2.5 * abs(sin(x * 0.45))
        if not far and noise((x * 13) % width) > 0.78:
            cap += height * 0.25 * noise((x * 5) % width)
        stone_top = top + cap
        for y in range(max(0, int(top)), min(len(grid), base)):
            depth = y - top
            if depth < cap:
                lit = u > 0.2 or depth < 1.5
                material = "leaf_lit" if lit and u > -0.55 else "leaf"
                if not far and depth > cap - 1.5:
                    material = "leaf_dark"
            else:
                material = "lit" if u > 0.34 else ("shade" if u < -0.5 else "body")
                if not far:
                    run = streaks.get(x)
                    below = (y - stone_top) / max(1.0, base - stone_top)
                    if run is not None and run[0] <= below <= run[1]:
                        material = "streak"
                    elif x in stains and y - stone_top < stains[x]:
                        material = "rust"
                    if y >= base - 2:
                        material = "notch"
            put(grid, x, y, material)
        # Trees standing up off the crown.
        if not far and rng.random() < 0.22:
            put(grid, x, int(top) - 1, "leaf_lit" if u > 0 else "leaf")
    # The reflection: the tower turned upside down in the water, broken into lines by the waves.
    for x, top in tops.items():
        tall = min(reflect, int((base - top) * 0.45))
        for r in range(tall):
            if shimmer_gap(x, r, tall):
                continue
            put(grid, x, base + r, "refl")
    return tops


def shimmer_gap(x, row, tall):
    """
    Whether a reflection leaves a gap at [x], [row] rows under the shore: none in its first two rows,
    then every other row, and further down those rows broken into dashes - the water moving the
    picture about the further it is from what it shows.
    """
    if row < 2:
        return False
    if row % 2 == 1:
        return True
    return row > tall * 0.5 and (x // 4 + row // 2) % 3 == 0


# --- the temple ----------------------------------------------------------------------------------


def prang(grid, cx, base, height, half_width, *, tiers=None):
    """
    A temple tower in the shape of a lotus bud: swelling out of its base, tapering in tiers to a
    finial - the five on the horizon are the temple's, seen from across its moat. Lit from the right.
    """
    tier = tiers or 3
    for y in range(base - height, base):
        t = (base - y) / height  # 0 at the foot, 1 at the tip
        # Swelling out of its base to the bud's widest a third of the way up, and drawing in to a point.
        bud = (1.0 + 0.5 * sin(pi * t * 0.9)) * (1.0 - t) ** 0.6
        hw = half_width * bud
        # Each corbelled tier steps the outline in a pixel at its top, which is what makes the bud read
        # as built rather than grown.
        stepped = (base - y) % tier == 0
        if stepped:
            hw -= 1.0
        if hw < 0.5:
            put(grid, int(cx), y, "tip")
            continue
        for x in range(int(round(cx - hw)), int(round(cx + hw)) + 1):
            u = (x - cx) / max(hw, 1.0)
            material = "stone_lit" if u > 0.15 else "stone"
            if stepped:
                material = "stone"
            put(grid, x, y, material)
    for y in range(base - height - 4, base - height):
        put(grid, int(cx), y, "tip")


def far_strip(rng):
    """
    Limestone towers far off on the horizon, in clusters, pale with the distance. Tall enough that
    the tallest stand well up into the sky, which is what makes the bay read as a bay of islands.
    """
    width, height = 960, 72
    grid = blank(width, height)
    horizon = 68
    for cx in spread(width, 8, rng):
        cluster = rng.randint(2, 4)
        for k in range(cluster):
            x = cx + (k - (cluster - 1) / 2) * rng.uniform(9, 16)
            tower(grid, x, horizon, rng.uniform(18, 58), rng.uniform(5, 11), rng,
                  jungle=0.34, reflect=4, far=True)
    return grid, width, height


def far_temple_strip(rng):
    """
    The temple on the horizon over its moat: five lotus-bud towers - the middle one the tallest - over
    tiers of galleries and the long wall round them, with sugar palms standing about it and a lesser
    temple further round. Drawn to meet the far islands' strip: nothing in the seams but the horizon.
    """
    width, height = 960, 72
    grid = blank(width, height)
    horizon = 68

    def gallery(left, right, top, roof=True):
        for x in range(int(left), int(right) + 1):
            for y in range(int(top), horizon):
                lit = x > (left + right) / 2 + (right - left) * 0.25
                put(grid, x, y, "stone_lit" if lit else "stone")
            if roof:
                put(grid, x, int(top) - 1, "stone_lit" if x % 6 < 4 else "stone")
        # A row of dark windows along it.
        for x in range(int(left) + 3, int(right) - 2, 5):
            put(grid, x, int(top) + 2, "shade")

    def palm(x, top):
        for y in range(top + 4, horizon):
            put(grid, x, y, "leaf")
        for dx, dy in ((-3, 1), (-2, 0), (-1, -1), (0, -1), (1, -1), (2, 0), (3, 1), (-2, 2), (2, 2),
                       (-1, 0), (0, 0), (1, 0), (0, 1), (-1, 1), (1, 1), (-4, 2), (4, 2)):
            put(grid, x + dx, top + 2 + dy, "leaf_lit" if dx > 0 else "leaf")

    center = 430
    # The outer wall and the galleries, stepping up toward the middle.
    gallery(center - 150, center + 150, horizon - 6, roof=False)
    gallery(center - 96, center + 96, horizon - 12)
    gallery(center - 60, center + 60, horizon - 19)
    gallery(center - 34, center + 34, horizon - 26)
    # The towers: the corners, then the middle one over them all.
    for dx, tall, hw in ((-56, 30, 7), (56, 30, 7), (-30, 40, 8), (30, 40, 8)):
        prang(grid, center + dx, horizon - 19 if abs(dx) > 40 else horizon - 26, tall, hw)
    prang(grid, center, horizon - 26, 40, 10)
    # The causeway out across the moat, and the gate at its end.
    gallery(center - 190, center - 150, horizon - 4, roof=False)
    prang(grid, center - 170, horizon - 4, 14, 5)
    # Sugar palms about it, and the jungle low along the shore.
    for x in (center - 128, center - 112, center + 118, center + 134, center + 168, 640, 662, 230, 250):
        palm(x, horizon - rng.randint(26, 36))
    noise = wrapped_noise(width, 30, rng)
    for x in range(SEAM + 6, width - SEAM - 6):
        if abs(x - center) < 150:
            continue
        tall = int(3 + 6 * noise(x))
        for y in range(horizon - tall, horizon):
            if grid[y][x] is None:
                put(grid, x, y, "leaf_lit" if y < horizon - tall + 2 else "leaf")
    # A lesser temple further round: one tower over a terrace.
    gallery(760, 812, horizon - 9)
    prang(grid, 786, horizon - 9, 24, 7)
    # Their reflections in the moat.
    for x in range(width):
        column = [y for y in range(horizon) if grid[y][x] is not None]
        if not column:
            continue
        tall = min(4, (horizon - min(column)) // 3)
        for r in range(tall):
            if not shimmer_gap(x, r, tall):
                put(grid, x, horizon + r, "refl")
    return grid, width, height


# --- the middle band -----------------------------------------------------------------------------


def mid_strip(rng):
    """
    The nearer islands: great towers of limestone standing up out of the sea, one of them the bay's
    famous needle - narrow at its foot and wider above, where the sea has eaten its base away - with
    smaller stacks between, and every one reflected in the water under it.
    """
    width, height = 960, 120
    grid = blank(width, height)
    base = 104
    for cx in spread(width, 5, rng):
        # An island is a cluster of towers grown together: the tallest first, the lesser ones in front
        # of it, so a cluster reads as one rock with several summits.
        towers = sorted(
            ((cx + rng.uniform(-30, 30), rng.uniform(46, 98), rng.uniform(12, 24)) for _ in range(rng.randint(2, 3))),
            key=lambda t: -t[1],
        )
        for x, tall, half in towers:
            x = min(max(x, SEAM + half + 2), width - SEAM - half - 2)
            tower(grid, x, base, tall, half, rng, jungle=0.24, reflect=14)
        # A lesser stack off on its own, kept out of the seams.
        if rng.random() < 0.8:
            half = rng.uniform(5, 9)
            x = cx + rng.choice((-1, 1)) * rng.uniform(52, 70)
            x = min(max(x, SEAM + half + 2), width - SEAM - half - 2)
            tower(grid, x, base, rng.uniform(18, 40), half, rng, jungle=0.34, reflect=8)
    needle(grid, 476, base, rng)
    foam_lines(grid, base, rng)
    return grid, width, height


def needle(grid, cx, base, rng):
    """
    A pillar of limestone standing alone in the water, narrow at its foot and swelling above it, with
    jungle on its top: the bay's own landmark.
    """
    height = 74
    for y in range(base - height, base):
        t = (base - y) / height
        hw = 4.0 + 9.0 * t ** 0.8 - 3.0 * max(0.0, t - 0.85) / 0.15 * t
        crown = t > 0.8
        if crown:
            # Rounded over the top, a cap of jungle rather than a lid.
            hw *= max(0.0, 1.0 - ((t - 0.8) / 0.2) ** 2) ** 0.5
        for x in range(int(cx - hw), int(cx + hw) + 1):
            u = (x - cx) / max(hw, 1.0)
            if crown:
                material = "leaf_lit" if u > 0 or t > 0.95 else "leaf"
            else:
                material = "lit" if u > 0.3 else ("shade" if u < -0.45 else "body")
                if (x * 7 + int(t * 40)) % 11 == 0:
                    material = "streak"
            put(grid, x, y, material)
    for y in range(base - 2, base):
        for x in range(int(cx - 4), int(cx + 5)):
            put(grid, x, y, "notch")
    for r in range(12):
        if shimmer_gap(int(cx), r, 12):
            continue
        hw = 4.0 + 9.0 * (r / 74) ** 0.8
        for x in range(int(cx - hw), int(cx + hw) + 1):
            put(grid, x, base + r, "refl")


def foam_lines(grid, base, rng):
    """Where the sea breaks against the feet of the islands, a broken line of foam."""
    width = len(grid[0])
    for x in range(SEAM, width - SEAM):
        if grid[base - 1][x] is not None and rng.random() < 0.6:
            put(grid, x, base, "foam")
            if rng.random() < 0.3:
                put(grid, x + 1, base + 1, "foam")


def mid_temple_strip(rng):
    """
    The temple's ruins on the shore: towers carved on every side with a calm, smiling face; a gate
    with a dark doorway under its tiers; a gallery fallen in, its windows full of stone balusters; and
    a strangler fig with its roots poured over a wall - all of it standing out of the jungle, and
    reflected in the water in front of it.
    """
    width, height = 960, 120
    grid = blank(width, height)
    base = 104

    def disc(cx, cy, rx, ry, material, only_empty=False):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
                if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                    if not only_empty or (0 <= y < height and 0 <= x < width and grid[y][x] is None):
                        put(grid, x, y, material)

    def crowns(left, right, low, high):
        """A canopy of tree crowns between [left] and [right], each lit on its upper right."""
        x = left
        while x < right:
            r = rng.uniform(8, 15)
            cy = base - rng.uniform(low, high)
            for px in range(int(x - r), int(x + r) + 1):
                for py in range(int(cy - r * 0.8), base):
                    dx, dy = (px - x) / r, (py - cy) / (r * 0.8)
                    if dx * dx + dy * dy > 1.0 and py < cy:
                        continue
                    if abs(dx) > 1.0 or px < left - 4 or px > right + 4:
                        continue
                    lit = dx * 0.6 - dy > 0.45 and py < cy
                    put(grid, px, py, "leaf_lit" if lit else ("leaf_dark" if py > cy + r * 0.5 else "leaf"))
            x += r * rng.uniform(0.9, 1.4)

    def block(left, top, right, bottom):
        """Laid sandstone, lit on its right, in courses of blocks."""
        split = left + (right - left) * 0.62
        for x in range(int(left), int(right)):
            for y in range(int(top), int(bottom)):
                put(grid, x, y, "stone_lit" if x >= split else "stone")
        for y in range(int(top) + 3, int(bottom), 4):
            for x in range(int(left), int(right)):
                if (x + (y // 4) * 3) % 7 == 0:
                    put(grid, x, y, "stone_dark")
        for x in range(int(left), int(right)):
            put(grid, x, int(bottom) - 1, "stone_dark")

    def moss(cx, cy, size):
        """A patch of moss, lying along a ledge."""
        disc(cx, cy, size, max(1.0, size * 0.4), "moss")

    def face_tower(cx, top, scale):
        """
        A tower of stacked tiers with a great face carved on its front, eyes lowered and smiling -
        the shape the temple's towers are known for.
        """
        hw = 20 * scale
        face_top = top + 22 * scale
        face_bottom = face_top + 26 * scale
        block(cx - hw, face_bottom, cx + hw, base)
        block(cx - hw * 0.82, face_top, cx + hw * 0.82, face_bottom)
        # The tiers above the face, stepping in to a lotus bud.
        for tier in range(3):
            w = hw * (0.66 - tier * 0.16)
            bottom = face_top - tier * 7 * scale
            block(cx - w, bottom - 7 * scale, cx + w, bottom)
        finial_top = top - 3 * scale
        finial_bottom = face_top - 21 * scale
        for y in range(int(finial_top), int(finial_bottom)):
            t = (finial_bottom - y) / (finial_bottom - finial_top)
            w = 3.5 * scale * max(0.0, 1.0 - t) ** 0.6
            for x in range(int(round(cx - w)), int(round(cx + w)) + 1):
                put(grid, x, y, "stone_lit" if x >= cx else "stone")
        # The face: a brow, lowered eyes, a broad nose and full, smiling lips.
        f = face_top
        s = scale
        for x in range(int(cx - 10 * s), int(cx + 10 * s) + 1):
            put(grid, x, int(f + 4 * s), "stone_dark")
        for side in (-1, 1):
            # The eyes, lowered: a lid's line, curving down at its ends.
            for dx in range(int(3 * s), int(8 * s) + 1):
                put(grid, int(cx + side * dx), int(f + 8 * s), "stone_dark")
            for dx in (int(3 * s), int(8 * s)):
                put(grid, int(cx + side * dx), int(f + 9 * s), "stone_dark")
        for y in range(int(f + 6 * s), int(f + 15 * s)):
            put(grid, int(cx + 2 * s), y, "stone_dark")
        for dx in range(int(-3 * s), int(3 * s) + 1):
            put(grid, int(cx + dx), int(f + 15 * s), "stone_dark")
        for dx in range(int(-7 * s), int(7 * s) + 1):
            lift = 1 if abs(dx) > 5 * s else 0
            put(grid, int(cx + dx), int(f + 19 * s) - lift, "stone_dark")
            if abs(dx) < 5 * s:
                put(grid, int(cx + dx), int(f + 21 * s), "stone_dark")
        # Ears, long-lobed, either side.
        for side in (-1, 1):
            for y in range(int(f + 6 * s), int(f + 18 * s)):
                put(grid, int(cx + side * 16 * s), y, "stone_dark")
        moss(cx - hw * 0.5, face_bottom, 4 * scale)
        moss(cx + hw * 0.3, face_top - 7 * scale, 3 * scale)

    def gate(cx, top):
        """A gate tower with a dark doorway under corbelled tiers."""
        block(cx - 22, top + 24, cx + 22, base)
        for tier in range(3):
            w = 19 - tier * 5
            block(cx - w, top + 24 - (tier + 1) * 8, cx + w, top + 24 - tier * 8)
        for y in range(top - 6, top):
            put(grid, cx, y, "stone_lit")
            put(grid, cx - 1, y, "stone")
        # The doorway, its arch stepped in the corbelled way.
        for y in range(top + 36, base):
            w = 7 if y > top + 44 else 7 - (top + 44 - y) // 2
            for x in range(cx - w, cx + w + 1):
                put(grid, x, y, "void")
        moss(cx - 10, top + 24, 5)

    def gallery(left, right, top):
        """A gallery fallen in at one end, its windows filled with balusters."""
        block(left, top, right, base)
        for wx in range(left + 6, right - 10, 15):
            for y in range(top + 8, top + 20):
                for x in range(wx, wx + 8):
                    put(grid, x, y, "void" if (x - wx) % 2 == 1 else "stone_lit")
        # Its roof, a corbelled vault along the top, broken off toward the fallen end.
        for x in range(left, right - 14):
            for y in range(top - 5, top):
                if abs(x - (left + right) / 2) < (right - left) / 2 - (top - y) * 2:
                    put(grid, x, y, "stone_lit" if y < top - 2 else "stone")
        # The fallen end: blocks tumbled down.
        for _ in range(12):
            bx = right + rng.randint(-6, 14)
            by = base - rng.randint(2, 12)
            for x in range(bx, bx + rng.randint(3, 6)):
                for y in range(by, by + 3):
                    put(grid, x, y, "stone_dark" if (x + y) % 3 == 0 else "stone")
        moss(left + 20, top - 4, 5)

    def fig(cx, top):
        """A strangler fig on a wall: a heavy crown, and roots poured down over the stones."""
        block(cx - 24, top + 34, cx + 24, base)
        for _ in range(9):
            x = cx + rng.uniform(-20, 20)
            y = top + 26
            drift = rng.uniform(-0.6, 0.6)
            while y < base:
                put(grid, int(x), int(y), "root")
                put(grid, int(x) + 1, int(y), "root")
                y += 1
                x += drift + 0.5 * sin(y * 0.3)
        for y in range(top + 14, top + 36):
            for x in range(cx - 2, cx + 3):
                put(grid, x, y, "root")
        for dx, dy, r in ((-18, 12, 12), (0, 6, 15), (17, 12, 12), (-8, 0, 10), (10, 2, 10)):
            disc(cx + dx, top + dy, r, r * 0.75, "leaf")
        for dx, dy, r in ((-14, 8, 8), (4, 1, 10), (20, 8, 7)):
            disc(cx + dx + 2, top + dy - 2, r * 0.7, r * 0.5, "leaf_lit")

    crowns(SEAM + 8, width - SEAM - 8, 18, 40)
    face_tower(170, 16, 1.0)
    gate(380, 34)
    gallery(470, 590, 58)
    fig(712, 20)
    face_tower(850, 40, 0.75)

    # Everything in front is reflected in the water.
    for x in range(SEAM, width - SEAM):
        column = [y for y in range(base) if grid[y][x] is not None]
        if not column:
            continue
        tall = min(14, (base - min(column)) // 4)
        for r in range(tall):
            if shimmer_gap(x, r, tall):
                continue
            put(grid, x, base + r, "refl")
    foam_lines(grid, base, rng)
    return grid, width, height


# --- the sea and the near water ------------------------------------------------------------------


def sea_strip(rng):
    """
    The open sea from the horizon to the bottom of the frame: bands deepening toward the viewer, a line
    of haze along the horizon, and waves - short and close together far off, long and far apart near -
    a light crest over a dark trough each, scattered so no two rows repeat.
    """
    width, height = 960, 128
    grid = blank(width, height)
    edges = ((2, "haze"), (8, "s0"), (20, "s1"), (40, "s2"), (70, "s3"), (height, "s4"))
    for y in range(height):
        index = next(i for i, (edge, _) in enumerate(edges) if y < edge)
        material = edges[index][1]
        # The row above a band's edge is half the next band's, every other pixel.
        dither = index + 1 < len(edges) and y == edges[index][0] - 1
        for x in range(width):
            grid[y][x] = edges[index + 1][1] if dither and (x + y) % 2 == 0 else material
    y = 4
    while y < height - 1:
        depth = y / height
        length = int(2 + depth * 18)
        count = int(width / (6 + depth * 34))
        for _ in range(count):
            x = rng.randrange(width)
            for dx in range(length):
                grid[y][(x + dx) % width] = "crest"
            for dx in range(1, length + 2):
                grid[y + 1][(x + dx + 1) % width] = "trough"
        y += 2 + int(depth * 7)
    return grid, width, height


def near_water(width, height, rng):
    """
    The water nearest the viewer: shallows deepening downward, with long waves, white-capped. Drawn
    from its own dice, so a strip and the temple strip after it lay down exactly the same water.
    """
    grid = blank(width, height)
    edges = ((10, "w0"), (24, "w1"), (40, "w2"), (height, "w3"))
    for y in range(height):
        index = next(i for i, (edge, _) in enumerate(edges) if y < edge)
        material = edges[index][1]
        dither = index + 1 < len(edges) and y == edges[index][0] - 1
        for x in range(width):
            grid[y][x] = edges[index + 1][1] if dither and (x + y) % 2 == 0 else material
    y = 2
    while y < height - 2:
        depth = y / height
        length = int(8 + depth * 26)
        for _ in range(int(width / (26 + depth * 50))):
            x = rng.randrange(width)
            for dx in range(length):
                wave = int(1.4 * sin(dx / length * pi))
                grid[y - wave][(x + dx) % width] = "crest"
                grid[y + 1 - wave][(x + dx + 2) % width] = "trough"
        y += 4 + int(depth * 6)
    return grid


def rock(grid, cx, top, rng):
    """A boulder of limestone breaking the surface, foam round its foot."""
    hw = rng.uniform(5, 11)
    tall = rng.uniform(4, 9)
    for x in range(int(cx - hw), int(cx + hw) + 1):
        u = (x - cx) / hw
        h = tall * max(0.0, 1 - u * u) ** 0.6
        for y in range(int(top - h), top + 2):
            put(grid, x, y, "rock_lit" if u > 0.2 else "rock")
        put(grid, x, top + 2, "crest")
        if abs(u) > 0.7:
            put(grid, x + (2 if u > 0 else -2), top + 1, "crest")


def longtail(grid, x0, waterline, facing):
    """
    A longtail boat at its mooring: a long low hull with its prow swept up high and garlands of
    ribbons tied round it, a canopy over the middle, and the long-shafted motor tipped up at the back.
    """
    length = 46
    for i in range(length):
        t = i / (length - 1)
        x = x0 + (i if facing > 0 else length - 1 - i)
        # The hull's sheer: low through the middle, sweeping up to the prow at the front.
        rise = int(9 * (t - 0.78) / 0.22) if t > 0.78 else 0
        deck = waterline - 4 - rise
        keel = waterline + (1 if 0.1 < t < 0.85 else 0)
        for y in range(deck, keel + 1):
            material = "hull_lit" if y < deck + 2 else "hull"
            if y == deck + 2 and t < 0.8:
                material = "paint"
            put(grid, x, y, material)
    # The garlands round the prow, in the colors they come in.
    prow_x = x0 + (length - 2 if facing > 0 else 1)
    for k, material in enumerate(("garland_r", "garland_y", "garland_b", "garland_r", "garland_y")):
        put(grid, prow_x - facing * (k % 2), waterline - 12 + k, material)
        put(grid, prow_x - facing * (1 + k % 2), waterline - 11 + k, material)
    # The canopy, on two posts.
    mid = x0 + length // 2
    for x in range(mid - 9, mid + 8):
        put(grid, x, waterline - 11, "canopy")
        put(grid, x, waterline - 10, "canopy" if x % 3 else "hull")
    for px in (mid - 8, mid + 6):
        for y in range(waterline - 9, waterline - 4):
            put(grid, px, y, "hull")
    # The motor's long shaft, tipped up out of the water at the stern.
    stern = x0 + (0 if facing > 0 else length - 1)
    for k in range(9):
        put(grid, stern - facing * k, waterline - 5 - k // 3, "hull")


def near_strip(rng, water_rng):
    """The water the obstacles stand in, with a few rocks breaking it and boats moored in it."""
    width, height = 1440, 58
    grid = near_water(width, height, water_rng)
    for cx in spread(width, 7, rng):
        rock(grid, cx, rng.randint(12, 30), rng)
    longtail(grid, 300, 34, 1)
    longtail(grid, 980, 40, -1)
    return grid, width, height


def near_temple_strip(rng, water_rng):
    """
    The temple's moat: lotus on the water, and along the bottom the balustrade of a causeway - a
    stone serpent's body laid along low posts, rearing up at each end into a fan of seven heads.
    """
    width, height = 1440, 58
    grid = near_water(width, height, water_rng)

    def lotus(cx, cy, flower):
        for dx in range(-5, 6):
            for dy in range(-2, 3):
                if (dx / 5.5) ** 2 + (dy / 2.4) ** 2 <= 1 and not (dx > 1 and dy == 0):
                    put(grid, cx + dx, cy + dy, "pad_lit" if dy < 0 else "pad")
        if flower:
            for dx, dy, m in ((0, -3, "lotus_lit"), (-1, -2, "lotus"), (1, -2, "lotus_lit"), (-2, -1, "lotus"),
                              (2, -1, "lotus_lit"), (0, -2, "lotus_lit"), (0, -4, "lotus_lit")):
                put(grid, cx + dx, cy + dy, m)

    for _ in range(22):
        lotus(rng.randint(SEAM + 8, width - SEAM - 8), rng.randint(6, 30), rng.random() < 0.45)

    rail_top = height - 14

    def fan(cx, facing):
        """The serpent rearing up at the balustrade's end: a thick neck, and a hood of seven heads."""
        hood_y = rail_top - 22
        for y in range(hood_y, height):
            for x in range(cx - 4, cx + 5):
                put(grid, x, y, "stone_lit" if x > cx + 1 else "stone")
        # The hood: a fan behind the heads, filled, its rim lit on the right.
        for x in range(cx - 15, cx + 16):
            for y in range(hood_y - 13, hood_y + 3):
                if ((x - cx) / 15.5) ** 2 + ((y - hood_y) / 13.0) ** 2 <= 1.0:
                    put(grid, x, y, "stone_lit" if x > cx + 4 else "stone")
        # Seven heads round its rim, each with an eye toward the water.
        for k in range(7):
            angle = pi * (0.06 + 0.88 * k / 6)
            hx = cx + cos(angle) * 14
            hy = hood_y - sin(angle) * 12
            for dx in range(-3, 4):
                for dy in range(-3, 3):
                    if dx * dx + dy * dy <= 7:
                        put(grid, int(hx + dx), int(hy + dy), "stone_lit" if dx > 0 else "stone")
            put(grid, int(hx) - 1, int(hy), "stone_dark")
        # The scales down its neck.
        for y in range(hood_y + 4, rail_top, 3):
            put(grid, cx, y, "stone_dark")

    left, right = SEAM + 30, width - SEAM - 30
    for x in range(left, right):
        sag = int(1.5 * sin((x - left) / 24.0))
        for y in range(rail_top + sag, rail_top + sag + 7):
            put(grid, x, y, "stone_lit" if y <= rail_top + sag + 1 else ("stone_dark" if y == rail_top + sag + 6 else "stone"))
        # The scales along its back.
        if (x - left) % 4 == 0:
            put(grid, x, rail_top + sag + 2, "stone_dark")
        # The posts it is laid along.
        if (x - left) % 36 < 6:
            for y in range(rail_top + sag + 7, height):
                put(grid, x, y, "stone_lit" if (x - left) % 36 >= 4 else "stone")
    fan(left, -1)
    fan(right, 1)
    return grid, width, height


# --- the clouds ----------------------------------------------------------------------------------


def clouds_strip(rng):
    """
    Heaped clouds with flat bottoms, and a few long streaks: lit on their crowns from the right, where
    the sun comes up, shaded underneath, and rimmed along the bottom in the color the low sun throws up
    at them.
    """
    width, height = 1280, 120
    grid = blank(width, height)
    for cx in spread(width, 8, rng):
        bottom = rng.randint(50, 112)
        span = rng.uniform(50, 150)
        puffs = []
        for _ in range(int(span / 9)):
            px = cx + rng.uniform(-span / 2, span / 2)
            r = rng.uniform(8, 19) * (1.0 - abs(px - cx) / span)
            puffs.append((px, bottom - r * 0.6, max(4.0, r)))
        top = min(py - r for _, py, r in puffs)
        for x in range(int(cx - span / 2 - 16), int(cx + span / 2 + 16)):
            for y in range(max(0, int(top) - 1), bottom + 1):
                inside = [(px, py, r) for px, py, r in puffs if (x - px) ** 2 + (y - py) ** 2 <= r * r]
                if not inside:
                    continue
                px, py, r = max(inside, key=lambda p: p[2] - ((x - p[0]) ** 2 + (y - p[1]) ** 2) ** 0.5)
                lit = (x - px) * 0.7 - (y - py) > r * 0.35
                if y >= bottom - 1:
                    material = "under"
                elif y > bottom - (bottom - top) * 0.32:
                    material = "shade"
                else:
                    material = "crown" if lit else "body"
                put(grid, x % width, y, material)
    # Long thin streaks, tapering at both ends: two rows thick through the middle.
    for _ in range(7):
        y = rng.randint(14, 100)
        x = rng.randint(SEAM + 30, width - SEAM - 160)
        length = rng.randint(60, 140)
        for dx in range(length):
            put(grid, x + dx, y, "streak")
            if length * 0.2 < dx < length * 0.75:
                put(grid, x + dx + 4, y + 1, "streak")
    return grid, width, height


def moon():
    """
    The moon a night short of full, lit from the right where the sun will come up, with a few seas on
    its face. Big and pale, low over the sea and going down.
    """
    size = 32
    grid = [["."] * size for _ in range(size)]
    cx = cy = 15.5
    radius = 14.5
    for y in range(size):
        for x in range(size):
            px, py = x + 0.5, y + 0.5
            d = ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5
            if d > radius:
                continue
            # The terminator, a sliver of shadow down its left edge.
            if (px - cx + 23.0) ** 2 + (py - cy) ** 2 > (radius + 8.5) ** 2:
                grid[y][x] = "e"
                continue
            light = (px - cx) * 0.5 - (py - cy) * 0.3
            grid[y][x] = "m2" if light > 3.5 else ("m1" if light > -3 else "m0")
    for sx, sy, r in ((12, 11, 3.2), (19, 18, 2.6), (10, 20, 2.2), (21, 9, 1.8)):
        for y in range(size):
            for x in range(size):
                if grid[y][x] in ("m0", "m1", "m2") and (x - sx) ** 2 + (y - sy) ** 2 <= r * r:
                    grid[y][x] = "c"
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            if grid[y][x] != ".":
                image.putpixel((x, y), MOON[grid[y][x]])
    return image


# --- output --------------------------------------------------------------------------------------


def save_keyframes(path, grid, width, height, palette):
    frames = keyframe_count(palette)
    image = Image.new("RGBA", (width, height * frames), (0, 0, 0, 0))
    pixels = image.load()
    for frame in range(frames):
        for y in range(height):
            row = grid[y]
            for x in range(width):
                material = row[x]
                if material is not None:
                    pixels[x, frame * height + y] = palette[material][frame] + (255,)
    image.save(path)
    return image


def column_step(image, left, right, height):
    total = 0
    for y in range(height):
        a = image.getpixel((left, y))
        b = image.getpixel((right, y))
        total += sum(abs(p - q) for p, q in zip(a, b))
    return total / height


def check_seam(image, width, height):
    seam = column_step(image, width - 1, 0, height)
    interior = sum(column_step(image, x, x + 1, height) for x in range(0, width - 1, 7)) / len(range(0, width - 1, 7))
    return seam, interior


def main() -> int:
    rng = random.Random(SEED)
    strips = []
    strips.append(("lagoonClouds.png", *clouds_strip(rng), CLOUDS))
    strips.append(("lagoonSea.png", *sea_strip(rng), SEA))
    strips.append(("lagoonFar.png", *far_strip(rng), FAR))
    strips.append(("lagoonFarTemple.png", *far_temple_strip(rng), FAR))
    strips.append(("lagoonMid.png", *mid_strip(rng), MID))
    strips.append(("lagoonMidTemple.png", *mid_temple_strip(rng), MID))
    # The near water of both strips from one set of dice, so the two lay down the same water and meet.
    strips.append(("lagoonNear.png", *near_strip(rng, random.Random(SEED + 7)), NEAR))
    strips.append(("lagoonNearTemple.png", *near_temple_strip(rng, random.Random(SEED + 7)), NEAR))

    for name, grid, width, height, palette in strips:
        image = save_keyframes(OUT / name, grid, width, height, palette)
        seam, interior = check_seam(image, width, height)
        print(f"{OUT / name} ({image.width}x{image.height}, {keyframe_count(palette)} keyframes of {height}); "
              f"seam {seam:.1f} vs typical step {interior:.1f}")

    # A strip and the temple strip after it meet column for column.
    for base, ahead in (("lagoonFar.png", "lagoonFarTemple.png"), ("lagoonMid.png", "lagoonMidTemple.png"),
                        ("lagoonNear.png", "lagoonNearTemple.png")):
        a = Image.open(OUT / base)
        b = Image.open(OUT / ahead)
        for x in (0, a.width - 1):
            assert all(a.getpixel((x, y)) == b.getpixel((x, y)) for y in range(a.height)), f"{base} and {ahead} differ at column {x}"

    image = moon()
    image.save(OUT / "lagoonMoon.png")
    print(f"{OUT / 'lagoonMoon.png'} ({image.width}x{image.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
