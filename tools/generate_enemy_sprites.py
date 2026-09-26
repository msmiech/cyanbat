# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the cave's enemy sheet: three hostiles, four frames of wingbeat each.

    uv run tools/generate_enemy_sprites.py

The drones this replaced read as fish: a teardrop hull with an eye at the pointed end, a spike off
the back where a tail fin would be, and a pair of blades that sat on it like fins. So the cave is
now haunted by imps instead, and every part of one is chosen to be a thing no fish has:

* **A round body under a crown of horns**, drawn a little taller than it is wide, so it stands
  upright rather than lying along the way it flies, the way a hull - or a fish - does.
* **One big eye, and a grin full of teeth** under it. The face is what the player reads first.
* **Devil wings** beating off its back, sharp-pointed where the bat's are scalloped, and **clawed
  feet** hanging underneath. The first pass trailed a tail behind instead, and a round body towing
  a tail is a tadpole - a fish again by another name.

Two rules from the sheet before still hold, and they are the whole design:

* **The player is cool, everything hostile is warm.** The bat is cyan and deep blue; these are
  violet, amber and crimson, the same colors their shots are drawn in. The one cold thing on them
  is the eye, so it reads as a lamp in the dark rather than as part of the body.
* **One body, three tempers.** All three are the same imp. They differ in hue, in their horns and
  in how angry the face is, so a new wave reads as *more of the same enemy, angrier* rather than as
  a different game.

Types are indexed the way `EntityFactory.srcXOf` addresses them: 0 SCOUT, 1 WEAVER, 2 STRIKER.
Type 2 is also what the boss is drawn from, three times over, so it is the one that has to survive
being scaled up - and the one that gets the spines down its back.
"""

import pathlib
import sys
from math import cos, hypot, radians, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME_WIDTH = 32
FRAME_HEIGHT = 29
FRAME_COUNT = 4
TYPE_COUNT = 3

# Four frames at the interval EntityFactory uses puts the cycle at about the 0.4s the bat's six
# take, so the two animate at the same rate rather than one of them looking slowed down.
#
# A full beat, not a ping-pong: top, driving down, bottom, recovering. `wing` is the wing's angle
# above straight back, in degrees; `kick` swings the feet, a beat behind the wings the way anything
# dangling follows what the body does; `bob` is the body riding its own wingbeat.
BEAT = (
    # wing  kick  bob
    (66.0, 0.0, 1),     # top of the stroke
    (30.0, 1.0, 0),     # driving down
    (-18.0, 0.0, -1),   # bottom of the stroke
    (34.0, -1.0, 0),    # recovering
)

# Where the body sits, before the bob. The collision box an enemy occupies is the left 28 columns
# of its frame, inset by its tolerance, so the body is centered on that rather than on the frame -
# the wings can have the four columns on the right to themselves.
CENTER = (14.0, 15.6)

TYPES = (
    # SCOUT: the smallest, horns swept straight back. Reads as quick, which is what it is.
    dict(key="v", body=(6.0, 7.0), horns="swept", wing=12.0, lid=0.25, teeth=2, spines=0),
    # WEAVER: round and pleased with itself - a bull's horns, the widest grin, eye wide open.
    dict(key="a", body=(6.7, 7.4), horns="bull", wing=12.4, lid=None, teeth=3, spines=0),
    # STRIKER, and the boss: a crown of horns, a heavy scowl, a jaw of teeth and a spined back.
    dict(key="c", body=(6.9, 7.8), horns="crown", wing=12.4, lid=-0.5, teeth=4, spines=3),
)

# Three ramps of five tones each, plus the shared parts. Same structure every time - shadow, body,
# lit side, light, and the shine on top - so the three read as one species in three tempers.
PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (14, 8, 26, 255),      # outline, near-black and slightly violet
    "O": (26, 14, 40, 255),     # inner outline, where a wing or a horn crosses the body
    # violet
    "v0": (56, 24, 96, 255),
    "v1": (100, 42, 160, 255),
    "v2": (150, 76, 214, 255),
    "v3": (196, 132, 244, 255),
    "v4": (236, 206, 255, 255),
    # amber
    "a0": (96, 42, 12, 255),
    "a1": (160, 84, 20, 255),
    "a2": (220, 136, 34, 255),
    "a3": (248, 192, 90, 255),
    "a4": (255, 238, 184, 255),
    # crimson
    "c0": (84, 16, 34, 255),
    "c1": (148, 30, 52, 255),
    "c2": (206, 56, 70, 255),
    "c3": (244, 122, 118, 255),
    "c4": (255, 204, 196, 255),
    # horn, old bone
    "h0": (118, 88, 64, 255),
    "h1": (196, 166, 124, 255),
    "h2": (244, 228, 192, 255),
    # the mouth, and the teeth in it
    "m": (40, 6, 22, 255),
    "t": (252, 246, 232, 255),
    # the eye: the one cold thing on a warm body, so it reads as a lamp rather than a pupil
    "e": (226, 252, 255, 255),
    "i": (96, 222, 250, 255),
    "p": (16, 10, 28, 255),
}

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "enemies.png"


def raster(shape):
    return pa.rasterize(shape, FRAME_WIDTH, FRAME_HEIGHT)


def paint(grid, pixels, material):
    for x, y in pixels:
        grid[y][x] = material


def along(points, radii):
    """A tapered stroke through [points], thick to thin: a horn, a leg."""
    return pa.union(*(
        pa.capsule(p, q, r0, r1)
        for p, q, r0, r1 in zip(points, points[1:], radii, radii[1:])
    ))


def shade_ball(grid, pixels, center, radii, key):
    """
    Shades [pixels] as a lit ball: light from the front and above, which is where the player is.

    Banded by distance from the lit point rather than top to bottom, so the body reads as round -
    a top-to-bottom gradient is how the old drones got the flat look of a fish's flank.
    """
    cx, cy = center
    rx, ry = radii
    lx, ly = cx - rx * 0.38, cy - ry * 0.42
    for x, y in pixels:
        d = hypot((x + 0.5 - lx) / rx, (y + 0.5 - ly) / ry)
        tone = 3 if d < 0.42 else 2 if d < 0.86 else 1 if d < 1.22 else 0
        grid[y][x] = f"{key}{tone}"


def wing(root, angle_degrees, length, far):
    """
    One devil wing, as a polygon: a leading edge out to a hooked tip, and a trailing edge cut into
    sharp points - where the bat's wings scallop between their fingers, these spike.

    Built pointing along +u with its leading edge on +v, then turned to [angle_degrees] above
    straight back. Below level it is mirrored, so the leading edge stays at the front of the stroke
    on the way down as well as up.
    """
    shape = [
        (0.0, 0.0),
        (0.46, 0.20),
        (1.00, 0.16),   # the tip, hooked forward
        (0.70, -0.12),
        (0.72, -0.46),  # second point
        (0.42, -0.30),
        (0.26, -0.56),  # third point
        (0.06, -0.30),
    ]
    theta = radians(angle_degrees)
    flip = 1.0 if angle_degrees >= 0 else -1.0
    ux, uy = cos(theta), -sin(theta)
    vx, vy = -sin(theta) * flip, -cos(theta) * flip
    scale = length * (0.86 if far else 1.0)
    points = [
        (root[0] + (u * ux + v * vx) * scale, root[1] + (u * uy + v * vy) * scale)
        for u, v in shape
    ]
    return pa.polygon(points), (points[0], points[2])


def horns(kind, center, radii):
    """
    The horns for one temper, back to front, as (points, radii) strokes from base to tip.

    Few and far apart: horns bunched together read as a tuft of hair at this size, which is what
    the first pass of these got.
    """
    cx, cy = center
    rx, ry = radii
    top = cy - ry
    if kind == "swept":
        # Laid straight back: nothing on a scout catches the wind.
        return [
            ([(cx + 2.6, top + 1.8), (cx + 4.6, top - 0.8), (cx + 7.8, top - 2.2)], [1.4, 1.0, 0.3]),
            ([(cx - 2.0, top + 1.6), (cx - 0.6, top - 2.2), (cx + 2.8, top - 4.4)], [1.6, 1.1, 0.3]),
        ]
    if kind == "bull":
        # Out to the sides and hooked up at the ends.
        return [
            ([(cx + 3.4, top + 2.4), (cx + 6.4, top + 0.4), (cx + 7.6, top - 3.2)], [1.5, 1.1, 0.3]),
            ([(cx - 3.4, top + 2.4), (cx - 6.2, top + 0.2), (cx - 6.8, top - 3.6)], [1.7, 1.2, 0.3]),
        ]
    # A crown: long, sharp, and raked forward toward whatever it is looking at.
    return [
        ([(cx + 3.6, top + 2.2), (cx + 5.6, top - 1.6), (cx + 7.6, top - 3.6)], [1.4, 0.9, 0.3]),
        ([(cx - 0.2, top + 1.2), (cx - 0.6, top - 3.0), (cx - 0.2, top - 5.6)], [1.6, 1.0, 0.3]),
        ([(cx - 3.4, top + 2.4), (cx - 5.4, top - 2.0), (cx - 5.6, top - 5.2)], [1.5, 0.9, 0.3]),
    ]


def shade_along(grid, pixels, points):
    """Shades a horn from its base, in shadow against the head, out to a tip catching the light."""
    segments = list(zip(points, points[1:]))
    lengths = [hypot(q[0] - p[0], q[1] - p[1]) for p, q in segments]
    total = sum(lengths)
    for x, y in pixels:
        px, py = x + 0.5, y + 0.5
        best, where = float("inf"), 0.0
        start = 0.0
        for (p, q), length in zip(segments, lengths):
            dx, dy = q[0] - p[0], q[1] - p[1]
            t = max(0.0, min(1.0, ((px - p[0]) * dx + (py - p[1]) * dy) / (length * length)))
            d = hypot(px - p[0] - dx * t, py - p[1] - dy * t)
            if d < best:
                best, where = d, (start + t * length) / total
            start += length
        grid[y][x] = "h0" if where < 0.30 else "h1" if where < 0.68 else "h2"


def feet(center, radii, kick):
    """
    Two short legs hanging under the body, swinging a little with the wingbeat, and the claws on
    the end of them - legs and claws being the plainest way there is to say "not a fish".

    Returns the legs and the claws separately, because the claws are drawn in bone.
    """
    cx, cy = center
    rx, ry = radii
    legs, claws = [], []
    for dx, lag in ((-2.4, 0.0), (1.6, 0.6)):
        hip = (cx + dx, cy + ry * 0.84)
        ankle = (hip[0] + 0.4 + kick * (0.4 + lag * 0.4), hip[1] + 2.4)
        legs.append(along([hip, ankle], [1.3, 0.9]))
        for toe in (-1.3, 0.3):
            claws.append(pa.capsule(ankle, (ankle[0] + toe, ankle[1] + 1.5), 0.6, 0.3))
    return pa.union(*legs), pa.union(*claws)


def render(type_index, frame_index):
    spec = TYPES[type_index]
    key = spec["key"]
    wing_angle, kick, bob = BEAT[frame_index]
    grid = pa.blank(FRAME_WIDTH, FRAME_HEIGHT)

    center = (CENTER[0], CENTER[1] + bob)
    rx, ry = spec["body"]
    cx, cy = center

    # Back to front: the far wing, the legs, the body, the near wing, and the face and horns over
    # everything - the face is what the player reads, so nothing is allowed to cover it.
    root = (cx + rx * 0.48, cy - ry * 0.44)
    far_shape, _ = wing((root[0] + 1.4, root[1] - 1.2), wing_angle + 10.0, spec["wing"], far=True)
    far = raster(far_shape)
    pa.shade_bands(grid, far, ((0.55, f"{key}0"), (1.01, f"{key}1")))

    leg_shape, claw_shape = feet(center, (rx, ry), kick)
    claw_pixels = raster(claw_shape)
    paint(grid, claw_pixels, "h1")
    leg_pixels = raster(leg_shape)
    paint(grid, leg_pixels, f"{key}1")
    feet_pixels = leg_pixels | claw_pixels

    body = raster(pa.ellipse(cx, cy, rx, ry))
    shade_ball(grid, body, center, (rx, ry), key)

    # Spines down the back, for the one that becomes the boss.
    spines = set()
    for n in range(spec["spines"]):
        a = radians(8.0 - n * 30.0)
        base = (cx + rx * cos(a), cy - ry * sin(a))
        out = (cos(a) * 3.4, -sin(a) * 3.4)
        perp = (-out[1] * 0.42, out[0] * 0.42)
        spines |= raster(pa.polygon([
            (base[0] - out[0] * 0.3 + perp[0], base[1] - out[1] * 0.3 + perp[1]),
            (base[0] + out[0], base[1] + out[1]),
            (base[0] - out[0] * 0.3 - perp[0], base[1] - out[1] * 0.3 - perp[1]),
        ]))
    spines -= body
    paint(grid, spines, f"{key}1")

    near_shape, (near_root, near_tip) = wing(root, wing_angle, spec["wing"], far=False)
    near = raster(near_shape)
    pa.shade_bands(grid, near, ((0.40, f"{key}1"), (1.01, f"{key}2")))
    # The wing's arm, a ridge along its leading edge.
    ridge = raster(pa.capsule(near_root, near_tip, 0.7, 0.4)) & near
    paint(grid, ridge, f"{key}3")

    # Back horn first, so the front one is drawn over it and outlined against it.
    horn_pixels = set()
    for points, radii in horns(spec["horns"], center, (rx, ry)):
        pixels = raster(along(points, radii)) - body
        pa.outline_against(grid, horn_pixels - pixels, pixels, color="O")
        horn_pixels = (horn_pixels - pixels) | pixels
        shade_along(grid, pixels, points)

    # Depth, innermost first, so nothing eats into the shape in front of it.
    back = far | feet_pixels | spines
    pa.outline_against(grid, back - body - near - horn_pixels, body | near | horn_pixels, color="O")
    pa.outline_against(grid, near - horn_pixels, body | horn_pixels, color="O")
    pa.outline_against(grid, horn_pixels - body, body, color="O")

    # The shine, a pixel or two where the light lands.
    shine_x, shine_y = round(cx - rx * 0.42), round(cy - ry * 0.5)
    for x in (shine_x, shine_x + 1):
        if (x, shine_y) in body and grid[shine_y][x] == f"{key}3":
            grid[shine_y][x] = f"{key}4"

    face(grid, spec, center, (rx, ry))
    pa.outer_outline(grid, FRAME_WIDTH, FRAME_HEIGHT)
    return grid


def face(grid, spec, center, radii):
    """One eye and a grin, turned toward the player - which, for everything here, is to the left."""
    cx, cy = center
    rx, ry = radii
    ex, ey = cx - rx * 0.30, cy - ry * 0.18
    erx, ery = rx * 0.46, ry * 0.44

    eye = raster(pa.ellipse(ex, ey, erx, ery))
    lid = spec["lid"]
    if lid is not None:
        # The upper lid, cut straight across: level it looks sly, tipped down toward the front it
        # scowls. What it covers is lid, not eye, so it goes back to the body's own shading.
        def under_lid(x, y):
            return y + 0.5 > ey - ery * 0.34 + lid * (x + 0.5 - ex)

        covered = {p for p in eye if not under_lid(*p)}
        eye -= covered
        brow = {p for p in eye if any(n in covered for n in pa.neighbors(*p))}
        eye -= brow
        paint(grid, brow, "o")
    paint(grid, eye, "e")
    # A cold iris looking straight at the player, and a slit down the middle of it.
    iris = raster(pa.ellipse(ex - erx * 0.30, ey + ery * 0.08, erx * 0.62, ery * 0.66)) & eye
    paint(grid, iris, "i")
    pupil = raster(pa.ellipse(ex - erx * 0.34, ey + ery * 0.10, 0.62, ery * 0.52)) & eye
    paint(grid, pupil, "p")

    # The grin: the bottom of an ellipse, tipped up at the back into a smirk.
    mx, my = cx - rx * 0.26, cy + ry * 0.44
    mrx, mry = rx * 0.56, ry * 0.26

    def grin(x, y):
        dx, dy = (x - mx) / mrx, (y - my) / mry
        return dx * dx + dy * dy <= 1.0 and y >= my - 0.4 - 0.35 * (x - mx)

    mouth = raster(grin)
    paint(grid, mouth, "m")
    # Teeth along the top edge, hanging into the dark of the mouth, spaced evenly and clear of the
    # corners. The angrier the temper, the more of them there are.
    top = {}
    for x, y in mouth:
        top[x] = min(top.get(x, y), y)
    columns = sorted(top)[1:-1]
    for n in range(spec["teeth"]):
        x = columns[round((n + 0.5) * (len(columns) - 1) / spec["teeth"])]
        grid[top[x]][x] = "t"


def main() -> int:
    # Laid out type by type, each type's frames contiguous, which is what srcXOf plus the
    # animation's frame stride walks.
    grids = [
        render(type_index, frame_index)
        for type_index in range(TYPE_COUNT)
        for frame_index in range(FRAME_COUNT)
    ]
    last_x, last_y = FRAME_WIDTH - 1, FRAME_HEIGHT - 1
    for index, grid in enumerate(grids):
        if any(
            grid[y][x] not in (pa.TRANSPARENT, pa.OUTLINE)
            for y in range(FRAME_HEIGHT)
            for x in range(FRAME_WIDTH)
            if x in (0, last_x) or y in (0, last_y)
        ):
            print(f"frame {index} runs off the edge of its frame", file=sys.stderr)
            return 1
    sheet = pa.save_sheet(OUTPUT, grids, PALETTE, FRAME_WIDTH, FRAME_HEIGHT)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {TYPE_COUNT} types x {FRAME_COUNT} frames)")
    print(f"strip offsets: {[i * FRAME_WIDTH * FRAME_COUNT for i in range(TYPE_COUNT)]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
