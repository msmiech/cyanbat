# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the forest's boss: the Moth Queen, four frames of wingbeat.

    uv run tools/generate_forest_boss_sprite.py

Stage 1's boss is the cave's crimson drone drawn three times over, which works because it is the
toughest thing the cave's waves were already sending. Stage 2 wanted a boss with a design of its
own, drawn at its own size rather than magnified: at 3x a 32-pixel sprite has 3-pixel "pixels",
which reads as coarse next to everything else on screen.

So this is drawn natively at 96x80, the same footprint the cave's boss occupies, at the same pixel
density as the bat. A moth, because the forest's other hostiles are its brood - she summons wasp
swarms mid-fight - and because a moth's wings are the one animal shape big enough to fill a boss
frame and still read as *flying*:

* **Eye spots.** Two on the forewing, two on the hind. They are what make the wings read as a
  moth's rather than a bat's, and they are a threat display - which is what a boss is.
* **Warm, dusky and loud.** Rose and plum wings with amber eye spots. Warm like every hostile, and
  darker than the small fry so the smaller enemies she summons still read in front of her.
* **A crown.** Feathered antennae swept back, so the silhouette says *queen* at a glance.

Faces left, like every hostile: the head is at the left edge and the wings trail right.
"""

import pathlib
import sys
from math import cos, radians, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME_WIDTH = 96
FRAME_HEIGHT = 80
FRAME_COUNT = 4

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (16, 8, 16, 255),
    "O": (34, 16, 34, 255),
    # wings: dusky rose
    "w0": (76, 22, 52, 255),
    "w1": (124, 38, 80, 255),
    "w2": (176, 62, 108, 255),
    "w3": (222, 116, 150, 255),
    # the far wings, a step darker, so the pair reads as two planes
    "d0": (48, 14, 36, 255),
    "d1": (82, 26, 58, 255),
    # eye spots: amber rings round a dark pupil with a glint
    "s0": (150, 70, 20, 255),
    "s1": (234, 150, 44, 255),
    "s2": (255, 216, 120, 255),
    # body: plum with a pale fur collar
    "b0": (44, 18, 44, 255),
    "b1": (78, 34, 76, 255),
    "b2": (118, 58, 112, 255),
    "b3": (164, 100, 156, 255),
    "f0": (206, 170, 190, 255),
    "f1": (244, 226, 236, 255),
    # the eyes: cold, like every hostile's
    "e": (226, 252, 255, 255),
    "p": (16, 10, 28, 255),
}

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "forestBoss.png"

W, H = FRAME_WIDTH, FRAME_HEIGHT

# One beat, as the angle the forewing's leading edge makes above horizontal, and how far the wing
# is spread. A full beat rather than a ping-pong, like the enemy sheets.
BEAT = (
    (40.0, 1.00),   # top of the stroke
    (14.0, 0.94),   # driving down
    (-22.0, 0.86),  # bottom of the stroke
    (20.0, 0.90),   # recovering
)


def raster(shape):
    return pa.rasterize(shape, W, H)


def paint(grid, pixels, key):
    for x, y in pixels:
        grid[y][x] = key


def rotate(point, origin, degrees):
    """[point] turned about [origin], counterclockwise on screen for a positive angle."""
    a = radians(degrees)
    dx, dy = point[0] - origin[0], point[1] - origin[1]
    return (origin[0] + dx * cos(a) + dy * sin(a), origin[1] - dx * sin(a) + dy * cos(a))


def wing_shape(root, lift, spread, length, depth, droop):
    """
    One wing as a scalloped blade from [root], lifted [lift] degrees above horizontal.

    Drawn flat, pointing back and to the right, and then turned about the root - so the same
    outline serves the whole beat, and the scalloped trailing edge moves with the wing instead of
    being redrawn per frame.
    """
    reach = length * spread
    lead = (root[0] + reach, root[1])
    tip = (root[0] + reach * 1.02, root[1] + depth * 0.35)
    trailing_end = (root[0] + reach * 0.35, root[1] + depth)
    outline = [root, lead, tip]
    outline += pa.bezier(tip, (root[0] + reach * 0.85, root[1] + depth * droop), trailing_end, steps=6)
    outline += [trailing_end, (root[0] - 2.0, root[1] + depth * 0.45)]
    return pa.polygon([rotate(p, root, lift) for p in outline]), [rotate(p, root, lift) for p in (lead, tip, trailing_end)]


def spot_at(lead, tip, trailing_end, along, across):
    """A point inside a wing, as fractions along its span and across its chord."""
    mid_x = lead[0] + (tip[0] - lead[0]) * 0.5
    mid_y = lead[1] + (tip[1] - lead[1]) * 0.5
    base_x = trailing_end[0] + (mid_x - trailing_end[0]) * along
    base_y = trailing_end[1] + (mid_y - trailing_end[1]) * along
    return base_x + (lead[0] - trailing_end[0]) * across * 0.2, base_y + (lead[1] - trailing_end[1]) * across * 0.2


def eye_spot(grid, center, radius, clip):
    cx, cy = center
    ring = raster(pa.ellipse(cx, cy, radius, radius * 0.9)) & clip
    paint(grid, ring, "s0")
    paint(grid, raster(pa.ellipse(cx, cy, radius * 0.72, radius * 0.66)) & clip, "s1")
    paint(grid, raster(pa.ellipse(cx, cy, radius * 0.40, radius * 0.38)) & clip, "p")
    paint(grid, raster(pa.ellipse(cx - radius * 0.15, cy - radius * 0.15, radius * 0.14, radius * 0.14)) & clip, "s2")


def render(frame):
    grid = pa.blank(W, H)
    lift, spread = BEAT[frame]
    bob = (-2, 0, 2, 0)[frame]
    cy = 44 + bob

    shoulder = (34.0, cy - 6.0)

    # The far pair first, lifted a little higher and cut shorter, in the darker ramp.
    far_fore, _ = wing_shape((shoulder[0] + 4, shoulder[1] - 1), lift + 12, spread, 48, 26, 0.95)
    far_hind, _ = wing_shape((shoulder[0] + 8, shoulder[1] + 6), lift * 0.5 - 30, spread, 32, 18, 0.9)
    far = raster(pa.union(far_fore, far_hind))
    pa.shade_bands(grid, far, ((0.5, "d1"), (1.01, "d0")))

    # The near hindwing, rounder and hanging lower.
    hind, (h_lead, h_tip, h_trail) = wing_shape((shoulder[0] + 6, shoulder[1] + 8), lift * 0.5 - 38, spread, 36, 22, 1.05)
    hind_px = raster(hind)
    pa.shade_bands(grid, hind_px, ((0.3, "w2"), (0.7, "w1"), (1.01, "w0")))

    # The near forewing, the big one.
    fore, (f_lead, f_tip, f_trail) = wing_shape(shoulder, lift, spread, 52, 30, 0.95)
    fore_px = raster(fore)
    pa.shade_bands(grid, fore_px, ((0.14, "w3"), (0.45, "w2"), (0.8, "w1"), (1.01, "w0")))

    # Veins radiating from the root, so the wing has structure at this size.
    for along in (0.45, 0.75):
        end = (f_trail[0] + (f_tip[0] - f_trail[0]) * along, f_trail[1] + (f_tip[1] - f_trail[1]) * along)
        paint(grid, raster(pa.capsule(shoulder, end, 0.6, 0.5)) & fore_px, "w1")

    eye_spot(grid, spot_at(f_lead, f_tip, f_trail, 0.62, 0.5), 5.2, fore_px)
    eye_spot(grid, spot_at(h_lead, h_tip, h_trail, 0.55, 0.3), 3.6, hind_px - fore_px)

    pa.outline_against(grid, hind_px - fore_px, fore_px, color="O")
    pa.outline_against(grid, far - hind_px - fore_px, hind_px | fore_px, color="O")

    # The body, over the wing roots: a segmented abdomen trailing right, a furred thorax, a head.
    abdomen = pa.union(
        pa.ellipse(47, cy + 6, 15.0, 8.5),
        pa.polygon([(54, cy + 2), (66, cy + 9), (54, cy + 12)]),
    )
    thorax = pa.ellipse(30, cy + 1, 11.0, 10.0)
    head = pa.ellipse(17, cy - 1, 8.0, 7.6)
    legs = pa.union(
        pa.capsule((26, cy + 8), (20, cy + 17), 1.1, 0.8),
        pa.capsule((32, cy + 9), (30, cy + 19), 1.1, 0.8),
        pa.capsule((38, cy + 9), (41, cy + 18), 1.1, 0.8),
    )
    body = raster(pa.union(abdomen, thorax, head))
    pa.shade_bands(grid, body, ((0.25, "b3"), (0.55, "b2"), (0.82, "b1"), (1.01, "b0")))
    leg_px = raster(legs) - body
    paint(grid, leg_px, "b0")

    # Segments on the abdomen.
    for sx in (40, 46, 52):
        paint(grid, raster(pa.capsule((sx, cy - 1), (sx + 1.5, cy + 13), 0.55, 0.55)) & body, "b1")

    # The fur collar, the pale band that separates head from body.
    collar = raster(pa.ellipse(26, cy + 1, 4.5, 8.0)) & body
    pa.shade_bands(grid, collar, ((0.5, "f1"), (1.01, "f0")))

    # The crown: two feathered antennae swept back over the head.
    antennae = set()
    for base, tip in (((16, cy - 6), (30, cy - 24)), ((20, cy - 6), (38, cy - 20))):
        antennae |= raster(pa.capsule(base, tip, 1.0, 0.7))
        for i in range(1, 6):
            t = i / 6.0
            px, py = base[0] + (tip[0] - base[0]) * t, base[1] + (tip[1] - base[1]) * t
            antennae |= raster(pa.capsule((px, py), (px + 3.5, py + 1.5), 0.55, 0.45))
    antennae -= body
    paint(grid, antennae, "f0")

    # The eyes: big, compound, cold.
    paint(grid, raster(pa.ellipse(13.5, cy - 1.5, 4.2, 4.2)), "e")
    paint(grid, raster(pa.ellipse(12.4, cy - 1.2, 2.2, 2.4)), "p")
    paint(grid, raster(pa.ellipse(11.2, cy - 2.8, 0.8, 0.8)), "e")

    pa.outline_against(grid, fore_px - body, body, color="O")
    pa.outer_outline(grid, W, H)
    return grid


def main() -> int:
    grids = [render(frame) for frame in range(FRAME_COUNT)]
    sheet = pa.save_sheet(OUTPUT, grids, PALETTE, FRAME_WIDTH, FRAME_HEIGHT)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {FRAME_COUNT} frames)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
