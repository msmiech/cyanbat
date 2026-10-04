# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the ground the desert stage scrolls past, and the moon that rises over it.

    uv run tools/generate_desert_background.py

The jungle and the cave are one strip each. The desert is three, at three depths, because its sky
is drawn by the game rather than painted - it turns from noon to night across the stage - and what
stands against a sky that big needs depth to be a landscape rather than a stage flat:

* **desertFar.png** - the far dunes, low on the horizon, with the pyramids on them. Barely moves.
* **desertMid.png** - rolling dunes, and a small oasis of palms. Moves at half the scenery's pace.
* **desertNear.png** - the sand the obstacles stand on, rippled by the wind. Moves with them.

Every strip is drawn **four times, stacked top to bottom**: noon, the golden hour, sunset and night,
the keyframes `Daylight.KEYFRAMES` names. The game crossfades between neighbors as the stage clock
runs, so the hours in between are blends of two pictures that were each picked by eye. The shapes
are drawn once, as materials, and each keyframe is only a palette for them - so the four can never
drift apart, and a crossfade never shows two different dunes.

The light is the sun's, which the player can see: it stands to the right and sets there, so the
faces turned right are lit and the ones turned left are in shadow. At night the palette turns that
round - the moon rises on the left - which is a palette swap and nothing more: the materials say
which way a face is turned, and the night says which way is lit.

As with the other strips, each is **periodic** across its width, so it tiles as it scrolls: every
dune, pyramid and palm wraps its column index, and the seam is checked at the end.
"""

import pathlib
import random
from math import cos, pi, sin

from PIL import Image

HEIGHT = 360
OUT = pathlib.Path(__file__).resolve().parent.parent / "assets"
SEED = 20260927

TRANSPARENT = None

# --- palettes ------------------------------------------------------------------------------------
#
# One entry per material, one color per keyframe: noon, golden hour, sunset, night.
#
# Each band is closer to the sky the farther away it is - the haze of distance - and the near sand
# is the most saturated. At sunset the sun is behind the dunes and everything facing the player is
# in its own shadow, rimmed where the crests catch the light; at night the "shadow" faces, turned to
# the left, are the moonlit ones.

FAR = {
    "shadow": ((222, 192, 142), (204, 140, 96), (118, 58, 88), (38, 22, 58)),
    "body": ((234, 208, 160), (224, 164, 108), (148, 74, 92), (30, 18, 48)),
    "lit": ((244, 222, 178), (240, 188, 122), (196, 102, 94), (26, 16, 42)),
    "pyr_shadow": ((206, 176, 128), (188, 124, 86), (104, 50, 80), (40, 24, 62)),
    "pyr_lit": ((240, 216, 166), (244, 186, 118), (214, 112, 92), (24, 14, 40)),
    "pyr_cap": ((252, 238, 200), (255, 222, 150), (255, 168, 110), (58, 40, 84)),
}

MID = {
    "deep": ((200, 156, 96), (160, 98, 66), (82, 38, 64), (14, 8, 26)),
    "shadow": ((216, 172, 106), (180, 114, 72), (100, 48, 72), (44, 30, 72)),
    "body": ((230, 190, 122), (212, 148, 84), (130, 62, 78), (26, 16, 44)),
    "lit": ((244, 210, 142), (238, 176, 98), (180, 88, 82), (20, 12, 36)),
    "crest": ((252, 230, 176), (252, 208, 128), (242, 148, 88), (74, 58, 110)),
    "palm_trunk": ((150, 100, 60), (120, 70, 44), (70, 30, 44), (12, 8, 20)),
    "palm_leaf": ((124, 132, 60), (110, 100, 44), (62, 34, 46), (10, 8, 18)),
    "palm_leaf_lit": ((164, 168, 76), (150, 128, 54), (92, 48, 52), (16, 12, 28)),
}

NEAR = {
    "deep": ((176, 122, 72), (126, 72, 50), (60, 26, 44), (8, 4, 14)),
    "ripple": ((196, 146, 86), (160, 98, 60), (84, 38, 56), (12, 8, 22)),
    "shadow": ((208, 160, 94), (170, 106, 64), (92, 42, 60), (40, 28, 70)),
    "body": ((228, 182, 108), (206, 140, 78), (124, 58, 70), (22, 14, 38)),
    "lit": ((242, 206, 132), (234, 170, 92), (172, 80, 74), (16, 10, 30)),
    "crest": ((252, 228, 166), (250, 204, 122), (240, 136, 82), (70, 54, 104)),
    "rock": ((146, 110, 82), (120, 78, 60), (70, 34, 48), (26, 18, 44)),
    "rock_lit": ((188, 150, 112), (170, 116, 82), (120, 60, 66), (50, 38, 80)),
    "bone": ((242, 234, 212), (250, 220, 176), (232, 160, 132), (112, 100, 140)),
    "bone_shade": ((196, 182, 156), (204, 164, 126), (160, 94, 96), (62, 54, 92)),
}

MOON = {
    ".": (0, 0, 0, 0),
    # the rest of the disc, in earthshine: just lighter than the night sky behind it
    "e0": (40, 32, 70, 255),
    "e1": (50, 40, 84, 255),
    # the lit crescent, and a shadow along its inner edge
    "m0": (176, 164, 210, 255),
    "m1": (222, 214, 244, 255),
    "m2": (246, 242, 255, 255),
    "c": (196, 186, 228, 255),
}


def keyframe_count(palette):
    counts = {len(ramp) for ramp in palette.values()}
    assert len(counts) == 1, "every material needs a color for every keyframe"
    return counts.pop()


# --- periodic shapes -------------------------------------------------------------------------------


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


def wrapped_offset(x, center, width):
    """Signed distance from [center] to [x] the short way round a strip [width] wide."""
    d = (x - center) % width
    return d - width if d > width / 2 else d


def dune_field(width, count, height_range, windward_range, lee_range, rng):
    """
    Dunes as asymmetric bumps: a long, gentle windward slope on the left - the wind comes from the
    left, the way the scenery goes - and a short, steep slip face on the right, meeting in a crest.

    Returns the dunes as (crest x, height, windward width, lee width), spread round the strip on a
    jittered grid so no stretch of it is empty and no two crests stack.
    """
    spacing = width / count
    dunes = []
    for index in range(count):
        center = (index * spacing + rng.uniform(-0.3, 0.3) * spacing) % width
        dunes.append((center, rng.uniform(*height_range), rng.uniform(*windward_range), rng.uniform(*lee_range)))
    return dunes


def dune_height(x, dune, width):
    center, height, windward, lee = dune
    d = wrapped_offset(x, center, width)
    t = -d / windward if d <= 0 else d / lee
    if t >= 1.0:
        return 0.0, d
    # Rounded over the crest, straight down the slopes: 1 - t^2 on the windward side, and a
    # steeper fall on the lee.
    shape = 1.0 - t * t if d <= 0 else (1.0 - t) ** 1.2
    return height * shape, d


def surface(width, dunes, base, noise, noise_amplitude):
    """Per column: the height of the sand above [base], and which dune owns the surface there."""
    heights = []
    owners = []
    for x in range(width):
        best, owner = 0.0, None
        for dune in dunes:
            h, d = dune_height(x, dune, width)
            if h > best:
                best, owner = h, (dune, d)
        heights.append(base + best + noise_amplitude * (noise(x) - 0.5))
        owners.append(owner)
    return heights, owners


def face_of(x, y, top, owner, width, skew):
    """
    Which face of its dune a pixel under the surface is on.

    The line between the faces runs down from the crest and leans toward the lee, the way a slip
    face's edge does, so a dune shows as a lit triangle beside a shaded one rather than as two
    columns of color.
    """
    if owner is None:
        return "body"
    dune, _ = owner
    center = dune[0]
    crest_top = top  # the surface at the crest is as high as this dune gets
    d = wrapped_offset(x, center, width)
    return "lee" if d > (y - crest_top) * skew else "windward"


# --- the strips ----------------------------------------------------------------------------------


def blank(width, height):
    return [[TRANSPARENT] * width for _ in range(height)]


def fill_dunes(grid, width, height, heights, owners, crest_width, face_depth, skew, deep_from=None, ripples=None):
    """
    Fills the sand under a surface: a bright crest along the top, the two faces of each dune for
    [face_depth] rows under it, then the body, then - where given - a darker band toward the bottom
    so a nearer strip laid over this one has something to stand out against.
    """
    for x in range(width):
        top = height - heights[x]
        owner = owners[x]
        crest_y = None
        if owner is not None:
            dune, _ = owner
            crest_y = height - max(heights[(int(dune[0]) + k) % width] for k in (-1, 0, 1))
        for y in range(max(0, int(top)), height):
            below = y - top
            if below < 1.0 and owner is not None and abs(owner[1]) < crest_width:
                material = "crest"
            elif below < face_depth and owner is not None:
                side = face_of(x, y, crest_y, owner, width, skew)
                material = "lit" if side == "lee" else "shadow"
            else:
                material = "body"
            if deep_from is not None and y >= deep_from:
                material = "deep"
            grid[y][x] = material
        if ripples:
            for depth in ripples(x):
                ry = int(top + depth)
                if 0 <= ry < height and grid[ry][x] in ("body", "shadow", "lit"):
                    grid[ry][x] = "ripple"


def far_strip(rng):
    """
    Low dunes on the horizon with the pyramids standing in them. Tall enough that its sand runs
    down behind the middle band's lowest hollow, so no sky ever shows between the two.
    """
    width, height = 960, 116
    grid = blank(width, height)
    ground = height - 30

    # The pyramids first, so the dunes drawn after them bury their feet: a great one with a lesser
    # beside it, and a third on its own further round. Lit on the right face, the sun's side, with a
    # cap of dressed stone still catching it.
    for center, half_base, tall in ((230, 40, 46), (298, 27, 31), (700, 22, 25)):
        for x in range(int(center - half_base) - 1, int(center + half_base) + 2):
            dx = x - center
            if abs(dx) > half_base:
                continue
            peak = ground - tall * (1.0 - abs(dx) / half_base)
            for y in range(int(peak), ground + 1):
                material = "pyr_lit" if dx >= 0 else "pyr_shadow"
                if y < ground - tall * 0.84:
                    material = "pyr_cap"
                grid[y][x % width] = material
        # Courses of stone, a broken line every few rows, so a pyramid reads as built rather than
        # as a triangle.
        for course in range(4, tall - 6, 5):
            y = ground - course
            span = half_base * (1.0 - course / tall)
            for x in range(int(center - span) + 2, int(center + span) - 1):
                if (x + course) % 4 != 0:
                    grid[y][x % width] = "pyr_shadow" if x >= center else "shadow"

    noise = wrapped_noise(width, 12, rng)
    dunes = dune_field(width, 7, (4.0, 12.0), (80.0, 130.0), (24.0, 44.0), rng)
    heights, owners = surface(width, dunes, base=26.0, noise=noise, noise_amplitude=3.0)
    fill_dunes(grid, width, height, heights, owners, crest_width=0.0, face_depth=4, skew=0.8)
    return grid, width, height


def palm(grid, width, height, base_x, base_y, tall, lean, rng):
    """A palm: a curving trunk and a crown of drooping fronds, wrapped round the seam."""
    top_x, top_y = base_x, base_y
    for step in range(tall):
        t = step / tall
        x = base_x + lean * t * t * tall * 0.25
        y = base_y - step
        for dx in (0, 1):
            xi = int(x + dx) % width
            if 0 <= int(y) < height:
                grid[int(y)][xi] = "palm_trunk"
        top_x, top_y = x, y
    for frond in range(7):
        angle = pi * (0.05 + frond / 6.0 * 0.9) + rng.uniform(-0.08, 0.08)
        length = rng.uniform(7.0, 11.0)
        for step in range(int(length)):
            t = step / length
            x = top_x + cos(angle) * step * (1.0 if frond % 2 else -1.0) * 0.9
            y = top_y - sin(angle) * step * 0.55 + t * t * 6.0
            xi = int(round(x)) % width
            yi = int(round(y))
            if 0 <= yi < height:
                grid[yi][xi] = "palm_leaf_lit" if x > top_x else "palm_leaf"


def mid_strip(rng):
    width, height = 960, 90
    grid = blank(width, height)
    noise = wrapped_noise(width, 16, rng)
    dunes = dune_field(width, 6, (14.0, 30.0), (60.0, 110.0), (18.0, 34.0), rng)
    heights, owners = surface(width, dunes, base=42.0, noise=noise, noise_amplitude=4.0)
    # An oasis in a hollow between two dunes: three palms of different heights leaning together.
    oasis = min(range(width), key=lambda x: heights[x] if 380 <= x <= 620 else 1e9)
    for offset, tall, lean in ((-14, 20, 0.6), (0, 26, -0.4), (12, 17, 0.9)):
        palm(grid, width, height, oasis + offset, int(height - heights[(oasis + offset) % width]) + 2, tall, lean, rng)
    fill_dunes(grid, width, height, heights, owners, crest_width=2.5, face_depth=9, skew=0.9)
    return grid, width, height


def near_strip(rng):
    width, height = 1440, 57
    grid = blank(width, height)
    noise = wrapped_noise(width, 30, rng)
    dunes = dune_field(width, 11, (8.0, 18.0), (50.0, 90.0), (14.0, 26.0), rng)
    heights, owners = surface(width, dunes, base=22.0, noise=noise, noise_amplitude=3.0)

    # Wind ripples: thin lines following the surface a few pixels down, broken up so they read as
    # ripples in the sand rather than as strata.
    ripple_rng = random.Random(SEED + 3)
    breaks = [ripple_rng.random() for _ in range(width)]

    def ripples(x):
        out = []
        for depth in (6, 11, 17, 24):
            wobble = 1.2 * sin(2.0 * pi * (x / width) * 90 + depth)
            if breaks[(x // 7 + depth) % width] > 0.35:
                out.append(depth + wobble)
        return out

    fill_dunes(grid, width, height, heights, owners, crest_width=3.0, face_depth=5, skew=1.0,
               deep_from=height - 5, ripples=ripples)

    # Stones half sunk in the sand, and here and there a bleached skull: small, low, and never
    # tall enough to be mistaken for an obstacle.
    detail = random.Random(SEED + 4)
    for _ in range(26):
        x = detail.randrange(width)
        top = int(height - heights[x])
        radius = detail.uniform(1.5, 3.5)
        for dy in range(-int(radius) - 1, int(radius) + 2):
            for dx in range(-int(radius * 1.6) - 1, int(radius * 1.6) + 2):
                if (dx / 1.6) ** 2 + dy * dy <= radius * radius:
                    y = top + 4 + dy
                    if top <= y < height:
                        grid[y][(x + dx) % width] = "rock_lit" if dx > 0 and dy < 0 else "rock"
    for _ in range(3):
        x = detail.randrange(width)
        top = int(height - heights[x]) + 3
        # A skull side on, facing left: a dome, an eye socket, a jaw.
        for dy in range(-3, 3):
            for dx in range(-4, 5):
                if (dx / 4.2) ** 2 + ((dy + 0.5) / 3.2) ** 2 <= 1.0 and top + dy < height:
                    grid[top + dy][(x + dx) % width] = "bone" if dy < 1 else "bone_shade"
        grid[top - 1][(x - 2) % width] = "rock"
        grid[top + 2][(x - 4) % width] = "bone_shade"
        grid[top + 2][(x - 3) % width] = "bone_shade"
    return grid, width, height


def moon():
    """
    A crescent two days past new, lit from the lower right - where the sun went down - with the
    rest of its disc in earthshine. The earthshine is not decoration: at night the disc has to hide
    the stars behind it, and a crescent with stars showing through its dark side is a hole.
    """
    size = 24
    grid = [["."] * size for _ in range(size)]
    cx = cy = 11.5
    radius = 10.5
    for y in range(size):
        for x in range(size):
            px, py = x + 0.5, y + 0.5
            if (px - cx) ** 2 + (py - cy) ** 2 > radius * radius:
                continue
            # The terminator: the disc minus an offset disc, both hard-edged.
            dark = (px - cx + 5.6) ** 2 + (py - cy + 3.6) ** 2 <= (radius * 0.98) ** 2
            if dark:
                grid[y][x] = "e1" if (px - cx) + (py - cy) > 2 else "e0"
            else:
                depth = (px - cx) * 0.55 + (py - cy) * 0.35
                grid[y][x] = "m2" if depth > 5.5 else ("m1" if depth > 2.0 else "m0")
    for x, y in ((17, 14), (18, 15), (15, 18), (19, 11)):
        if grid[y][x] in ("m1", "m2"):
            grid[y][x] = "c"
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            image.putpixel((x, y), MOON[grid[y][x]])
    return image


def settle_seam(grid, width):
    """
    Turns a periodic strip round so its first column is where it changes least from one column to
    the next. Every column of it already follows on from the one before, the last into the first
    included, so this moves nothing but where the file happens to start - and puts the join, which
    is the one place a stray step would be blamed on the tiling, somewhere quiet.
    """
    def step(x):
        return sum(row[x - 1] != row[x] for row in grid)

    start = min(range(width), key=step)
    return [row[start:] + row[:start] for row in grid]


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


def check_seam(image, width, height):
    def column_step(left, right):
        total = 0
        count = 0
        for y in range(height):
            a = image.getpixel((left, y))
            b = image.getpixel((right, y))
            total += sum(abs(p - q) for p, q in zip(a, b))
            count += 1
        return total / count

    seam = column_step(width - 1, 0)
    interior = sum(column_step(x, x + 1) for x in range(0, width - 1, 7)) / len(range(0, width - 1, 7))
    return seam, interior


def main() -> int:
    rng = random.Random(SEED)
    for name, build, palette in (
        ("desertFar.png", far_strip, FAR),
        ("desertMid.png", mid_strip, MID),
        ("desertNear.png", near_strip, NEAR),
    ):
        grid, width, height = build(rng)
        grid = settle_seam(grid, width)
        image = save_keyframes(OUT / name, grid, width, height, palette)
        seam, interior = check_seam(image, width, height)
        print(f"{OUT / name} ({image.width}x{image.height}, {keyframe_count(palette)} keyframes of {height}); "
              f"seam {seam:.1f} vs typical step {interior:.1f}")

    image = moon()
    image.save(OUT / "desertMoon.png")
    print(f"{OUT / 'desertMoon.png'} ({image.width}x{image.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
