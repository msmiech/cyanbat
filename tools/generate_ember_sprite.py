# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the Caco Imp's ember: a coal it spits, which hangs in the air and bursts into a ring.

    uv run tools/generate_ember_sprite.py

A lumpy coal, its crust a dark crimson-brown split by cracks that glow from a heart of white-hot
yellow through orange. Four frames of it throbbing: the glow swells out along the cracks and the
heart brightens, then sinks back, so a hanging ember reads as alive and about to go. The flash
before it bursts is the game's, a wash over the whole sprite, not part of the sheet.

Warm, as everything hostile is, and in the imp's own crimson, so whose it is reads at a glance.
Ringed in the near-black outline every creature wears, so it stands out on the desert's pale noon
as well as in the cave.

Four 18x18 frames, laid left to right: `EMBER_FRAME` and `EMBER_FRAME_COUNT` in the game.
"""

import pathlib
from math import atan2, cos, hypot

from pixelart import blank, outer_outline, rasterize, save_sheet

FRAME = 18
FRAMES = 4
CENTER = FRAME / 2

PALETTE = {
    "o": (28, 8, 10, 255),
    "c": (78, 22, 22, 255),
    "d": (120, 34, 28, 255),
    "r": (196, 54, 34, 255),
    "g": (255, 128, 48, 255),
    "y": (255, 206, 92, 255),
    "w": (255, 246, 210, 255),
}

# The coal's outline, as a radius by angle: a circle pushed in and out by a few lumps, so it reads as
# a rough coal rather than a ball.
RADIUS = 6.6
LUMPS = ((3, 0.7, 0.3), (5, 0.45, 1.7))

# Where the light falls from on the crust: the top left, as on everything else in the game.
LIGHT = (-0.6, -0.8)

# The cracks, as segments out from near the middle, through which the heart shows.
CRACKS = (
    ((9.0, 9.0), (4.4, 5.6)),
    ((9.0, 9.0), (13.8, 6.6)),
    ((9.0, 9.0), (10.9, 14.4)),
    ((9.0, 9.0), (5.4, 12.8)),
)

# How wide the glowing cracks are, and how big the heart is, by frame: a swell and a sink.
CRACK_WIDTH = (0.8, 1.1, 1.35, 1.1)
HEART = (1.7, 2.3, 3.0, 2.3)

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "ember.png"


def radius_at(angle):
    """How far the coal's edge is from its middle at [angle]."""
    return RADIUS + sum(depth * cos(count * angle + turn) for count, depth, turn in LUMPS)


def inside_coal(x, y):
    dx, dy = x - CENTER, y - CENTER
    return hypot(dx, dy) <= radius_at(atan2(dy, dx))


def distance_to_cracks(x, y):
    best = float("inf")
    for (px, py), (qx, qy) in CRACKS:
        dx, dy = qx - px, qy - py
        span = dx * dx + dy * dy
        t = max(0.0, min(1.0, ((x - px) * dx + (y - py) * dy) / span))
        best = min(best, hypot(x - (px + dx * t), y - (py + dy * t)))
    return best


def frame(index):
    grid = blank(FRAME, FRAME)
    width = CRACK_WIDTH[index]
    heart = HEART[index]
    for x, y in rasterize(inside_coal, FRAME, FRAME):
        cx, cy = x + 0.5, y + 0.5
        from_heart = hypot(cx - CENTER, cy - CENTER)
        crack = distance_to_cracks(cx, cy)
        if from_heart <= heart * 0.55:
            key = "w"
        elif from_heart <= heart:
            key = "y"
        elif crack <= width * 0.6:
            # Brightest nearest the heart, cooling toward the crust.
            key = "y" if from_heart <= heart + 1.6 else "g"
        elif crack <= width:
            key = "g" if from_heart <= heart + 2.4 else "r"
        else:
            # The crust, lit from the top left and dark on the far side.
            dx, dy = (cx - CENTER) / RADIUS, (cy - CENTER) / RADIUS
            lit = -(dx * LIGHT[0] + dy * LIGHT[1])
            key = "r" if lit > 0.45 else "d" if lit > -0.2 else "c"
        grid[y][x] = key
    outer_outline(grid, FRAME, FRAME)
    return grid


def main():
    grids = [frame(i) for i in range(FRAMES)]
    save_sheet(OUTPUT, grids, PALETTE, FRAME, FRAME)
    print(f"wrote {OUTPUT}")


if __name__ == "__main__":
    main()
