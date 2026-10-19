# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the orb Guardian Orb sends circling the bat: six frames of a sphere of energy, swirling.

    uv run tools/generate_orb_sprite.py

The bat's own colors, because it is the bat's. It is energy rather than a thing, so it is lit from
inside rather than from the top left: a pale heart, cyan going deeper toward its edge, a bright rim
where the glow is seen edge-on, and a see-through haze round it. No hard highlight and no outline -
the white glint and the dark ring it once had made it read as a glass bauble.

The sphere stays put from frame to frame; what moves is the energy in it. Two arms of light curl
from the heart out to the rim and turn a sixth of the way round each frame, clockwise - the way the
orbs go round the bat - so a ring of them reads as spinning even where it is standing still against
the scenery, and the heart throbs as they go. Six 14x14 frames, laid left to right: `ORB_FRAME` and
`ORB_FRAME_COUNT` in the game.

Heavy Rounds makes the orbs bigger, and `orbHeavy.png` draws the orb again at each size, a band of six
frames per size, top to bottom, so that a bigger orb is drawn rather than stretched: stretched
nearest-neighbor, a pixel of art came out one pixel wide here and two there, and the sphere went
lumpy. Every size keeps the pixel density of the rest of the art.

The orb fills its box less the last column and row, which a blit never paints
(`ComposeGraphics.drawPixmap`).
"""

import pathlib
from math import atan2, cos, floor, hypot, pi, sin

from PIL import Image

from pixelart import blank, save_sheet

FRAME = 14
FRAMES = 6
TAU = 2 * pi

# The sphere's radius, in pixels at the base size. The haze is a pixel round it at every size, which
# keeps it a clean ring: drawn out to a radius, it stuck out in single pixels.
RADIUS = 4.7

# How far out, as a share of the radius, each tone holds: the heart, frame by frame as it throbs,
# and the glow round it. Past that is the body, out to a rim a pixel thick. Few bands, since more
# ringed the sphere like a target and drowned out the energy turning in it.
HEARTS = (0.30, 0.36, 0.30, 0.22, 0.16, 0.22)
GLOW = 0.42

# The arms of light: how many, how far round each curls from the heart to the rim (clockwise going
# out, so they trail the way they turn), how wide each is in pixels, and the stretch of the radius
# they are drawn along, between the heart and the rim.
ARMS = 2
CURL = 2.6
ARM_WIDTH = 1.5
ARMS_FROM = 0.3
ARMS_TO = 0.95

# The sizes Heavy Rounds draws the orbs at, as `SHOT_SCALE_PER_LEVEL` and `MAX_SHOT_SIZE_LEVEL` in
# `CyanBatConstants` declare them: the box at each is the base box scaled and rounded, as the game
# rounds it.
SCALE_PER_LEVEL = 0.2
MAX_SIZE_LEVEL = 3

# h haze   r rim   c body   l glow and arms   w heart
# The haze, the rim and the body are see-through: the haze faintly, so it glows over the dark and
# still rings the orb over the desert's pale noon; the sphere a little, so it is light rather than
# glass. The body is a deeper cyan than the arms turning in it, so they read.
PALETTE = {
    "h": (0, 150, 255, 84),
    "r": (90, 226, 255, 235),
    "c": (0, 176, 236, 220),
    "l": (110, 236, 255, 255),
    "w": (196, 250, 255, 255),
}

ASSETS = pathlib.Path(__file__).resolve().parent.parent / "assets"


def box(level):
    """The orb's box at Heavy Rounds size [level], and its scale: rounded half up, as roundToInt is."""
    scale = 1 + level * SCALE_PER_LEVEL
    return floor(FRAME * scale + 0.5), scale


def on_arm(out, angle, turn, radius):
    """Whether a pixel [out] of the way to the rim, at [angle], lies on one of the arms turned [turn]."""
    if not ARMS_FROM <= out <= ARMS_TO:
        return False
    for arm in range(ARMS):
        heading = turn + TAU * arm / ARMS + CURL * (out - ARMS_FROM)
        off = (angle - heading + pi) % TAU - pi
        # Its width held in pixels, so it does not fan out toward the rim.
        if abs(off) * out * radius <= ARM_WIDTH / 2:
            return True
    return False


def frame(index, level=0):
    size, scale = box(level)
    # Centered on the box less its last column and row, which the blit never paints.
    drawn = size - 1
    center = drawn / 2
    radius = RADIUS * scale
    # Clockwise as the frame is drawn: y runs down, so a growing angle goes right, then down.
    turn = TAU * index / FRAMES
    grid = blank(size, size)
    for y in range(drawn):
        for x in range(drawn):
            dx, dy = x + 0.5 - center, y + 0.5 - center
            away = hypot(dx, dy)
            out = away / radius
            if out > 1:
                continue
            if out <= HEARTS[index]:
                grid[y][x] = "w"
            elif on_arm(out, atan2(dy, dx), turn, radius):
                grid[y][x] = "l"
            elif out <= GLOW:
                grid[y][x] = "l"
            elif away <= radius - 0.9:
                grid[y][x] = "c"
            else:
                grid[y][x] = "r"
    # The haze: every pixel touching the sphere, corners included.
    sphere = {(x, y) for y in range(drawn) for x in range(drawn) if grid[y][x] in PALETTE}
    for y in range(drawn):
        for x in range(drawn):
            if (x, y) not in sphere and any(
                    (x + dx, y + dy) in sphere for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                grid[y][x] = "h"
    return grid


def save_heavy(name):
    """The orb at every size past the base one: a band of its frames per size, top to bottom, each
    frame as wide as its box."""
    sizes = [box(level)[0] for level in range(1, MAX_SIZE_LEVEL + 1)]
    sheet = Image.new("RGBA", (max(sizes) * FRAMES, sum(sizes)), (0, 0, 0, 0))
    top = 0
    for level, size in enumerate(sizes, start=1):
        for index in range(FRAMES):
            for y, row in enumerate(frame(index, level)):
                for x, key in enumerate(row):
                    if key in PALETTE:
                        sheet.putpixel((index * size + x, top + y), PALETTE[key])
        top += size
    path = ASSETS / name
    sheet.save(path)
    print(f"{path} ({sheet.width}x{sheet.height}, {len(sizes)} sizes of {FRAMES} frames)")


def main() -> int:
    path = ASSETS / "orb.png"
    sheet = save_sheet(path, [frame(i) for i in range(FRAMES)], PALETTE, FRAME, FRAME)
    print(f"{path} ({sheet.width}x{sheet.height}, {FRAMES} frames of {FRAME}x{FRAME})")
    save_heavy("orbHeavy.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
