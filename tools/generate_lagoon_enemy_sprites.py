# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the lagoon's enemy sheet: five hostiles, four frames each.

    uv run tools/generate_lagoon_enemy_sprites.py

As in every stage, each silhouette says what the thing is going to do:

* **Piranha** - a deep, blunt fish all jaw, its underbite full of teeth. It never comes alone; it
  comes as a school.
* **Crab** - a shell with its claws raised, scuttling. The tank, whose shell grows back.
* **Shark** - the one that comes from behind, and so the one drawn **facing right**. It comes in along
  the waterline with only its **fin** showing, so the top eleven rows of its frame are a dorsal fin
  that has to read as *something is coming* on its own, as the wyrmling's crest does.
* **Puffer** - a round fish bristling with spines, puffing itself up through its four frames: it
  throws its spines in a ring.
* **Krait** - a banded sea snake with a paddle for a tail: the Naga's brood. It weaves in a wide S and
  turns to point along it, so it is drawn to read the right way up at any angle it is turned to.

The lagoon is flown from a dark night into a cyan noon, so its hostiles sit in the middle, as the
desert's do: warm, saturated, mid-dark bodies that stand out against the pale, with bright bellies
and highlights that stand out against the dark, and the near-black outline every hostile has.
**Hostiles are warm**, as they are everywhere: coral, crimson, rose-grey and gold - the shark's gray
is a warm one.

Laid out like the other sheets: 32x29 frames, four per type, types left to right in the order
`EnemySpecies` addresses them - 0 PIRANHA, 1 CRAB, 2 SHARK, 3 PUFFER, 4 KRAIT. Everything faces left,
toward the bat, but the shark. The row is drawn three times over, top to bottom: unhurt, wounded and
battered (`WoundComponent`), the wounds following the other stages' rules - fins tear, shells crack,
what sticks out snaps off, battered eyes narrow.
"""

import pathlib
import sys
from math import cos, pi, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME_WIDTH = 32
FRAME_HEIGHT = 29
FRAME_COUNT = 4
TYPE_COUNT = 5

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (18, 8, 14, 255),
    "O": (42, 18, 26, 255),
    # piranha: a bronze-plum back, a scarlet belly, white teeth
    "q0": (62, 30, 40, 255),
    "q1": (118, 62, 62, 255),
    "q2": (178, 104, 80, 255),
    "q3": (226, 156, 112, 255),
    "r0": (176, 36, 40, 255),
    "r1": (240, 84, 62, 255),
    "t": (252, 240, 222, 255),
    # crab: a coral shell, darker claw tips
    "k0": (96, 22, 24, 255),
    "k1": (168, 44, 36, 255),
    "k2": (226, 92, 54, 255),
    "k3": (252, 166, 106, 255),
    # shark: a warm rose-gray back over a pale belly
    "s0": (66, 42, 56, 255),
    "s1": (112, 76, 90, 255),
    "s2": (160, 118, 128, 255),
    "s3": (204, 164, 166, 255),
    "b": (244, 224, 212, 255),
    # puffer: sunlit gold with brown spots and pale spines
    "y0": (150, 82, 22, 255),
    "y1": (216, 140, 36, 255),
    "y2": (250, 200, 80, 255),
    "d": (104, 50, 26, 255),
    "c": (252, 232, 176, 255),
    # krait: coral bands over black
    "n0": (40, 18, 26, 255),
    "n1": (82, 36, 46, 255),
    "a0": (222, 96, 54, 255),
    "a1": (252, 170, 104, 255),
    # the eye, shared with every hostile: the one cold thing on a warm body
    "e": (226, 252, 255, 255),
    "p": (16, 10, 28, 255),
}

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "lagoonEnemies.png"

W, H = FRAME_WIDTH, FRAME_HEIGHT


def raster(shape):
    return pa.rasterize(shape, W, H)


def paint(grid, pixels, key):
    for x, y in pixels:
        grid[y][x] = key


def eye(grid, cx, cy, rx=2.0, ry=1.9, pupil_dx=-0.6, wounds=0):
    """The cold eye every hostile has. Battered, it is screwed half shut, from the top."""
    if wounds >= 2:
        cy, ry = cy + ry * 0.35, ry * 0.6
    paint(grid, raster(pa.ellipse(cx, cy, rx, ry)), "e")
    paint(grid, raster(pa.ellipse(cx + pupil_dx, cy + 0.1, rx * 0.5, ry * 0.55)), "p")


def torn(pixels, *holes):
    """[pixels] with [holes] - polygons, as point lists - torn out of them."""
    for hole in holes:
        pixels = pixels - raster(pa.polygon(hole))
    return pixels


def gash(grid, points, within, key="o", radius=0.55):
    """A crack or a gash along [points], only where it lands on [within]."""
    paint(grid, raster(pa.crack(points, radius)) & within, key)


# --- piranha -------------------------------------------------------------------------------------


def piranha(frame, wounds=0):
    """
    Side on, swimming left: a deep, round-backed body, a blunt head with a jutting lower jaw and a row
    of teeth along it, a scarlet belly, and a tail that sweeps through the four frames. Small, because
    it comes as a school of six.

    Wounded, its tail is notched and its flank gashed; battered, a bite is gone from its back fin and
    a second gash crosses it.
    """
    grid = pa.blank(W, H)
    sweep = (-3.0, -1.0, 3.0, 1.0)[frame]
    cy = 15 + (0, -1, 0, 1)[frame]

    tail_tip_top = (30, cy - 6 + sweep)
    tail_tip_low = (30, cy + 6 + sweep)
    tail = raster(pa.polygon([(21, cy - 1), tail_tip_top, (27, cy + sweep * 0.5), tail_tip_low, (21, cy + 2)]))
    if wounds >= 1:
        tail = torn(tail, pa.notch(tail_tip_top, tail_tip_low, (22, cy), 0.45, width=0.4))
    pa.shade_bands(grid, tail, ((0.5, "q2"), (1.01, "q1")))

    fin = raster(pa.polygon([(12, cy - 6), (17, cy - 10), (20, cy - 5)]))
    if wounds >= 2:
        fin = torn(fin, pa.ring((16.5, cy - 9), 2.2))
    pa.shade_bands(grid, fin, ((0.5, "q1"), (1.01, "q0")))

    body = raster(pa.union(pa.ellipse(14.0, cy, 8.5, 6.5), pa.ellipse(8.0, cy + 1.0, 5.0, 5.0)))
    jaw = raster(pa.polygon([(3.0, cy + 1.5), (9.0, cy + 2.0), (11.0, cy + 6.0), (5.0, cy + 5.0)]))
    whole = body | jaw
    pa.shade_bands(grid, whole, ((0.22, "q0"), (0.45, "q1"), (0.62, "q2"), (0.78, "r1"), (1.01, "r0")))
    # Gold speckles on its flank, and the line where the jaw closes.
    for sx, sy in ((14, cy - 2), (17, cy - 1), (12, cy - 3), (19, cy - 3)):
        paint(grid, {(sx, sy)} & whole, "q3")
    paint(grid, raster(pa.capsule((3.5, cy + 1.6), (9.0, cy + 2.2), 0.45, 0.45)), "o")
    for tx in (4, 6, 8):
        paint(grid, {(tx, int(cy + 1))}, "t")
    if wounds >= 1:
        gash(grid, [(16, cy - 4), (14.4, cy - 1.6), (16.4, cy + 0.4), (14.8, cy + 3.2)], body)
    if wounds >= 2:
        gash(grid, [(19.6, cy - 3.6), (18.2, cy - 1.0), (20.0, cy + 1.6)], body)

    pa.outline_against(grid, tail, body, color="O")
    pa.outline_against(grid, fin, body, color="O")
    eye(grid, 8.6, cy - 2.2, 1.8, 1.8, pupil_dx=-0.5, wounds=wounds)
    pa.outer_outline(grid, W, H)
    return grid


# --- crab ----------------------------------------------------------------------------------------


def crab(frame, wounds=0):
    """
    A broad coral shell with its claws raised toward the bat, its eyes up on stalks and its legs
    scuttling under it - the claws opening and snapping shut through the four frames.

    Wounded, its shell is cracked; battered, cracked again, and its upper claw is broken off at the
    joint.
    """
    grid = pa.blank(W, H)
    bob = (0, 1, 0, 1)[frame]
    cy = 17 + bob
    gape = (2.2, 0.6, 2.2, 0.6)[frame]

    legs = set()
    for index, lx in enumerate((15, 19, 23, 27)):
        step = 1.6 if (frame + index) % 2 else -1.6
        legs |= raster(pa.capsule((lx, cy + 3), (lx + 2 + step, cy + 9), 0.8, 0.55))
    paint(grid, legs, "k1")

    shell = raster(pa.union(pa.ellipse(20.0, cy, 10.0, 6.4), pa.polygon([(10, cy - 1), (12, cy - 5), (14, cy - 2)])))
    if wounds >= 2:
        shell = torn(shell, pa.notch((26, cy - 5.5), (30, cy - 1), (23, cy - 1), 0.4, width=0.5))
    pa.shade_bands(grid, shell, ((0.2, "k3"), (0.45, "k2"), (0.78, "k1"), (1.01, "k0")))
    # The spines along the shell's front edge.
    for sx in (13, 16, 19):
        paint(grid, {(sx, int(cy - 6.0 + abs(sx - 19) * 0.25))}, "k2")
    if wounds >= 1:
        gash(grid, [(20.0, cy - 6.0), (18.8, cy - 3.0), (20.6, cy - 1.2), (19.2, cy + 2.0)], shell)
    if wounds >= 2:
        gash(grid, [(25.4, cy - 4.6), (24.0, cy - 2.2), (25.6, cy + 0.4)], shell)

    def claw(base, elbow, tip_y, broken):
        arm = raster(pa.union(pa.capsule(base, elbow, 1.6, 1.4)))
        if broken:
            paint(grid, arm, "k1")
            paint(grid, raster(pa.ellipse(elbow[0], elbow[1], 1.4, 1.4)), "k0")
            return arm
        hand = raster(pa.ellipse(elbow[0] - 3.0, elbow[1], 3.4, 2.6))
        upper = raster(pa.polygon([(elbow[0] - 4.0, elbow[1] - 1.6), (elbow[0] - 9.0, tip_y - gape), (elbow[0] - 6.0, elbow[1] - 0.4)]))
        lower = raster(pa.polygon([(elbow[0] - 4.0, elbow[1] + 1.4), (elbow[0] - 8.6, tip_y + gape * 0.6 + 1.0), (elbow[0] - 5.6, elbow[1] + 0.4)]))
        pieces = arm | hand | upper | lower
        pa.shade_bands(grid, pieces, ((0.35, "k3"), (0.7, "k2"), (1.01, "k1")))
        paint(grid, (upper | lower) - hand, "k0")
        return pieces

    upper = claw((13, cy - 2), (10, cy - 6), cy - 7, wounds >= 2)
    lower = claw((13, cy + 2), (10, cy + 4), cy + 3, False)

    # The eyes up on their stalks, over the front of the shell.
    paint(grid, raster(pa.capsule((14.6, cy - 5), (14.2, cy - 9), 0.5, 0.5)) | raster(pa.capsule((17.0, cy - 5.5), (17.4, cy - 9.4), 0.5, 0.5)), "k0")
    pa.outline_against(grid, legs - shell, shell, color="O")
    pa.outline_against(grid, (upper | lower) - shell, shell, color="O")
    eye(grid, 14.2, cy - 10.0, 1.3, 1.3, pupil_dx=-0.3, wounds=wounds)
    eye(grid, 17.6, cy - 10.4, 1.3, 1.3, pupil_dx=-0.3, wounds=wounds)
    pa.outer_outline(grid, W, H)
    return grid


# --- shark ---------------------------------------------------------------------------------------


def shark(frame, wounds=0):
    """
    Side on, swimming **right**: a torpedo of a body, rose-gray over a pale belly, a crescent tail
    sweeping through the four frames, and a tall dorsal fin - the whole of the top eleven rows, which
    is all that shows above the water as it cruises in from behind the bat.

    Wounded, its fin is notched and its flank gashed; battered, the fin's tip is torn away, its tail is
    notched and a second gash crosses it.
    """
    grid = pa.blank(W, H)
    sweep = (-2.0, 0.0, 2.0, 0.0)[frame]
    cy = 19 + (0, 0, 1, 1)[frame] * 0

    fin_tip = (15.5, 1.0)
    fin = raster(pa.polygon([(11, 15), (14, 7), fin_tip, (18, 4), (21, 15)]))
    if wounds >= 1:
        fin = torn(fin, pa.notch(fin_tip, (21, 15), (14, 10), 0.45, width=0.4))
    if wounds >= 2:
        fin = torn(fin, pa.ring(fin_tip, 2.6))
    pa.shade_bands_across(grid, fin, ((0.45, "s0"), (1.01, "s1")))

    tail_top = (1, cy - 8 + sweep)
    tail_low = (2, cy + 5 + sweep)
    tail = raster(pa.polygon([(8, cy - 1), tail_top, (5, cy + sweep * 0.4), tail_low, (8, cy + 2)]))
    if wounds >= 2:
        tail = torn(tail, pa.notch(tail_top, tail_low, (7, cy), 0.5, width=0.35))
    pa.shade_bands(grid, tail, ((0.5, "s1"), (1.01, "s0")))

    body = raster(pa.union(pa.ellipse(17.0, cy, 11.0, 4.8), pa.ellipse(26.0, cy - 0.4, 4.6, 3.6), pa.capsule((6, cy), (12, cy), 1.4, 3.0)))
    pa.shade_bands(grid, body, ((0.3, "s1"), (0.5, "s2"), (0.66, "s3"), (1.01, "b")))
    pectoral = raster(pa.polygon([(16, cy + 2), (12, cy + 8), (20, cy + 3)]))
    paint(grid, pectoral - body, "s1")
    # The gills, and a grin under its snout.
    for gx in (20, 22):
        paint(grid, raster(pa.capsule((gx, cy - 2), (gx - 0.6, cy + 1.6), 0.4, 0.4)) & body, "s0")
    paint(grid, raster(pa.capsule((24.0, cy + 2.0), (29.0, cy + 1.2), 0.45, 0.45)) & body, "o")
    for tx in (25, 27):
        paint(grid, {(tx, cy + 3)} & body, "t")
    if wounds >= 1:
        gash(grid, [(14.0, cy - 4.0), (12.8, cy - 1.4), (14.6, cy + 0.6), (13.2, cy + 3.0)], body)
    if wounds >= 2:
        gash(grid, [(18.0, cy - 4.2), (16.8, cy - 1.8), (18.4, cy + 0.8)], body)

    pa.outline_against(grid, fin, body, color="O")
    pa.outline_against(grid, tail, body, color="O")
    eye(grid, 25.4, cy - 1.8, 1.3, 1.3, pupil_dx=0.4, wounds=wounds)
    pa.outer_outline(grid, W, H)
    return grid


# --- puffer --------------------------------------------------------------------------------------


def puffer(frame, wounds=0):
    """
    A round fish bristling with spines, puffing up and letting go through its four frames: gold with
    brown spots, a pale belly, a little beak of a mouth and a big worried eye. It hangs on station and
    throws its spines in a ring.

    Wounded, it is cracked across and two spines are snapped; battered, a chunk is gone from its back
    and it is cracked again.
    """
    grid = pa.blank(W, H)
    radius = (8.0, 9.0, 10.0, 9.0)[frame]
    cx, cy = 16.5, 14.5

    tail = raster(pa.polygon([(cx + radius - 1, cy), (30.5, cy - 4), (30.5, cy + 4)]))
    pa.shade_bands(grid, tail, ((0.5, "y1"), (1.01, "y0")))

    spines = set()
    snapped = {1, 5} if wounds >= 1 else set()
    for index in range(12):
        angle = 2 * pi * index / 12 + 0.2
        if index in snapped or abs(cos(angle) - 1.0) < 0.15:
            continue
        reach = radius + (2.6 if index not in snapped else 0.8)
        spines |= raster(pa.capsule((cx + cos(angle) * (radius - 1), cy + sin(angle) * (radius - 1)),
                                    (cx + cos(angle) * reach, cy + sin(angle) * reach), 0.9, 0.35))
    body = raster(pa.ellipse(cx, cy, radius, radius * 0.92))
    if wounds >= 2:
        body = torn(body, pa.ring((cx + radius * 0.35, cy - radius * 0.85), 2.6))
    paint(grid, spines - body, "c")
    pa.shade_bands(grid, body, ((0.25, "y2"), (0.62, "y1"), (0.78, "y0"), (1.01, "c")))
    # Spots over its back.
    for sx, sy in ((0.2, -0.5), (0.5, -0.2), (-0.1, -0.15), (0.35, 0.15), (0.65, -0.55)):
        paint(grid, raster(pa.ellipse(cx + sx * radius, cy + sy * radius, 1.0, 0.9)) & body, "d")
    if wounds >= 1:
        gash(grid, [(cx + 1, cy - radius * 0.7), (cx - 0.6, cy - 1.0), (cx + 1.2, cy + 1.6), (cx - 0.4, cy + radius * 0.6)], body, "d")
    if wounds >= 2:
        gash(grid, [(cx + radius * 0.6, cy - 2.6), (cx + radius * 0.35, cy + 0.4), (cx + radius * 0.65, cy + 2.8)], body, "d")
    # The beak of a mouth, and a small fin fanning at its side.
    paint(grid, raster(pa.ellipse(cx - radius + 0.6, cy + 1.6, 1.2, 1.0)), "o")
    fin = raster(pa.polygon([(cx + 1, cy + 1), (cx + 5, cy - 1 + frame % 2 * 2), (cx + 5, cy + 3 + frame % 2)]))
    paint(grid, fin & body, "y0")

    pa.outline_against(grid, tail - body, body, color="O")
    eye(grid, cx - radius * 0.45, cy - radius * 0.3, 2.2, 2.2, pupil_dx=-0.7, wounds=wounds)
    pa.outer_outline(grid, W, H)
    return grid


# --- krait ---------------------------------------------------------------------------------------


def krait(frame, wounds=0):
    """
    A sea snake, coral banded over black, swimming left in a wave that runs down its body through the
    four frames, its tail flattened into a paddle. Turned to point along its weave, so it reads by its
    head and its paddle at any angle.

    Wounded, it is gashed and its paddle notched; battered, gashed again and the paddle torn.
    """
    grid = pa.blank(W, H)
    phase = frame * pi / 2.0

    def centerline(x):
        return 14.5 + 2.6 * sin(x * 0.3 - phase) * min(1.0, x / 10.0)

    body = set()
    for x in range(6, 27):
        r = 3.3 - (x - 6) * 0.04
        body |= raster(pa.ellipse(x, centerline(x), 1.4, r))
    paddle_top = (31, centerline(28) - 5)
    paddle_low = (31, centerline(28) + 5)
    paddle = raster(pa.polygon([(25, centerline(25) - 2), paddle_top, paddle_low, (25, centerline(25) + 2)]))
    if wounds >= 1:
        paddle = torn(paddle, pa.notch(paddle_top, paddle_low, (26, centerline(26)), 0.4, width=0.4))
    if wounds >= 2:
        paddle = torn(paddle, pa.ring(paddle_top, 2.4))
    whole = body | paddle
    pa.shade_bands(grid, whole, ((0.35, "a1"), (0.8, "a0"), (1.01, "n1")))
    # Black bands round it, every few pixels down its length.
    for x in range(9, 31, 4):
        band = raster(pa.capsule((x, centerline(min(x, 28)) - 6), (x + 0.4, centerline(min(x, 28)) + 6), 0.9, 0.9))
        paint(grid, band & whole, "n0")
    head = raster(pa.union(pa.ellipse(5.0, centerline(5), 4.4, 3.6), pa.ellipse(1.8, centerline(5) + 0.6, 1.8, 1.8)))
    pa.shade_bands(grid, head, ((0.4, "n1"), (1.01, "n0")))
    paint(grid, raster(pa.capsule((1.0, centerline(5) - 0.6), (3.0, centerline(5) - 1.6), 0.5, 0.5)) & head, "a1")
    if wounds >= 1:
        gash(grid, [(13.0, centerline(13) - 2.6), (12.0, centerline(13)), (13.4, centerline(13) + 2.4)], body, "o")
    if wounds >= 2:
        gash(grid, [(19.0, centerline(19) - 2.4), (18.0, centerline(19)), (19.6, centerline(19) + 2.2)], body, "o")

    pa.outline_against(grid, head, body, color="O")
    eye(grid, 3.8, centerline(5) - 1.2, 1.4, 1.4, pupil_dx=-0.4, wounds=wounds)
    pa.outer_outline(grid, W, H)
    return grid


TYPES = (piranha, crab, shark, puffer, krait)


def main() -> int:
    rows = [
        [draw(frame, level) for draw in TYPES for frame in range(FRAME_COUNT)]
        for level in pa.WOUND_LEVELS
    ]
    sheet = pa.save_rows(OUTPUT, rows, PALETTE, FRAME_WIDTH, FRAME_HEIGHT)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {TYPE_COUNT} types x {FRAME_COUNT} frames x {len(rows)} rows)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
