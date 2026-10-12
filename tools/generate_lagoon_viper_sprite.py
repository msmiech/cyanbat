# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the Sky Viper's sheet: its head, its wings, its body, its tail, and the venom it bites with.

    uv run tools/generate_lagoon_viper_sprite.py

The lagoon's second boss, which nothing on the stage select gives away: once the Naga falls, a
winged serpent comes down out of the sky, bites the bat, and the poisoned bat has it to fight. It is
built like the Naga and the Sand Wyrm - a head, a pair of wings and a body of ten parts, each its own
entity, which the game lays along the path the head has flown - so this sheet is pieces of an animal,
each in its own 80x80 frame:

* **0-1** the head, its jaws shut and then gaping, fangs bared and dripping venom. A viper's wedge
  under a frill of spines, facing left like every hostile; the game turns it along its flight.
* **2-5** its wings, four frames of a beat - up, spread, down, and folding back up - from a shoulder
  in the middle of the frame, where the game sets them on its neck, swept back from there.
* **6** the neck, the thickest of it, **7** the body, **8** a lesser part, **9** a small one, and
  **10** the tail, ending in a barbed spade. Drawn lying left to right, the front to the left.
* **11-14** venom bursting where it bites: a spurt, a splash, a spray of drops, and the last of them.

Everything but the wings is drawn the Naga's size, in a 64-pixel square in the middle of its frame;
the wings need the whole frame to spread in. Every part keeps the same pixel size, as on every boss's
sheet, and the game sets each part's hit box in from its frame by how much of the frame it leaves
empty.

**Hostiles are warm**, and this one is banded the way the deadliest snakes are: coral red, ringed in
black edged with gold, so it reads as poison before it bites. Its wings are a warm, dark membrane on
black bones. Its venom is the green of the venom elites' fire, which the fight's bolts are drawn in.

The row is drawn three times over, top to bottom: unhurt, wounded and battered (`WoundComponent`), the
head carrying the health and the brain drawing the rest from its row. A wound splits its scales onto
the venom inside, so its cracks run green, and holes a wing; battered, its frill is torn and a spine
snapped, its eye is screwed half shut, its wings are holed again and their edges torn, and its tail's
barb is snapped. The venom is never hurt, and is drawn in the top row only.
"""

import pathlib
import sys
from math import cos, pi, radians, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME = 80
FRAMES = 15

# The square every part but the wings is drawn on, set in the middle of its frame.
PART = 64
INSET = (FRAME - PART) // 2

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (22, 8, 10, 255),
    "O": (52, 14, 18, 255),
    # coral red scales, lit along the top
    "r0": (104, 18, 24, 255),
    "r1": (172, 34, 34, 255),
    "r2": (224, 68, 46, 255),
    "r3": (252, 128, 84, 255),
    # the black rings
    "k0": (24, 14, 18, 255),
    "k1": (56, 34, 40, 255),
    "k2": (96, 66, 70, 255),
    # the gold that edges them
    "y0": (184, 122, 32, 255),
    "y1": (242, 192, 66, 255),
    "y2": (255, 234, 150, 255),
    # its wings' membrane
    "m0": (88, 22, 30, 255),
    "m1": (150, 44, 44, 255),
    "m2": (208, 92, 62, 255),
    # its venom
    "v0": (48, 120, 26, 255),
    "v1": (116, 238, 60, 255),
    "v2": (214, 255, 140, 255),
    # its mouth and fangs
    "m": (96, 14, 32, 255),
    "t": (252, 240, 222, 255),
    # the cold eye every hostile has
    "e": (226, 252, 255, 255),
    "p": (16, 10, 28, 255),
}

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "lagoonViper.png"

# Its scales, lit from above: the top of every part catches the light, the underside is in shadow.
SCALES = ((0.18, "r3"), (0.42, "r2"), (0.74, "r1"), (1.01, "r0"))
RING = ((0.22, "k2"), (0.5, "k1"), (1.01, "k0"))
GOLD = ((0.3, "y2"), (0.6, "y1"), (1.01, "y0"))


def raster(shape, size=PART):
    return pa.rasterize(shape, size, size)


def paint(grid, pixels, key):
    for x, y in pixels:
        grid[y][x] = key


def framed(grid):
    """A part drawn on its [PART] square, set in the middle of a whole frame."""
    out = pa.blank(FRAME, FRAME)
    for y, row in enumerate(grid):
        for x, key in enumerate(row):
            out[y + INSET][x + INSET] = key
    return out


def venom_crack(grid, points, within, radius=0.6):
    """A split onto the venom inside it: a dark-green line with a bright core."""
    paint(grid, raster(pa.crack(points, radius + 0.35)) & within, "v0")
    paint(grid, raster(pa.crack(points, radius * 0.5)) & within, "v1")


# --- the head ------------------------------------------------------------------------------------


def head(frame, wounds=0):
    """
    A viper's head facing left: a broad wedge, wide at the jaw's hinge and narrowing to a blunt snout,
    coral red with a black mask over its eyes and snout and a gold collar behind it. A frill of four
    spines fans back off its crown, webbed in its wings' membrane. A short stub of neck leaves it on the
    right at its middle, rounded off, which the game sets over the first part of its body.

    Its jaws are shut in the first frame, a bead of venom on the fang; in the second they gape, the
    fangs bared and running with it.
    """
    grid = pa.blank(PART, PART)
    gape = frame == 1
    cy = 32.0

    # The frill first, so the skull covers its roots.
    frill = set()
    spines = ((24.0, (30.0, 8.0)), (30.0, (40.0, 7.0)), (35.0, (48.0, 10.0)), (39.0, (55.0, 16.0)))
    for index, (base_x, tip) in enumerate(spines):
        if wounds >= 2 and index == 2:
            # Snapped off near its root.
            tip = ((base_x + tip[0]) / 2.0 - 1.0, (24.0 + tip[1]) / 2.0 + 2.0)
        frill |= raster(pa.capsule((base_x, 24.0), tip, 1.6, 0.7))
    web = raster(pa.polygon([(22.0, 26.0), (30.0, 9.0), (40.0, 8.0), (48.0, 11.0), (55.0, 17.0), (46.0, 24.0), (40.0, 27.0)]))
    if wounds >= 1:
        web -= raster(pa.polygon(pa.notch((48.0, 11.0), (55.0, 17.0), (42.0, 22.0), 0.45)))
    if wounds >= 2:
        web -= raster(pa.polygon(pa.ring((36.0, 16.0), 2.2)))
    web -= frill
    pa.shade_bands(grid, web, ((0.45, "m2"), (0.8, "m1"), (1.01, "m0")))
    pa.shade_bands_across(grid, frill, ((0.5, "k2"), (1.01, "k1")))

    # The stub of neck, rounded off behind a gold collar.
    neck = raster(pa.capsule((38.0, cy + 1.0), (52.0, cy + 1.0), 10.5, 9.5))
    pa.shade_bands(grid, neck, SCALES)
    collar = {(x, y) for x, y in neck if 44 <= x <= 51}
    pa.shade_bands(grid, collar, GOLD)
    paint(grid, {(x, y) for x, y in neck if 46 <= x <= 49}, "k1")

    # The skull: wide at the hinge, narrowing to the snout.
    skull = raster(pa.union(
        pa.polygon([(5.0, 27.0), (14.0, 22.0), (30.0, 21.0), (42.0, 23.0), (46.0, 32.0), (42.0, 38.0), (24.0, 36.0), (6.0, 35.0)]),
        pa.ellipse(40.0, cy, 7.0, 10.0),
    ))
    pa.shade_bands(grid, skull, SCALES)
    # The black mask over its eyes and snout, edged in gold behind.
    mask = {(x, y) for x, y in skull if x < 26 and y < 31}
    pa.shade_bands(grid, mask, RING)
    paint(grid, {(x, y) for x, y in skull if 26 <= x <= 28 and y < 34}, "y1")
    # A few scales across the crown.
    for sx in (30, 35):
        paint(grid, raster(pa.capsule((sx, 22.5), (sx + 2.0, 27.5), 0.45, 0.45)) & skull, "r1")

    if gape:
        mouth = raster(pa.polygon([(4.0, 34.0), (24.0, 35.0), (38.0, 38.0), (26.0, 44.0), (8.0, 44.0)]))
        jaw = raster(pa.polygon([(6.0, 43.0), (26.0, 44.0), (38.0, 38.0), (42.0, 41.0), (30.0, 49.0), (9.0, 48.0)]))
        paint(grid, mouth, "m")
        paint(grid, raster(pa.ellipse(18.0, 39.0, 6.0, 2.6)) & mouth, "r0")
        pa.shade_bands(grid, jaw, ((0.5, "r1"), (1.01, "r0")))
        # The fangs, long and bared, venom running off them and dripping.
        fangs = raster(pa.capsule((8.0, 34.5), (9.5, 41.5), 1.1, 0.4)) | raster(pa.capsule((14.0, 35.0), (15.0, 40.5), 0.9, 0.35))
        paint(grid, fangs, "t")
        paint(grid, {(9, 41), (9, 42), (15, 40)}, "v1")
        drops = raster(pa.ellipse(9.5, 47.0, 1.3, 1.8)) | raster(pa.ellipse(15.0, 44.0, 0.9, 1.3))
        paint(grid, drops - jaw, "v1")
        paint(grid, {(9, 46)}, "v2")
        pa.outline_against(grid, jaw, mouth, color="O")
    else:
        jaw = raster(pa.polygon([(6.0, 34.0), (24.0, 35.0), (40.0, 37.0), (42.0, 40.0), (26.0, 41.0), (8.0, 40.0)]))
        pa.shade_bands(grid, jaw, ((0.5, "r1"), (1.01, "r0")))
        paint(grid, raster(pa.capsule((5.5, 34.6), (38.0, 37.0), 0.5, 0.5)), "o")
        # A fang over the lip, a bead of venom on its point.
        paint(grid, {(9, 35), (9, 36)}, "t")
        paint(grid, {(9, 37)}, "v1")
        # Its tongue, flicking out forked.
        paint(grid, raster(pa.capsule((5.0, 35.5), (0.8, 36.5), 0.45, 0.4)) | {(0, 35), (0, 38)}, "v1")

    if wounds >= 1:
        venom_crack(grid, [(34.0, 21.5), (32.2, 26.0), (35.0, 29.0), (32.6, 33.0)], skull - mask)
        venom_crack(grid, [(51.0, 25.5), (49.4, 29.0), (51.4, 32.0)], neck - collar)
    if wounds >= 2:
        venom_crack(grid, [(43.0, 24.0), (40.4, 29.0), (43.6, 33.0), (40.6, 37.0)], skull)
        venom_crack(grid, [(53.0, 33.0), (51.0, 37.0), (53.4, 40.0)], neck - collar)

    pa.outline_against(grid, skull | jaw, (neck | web | frill) - skull - jaw, color="O")
    eye(grid, 17.0, 27.0, 2.6, 2.1, wounds)
    pa.outer_outline(grid, PART, PART)
    return framed(grid)


def eye(grid, cx, cy, rx, ry, wounds):
    """The cold eye, ringed in venom green. Battered, it is screwed half shut."""
    if wounds >= 2:
        cy, ry = cy + ry * 0.35, ry * 0.6
    paint(grid, raster(pa.ellipse(cx, cy, rx + 1.0, ry + 0.9)), "v0")
    paint(grid, raster(pa.ellipse(cx, cy, rx, ry)), "e")
    paint(grid, raster(pa.ellipse(cx - 0.6, cy + 0.1, rx * 0.35, ry * 0.75)), "p")


# --- the wings -----------------------------------------------------------------------------------

# Each frame of the beat: where the near wing's leading bone points, in degrees from straight back
# (to the right), negative upward; and how far it reaches, as a share of its full length.
BEAT = ((-80.0, 1.0), (-34.0, 1.0), (56.0, 0.95), (-52.0, 0.8))

# The shoulder the wings beat from, the middle of the frame, and how far the leading bone reaches.
ROOT = (FRAME / 2.0, FRAME / 2.0)
WING_LENGTH = 38.0

# The fingers behind the leading bone: how far round toward the body each is turned from it, in
# degrees, and how long it is against it.
FINGERS = ((0.0, 1.0), (34.0, 0.92), (66.0, 0.8), (98.0, 0.62))

# How close to the line of the body, in degrees, the last finger of a wing comes.
BODY_CLEARANCE = 7.0


def wing(angle, reach, wounds, near):
    """
    One wing from the shoulder: a leading bone and three fingers fanning back from it toward the body,
    the membrane between them hanging in scallops along its trailing edge and meeting the body behind
    the shoulder. Returns its membrane and its bones.
    """
    rx, ry = ROOT
    length = WING_LENGTH * reach
    # A raised wing fans back down toward the body, a lowered one back up toward it.
    toward = 1.0 if angle < 0 else -1.0
    tips = []
    bones = []
    for index, (turn, share) in enumerate(FINGERS):
        # Never round past the body: a finger that would cross it lies along it instead.
        turned = angle + toward * turn
        turned = min(turned, -BODY_CLEARANCE) if angle < 0 else max(turned, BODY_CLEARANCE)
        a = radians(turned)
        tip = (rx + cos(a) * length * share, ry + sin(a) * length * share)
        tips.append(tip)
        bones.append(pa.capsule((rx, ry), tip, 1.6 if index == 0 else 1.0, 0.6))
    # Where the membrane meets the body: behind the shoulder, on the wing's side of it.
    heel = (rx + 24.0, ry - toward * 2.0)
    edge = [(rx, ry), tips[0]]
    for a, b in zip(tips, tips[1:] + [heel]):
        # Bowed in toward the shoulder between bones, as a membrane hangs.
        mid = ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)
        control = (mid[0] + (rx - mid[0]) * 0.2, mid[1] + (ry - mid[1]) * 0.2)
        edge += pa.bezier(a, control, b, steps=6) + [b]
    membrane = raster(pa.polygon(edge), FRAME)
    if wounds >= 1 and near:
        hole = (tips[1][0] * 0.55 + rx * 0.45, tips[1][1] * 0.55 + ry * 0.45)
        membrane -= raster(pa.polygon(pa.ring(hole, 2.4)), FRAME)
    if wounds >= 2:
        hole = (tips[2][0] * 0.6 + tips[3][0] * 0.2 + rx * 0.2, tips[2][1] * 0.6 + tips[3][1] * 0.2 + ry * 0.2)
        membrane -= raster(pa.polygon(pa.ring(hole, 2.0)), FRAME)
        membrane -= raster(pa.polygon(pa.notch(tips[0], tips[1], (rx, ry), 0.35)), FRAME)
    return membrane, raster(pa.union(*bones), FRAME)


def wings(frame, wounds=0):
    """
    The pair from the side: the far wing a little behind and darker, the near one over it, both swept
    back from the shoulder and beating together.
    """
    grid = pa.blank(FRAME, FRAME)
    angle, reach = BEAT[frame]

    def shifted(pixels):
        return {(x + 6, y - 3) for x, y in pixels if 0 <= x + 6 < FRAME and 0 <= y - 3 < FRAME}

    far_membrane, far_bones = (shifted(part) for part in wing(angle - 9.0, reach * 0.86, wounds, near=False))
    paint(grid, far_membrane, "m0")
    paint(grid, far_bones, "k0")
    membrane, bones = wing(angle, reach, wounds, near=True)
    # Lit along the edge that leads it into the light, darkening toward the body.
    lit = ((0.3, "m2"), (0.7, "m1"), (1.01, "m0")) if angle < 0 else ((0.3, "m0"), (0.7, "m1"), (1.01, "m2"))
    pa.shade_bands(grid, membrane, lit)
    pa.shade_bands_across(grid, bones, ((0.5, "k2"), (1.01, "k1")))
    pa.outline_against(grid, membrane | bones, far_membrane - membrane - bones, color="O")
    # The shoulder it beats from, a knot of gold.
    shoulder = raster(pa.ellipse(ROOT[0], ROOT[1], 3.6, 3.6), FRAME)
    pa.shade_bands(grid, shoulder, GOLD)
    pa.outer_outline(grid, FRAME, FRAME)
    return grid


# --- the body ------------------------------------------------------------------------------------


def section(thickness, wounds=0):
    """
    A length of its body lying left to right, the front to the left: coral red, lit along its back,
    with a black ring edged in gold round its middle - so the game, overlapping the parts, bands the
    whole body red, gold, black, gold, red.
    """
    grid = pa.blank(PART, PART)
    cy = 32.0
    reach = thickness * 1.15
    body = raster(pa.capsule((32.0 - reach, cy), (32.0 + reach, cy), thickness, thickness))
    pa.shade_bands(grid, body, SCALES)
    # A diamond of scales along its back.
    paint(grid, {(x, y) for x, y in body if grid[y][x] in ("r2", "r1") and (x + y) % 5 == 0}, "r1")
    ring_width = max(2.0, thickness * 0.42)
    gold = {(x, y) for x, y in body if abs(x - 32.0) <= ring_width + 1.5}
    black = {(x, y) for x, y in body if abs(x - 32.0) <= ring_width}
    pa.shade_bands(grid, gold, GOLD)
    pa.shade_bands(grid, black, RING)
    if wounds >= 1:
        venom_crack(grid, [(24.0, cy - thickness * 0.8), (22.4, cy - 1.0), (24.6, cy + thickness * 0.5)], body, 0.5)
    if wounds >= 2:
        venom_crack(grid, [(40.0, cy - thickness * 0.7), (38.0, cy + 0.6), (40.6, cy + thickness * 0.6)], body, 0.5)
        notch = raster(pa.polygon(pa.notch((46.0, cy - thickness), (37.0, cy - thickness), (41.0, cy), 0.35, width=0.6)))
        paint(grid, notch & body, ".")
    pa.outer_outline(grid, PART, PART)
    return framed(grid)


def tail(wounds=0):
    """The tail, narrowing to the right and ending in a barbed spade of gold, a black ring before it."""
    grid = pa.blank(PART, PART)
    cy = 32.0
    body = set()
    for i in range(40):
        t = i / 39.0
        body |= raster(pa.ellipse(8.0 + i, cy + 2.0 * t * t, 1.2, max(1.0, 7.5 * (1.0 - t) ** 0.7)))
    pa.shade_bands(grid, body, SCALES)
    black = {(x, y) for x, y in body if 18 <= x <= 22}
    pa.shade_bands(grid, black, RING)
    if wounds >= 2:
        # Its barb snapped off, leaving a stub.
        barb = raster(pa.polygon([(46.0, cy + 1.0), (50.0, cy - 2.0), (51.0, cy + 4.0)]))
    else:
        barb = raster(pa.polygon([(45.0, cy + 1.5), (52.0, cy - 5.0), (61.0, cy + 2.5), (52.0, cy + 9.0), (54.0, cy + 2.5)]))
    barb -= body
    pa.shade_bands(grid, barb, GOLD)
    if wounds >= 1:
        venom_crack(grid, [(28.0, cy - 4.0), (26.6, cy), (28.4, cy + 3.6)], body, 0.45)
    pa.outline_against(grid, barb, body, color="O")
    pa.outer_outline(grid, PART, PART)
    return framed(grid)


# --- the venom -----------------------------------------------------------------------------------


def venom(frame):
    """
    Venom bursting where it bites, centered in the frame: a spurt, a splash of it with drops flying,
    the drops spread wide, and the last of them falling.
    """
    grid = pa.blank(FRAME, FRAME)
    c = FRAME / 2.0
    shapes = []
    if frame == 0:
        shapes = [pa.ellipse(c, c, 7.0, 6.0), pa.ellipse(c - 7.0, c - 4.0, 3.4, 2.8), pa.ellipse(c + 6.0, c + 4.0, 3.0, 2.6)]
        drops = [(c - 13.0, c - 8.0, 1.8), (c + 12.0, c - 6.0, 1.6), (c - 2.0, c + 12.0, 1.5)]
    elif frame == 1:
        shapes = [pa.ellipse(c, c, 10.0, 8.0)]
        for i in range(7):
            a = 2.0 * pi * (i + 0.3) / 7.0
            shapes.append(pa.capsule((c, c), (c + cos(a) * 19.0, c + sin(a) * 16.0), 3.4, 1.4))
        drops = [(c + cos(2.0 * pi * i / 5.0 + 0.6) * 26.0, c + sin(2.0 * pi * i / 5.0 + 0.6) * 22.0, 2.0) for i in range(5)]
    elif frame == 2:
        shapes = [pa.ellipse(c, c + 2.0, 7.0, 5.0)]
        drops = [(c + cos(2.0 * pi * i / 9.0 + 0.2) * (22.0 + 7.0 * (i % 2)),
                  c + 2.0 + sin(2.0 * pi * i / 9.0 + 0.2) * (19.0 + 6.0 * (i % 3)), 2.4 - 0.4 * (i % 2)) for i in range(9)]
    else:
        shapes = [pa.ellipse(c, c + 7.0, 3.4, 2.2)]
        drops = [(c + cos(2.0 * pi * i / 8.0 + 0.5) * (29.0 + 4.0 * (i % 2)),
                  c + 9.0 + sin(2.0 * pi * i / 8.0 + 0.5) * (20.0 + 5.0 * (i % 3)), 1.6) for i in range(8)]
    pool = raster(pa.union(*shapes), FRAME)
    for dx, dy, r in drops:
        pool |= raster(pa.ellipse(dx, dy, r, r * 1.2), FRAME)
    pa.shade_bands(grid, pool, ((0.35, "v2"), (0.7, "v1"), (1.01, "v0")))
    pa.outer_outline(grid, FRAME, FRAME, color="v0")
    return grid


def body_row(wounds):
    return [
        head(0, wounds),
        head(1, wounds),
        *(wings(frame, wounds) for frame in range(len(BEAT))),
        section(10.5, wounds),
        section(9.0, wounds),
        section(7.5, wounds),
        section(6.0, wounds),
        tail(wounds),
    ]


def main() -> int:
    rows = [body_row(level) + ([venom(frame) for frame in range(4)] if level == 0 else [None] * 4)
            for level in pa.WOUND_LEVELS]
    sheet = pa.save_rows(OUTPUT, rows, PALETTE, FRAME, FRAME)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {FRAMES} frames x {len(rows)} rows)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
