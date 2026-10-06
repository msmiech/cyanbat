# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the Naga's sheet: its hooded head, its body, its tail, and the water it throws up.

    uv run tools/generate_lagoon_boss_sprite.py

The lagoon's boss is a serpent that rears up out of the water: a cobra's head under a great spread
hood, the hood banded with light, a gilded crest on its head, and a body a dozen parts long. Every
part is drawn at its own size in a 64x64 frame - bigger than the Sand Wyrm's, as the Naga is - so its
pixels stay as even as everything else's when the game turns the parts to lie along the body.

* **0-1** the head, its jaws shut and then open, facing left, the hood spread behind it. The game
  holds it upright while it rears and turns it only to strike and to swim.
* **2** its neck, the thickest of it, **3** a great coil, **4** a lesser one, **5** a small one, and
  **6** the tail. Drawn lying left to right, the front to the left, as the game lays them along the
  body toward the head; dark scales over a pale, banded belly, with a band of the hood's light round
  each.
* **7-10** water thrown up: a boil, a spout, a burst and the spray falling back - the tell before it
  rises, and the burst as it does.

**Hostiles are warm**: plum-black scales, a mauve belly and gold, and its light is neon pink, where a
cobra in a temple painting would glow with cold - cyan is the bat's. Its water is the sea's, pale
foam over lavender shadow.

The row is drawn three times over, top to bottom: unhurt, wounded and battered (`WoundComponent`), the
head carrying the health and the brain drawing the body from its row. A wound splits the hood and the
scales open onto the light inside, so its cracks glow, and battered the hood's edge is torn. The water
is never hurt, and is drawn in the top row only.
"""

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME = 64
FRAMES = 11

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (18, 6, 16, 255),
    "O": (44, 14, 36, 255),
    # its scales, plum-black, lit at the crown
    "n0": (26, 10, 26, 255),
    "n1": (54, 20, 48, 255),
    "n2": (92, 36, 76, 255),
    "n3": (140, 66, 112, 255),
    # its belly
    "v0": (150, 90, 96, 255),
    "v1": (214, 150, 136, 255),
    # the light in it
    "g0": (212, 36, 118, 255),
    "g1": (255, 92, 170, 255),
    "g2": (255, 196, 228, 255),
    # its gilded crest
    "a0": (184, 120, 38, 255),
    "a1": (246, 198, 86, 255),
    # its mouth and fangs
    "m": (110, 18, 48, 255),
    "t": (252, 238, 224, 255),
    # the cold eye every hostile has
    "e": (226, 252, 255, 255),
    "p": (16, 10, 28, 255),
    # the water: foam, its pale side, and its shadow
    "w0": (246, 250, 255, 255),
    "w1": (186, 232, 240, 255),
    "w2": (160, 150, 214, 255),
}

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "lagoonBoss.png"

W = H = FRAME


def raster(shape):
    return pa.rasterize(shape, W, H)


def paint(grid, pixels, key):
    for x, y in pixels:
        grid[y][x] = key


def glow_crack(grid, points, within, radius=0.6):
    """A split onto the light inside it: a dark-pink line with a bright core."""
    paint(grid, raster(pa.crack(points, radius + 0.35)) & within, "g0")
    paint(grid, raster(pa.crack(points, radius * 0.5)) & within, "g2")


# --- the head ------------------------------------------------------------------------------------


def head(frame, wounds=0):
    """
    A cobra's head facing left under its spread hood: the hood a tall rounded fan behind the head,
    banded with the light, lit along its right edge; the head on its front with a gilded crest; the
    pale scales of its throat running down the front of the hood to the neck, which leaves the frame
    where the body is laid from (the game's neck point, below and right of the middle).

    Its jaws are shut in the first frame and open in the second, the mouth glowing inside and the
    fangs bared.
    """
    grid = pa.blank(W, H)
    open_jaw = frame == 1

    hood = raster(pa.union(pa.ellipse(36.0, 28.0, 16.5, 22.0), pa.capsule((36, 40), (36, 63), 9.0, 8.0)))
    if wounds >= 2:
        hood = hood - raster(pa.polygon(pa.notch((52.0, 18.0), (52.0, 38.0), (40.0, 28.0), 0.4, width=0.6)))
    pa.shade_bands_across(grid, hood, ((0.25, "n0"), (0.6, "n1"), (0.86, "n2"), (1.01, "n3")))
    # The bands of light across the hood: arcs, widest at the hood's widest.
    bands = set()
    for k, by in enumerate((14.0, 23.0, 32.0, 41.0)):
        arc = raster(pa.ellipse(36.0, by + 10.0, 15.0 - k * 0.8, 10.0))
        inner = raster(pa.ellipse(36.0, by + 11.6, 15.0 - k * 0.8, 10.0))
        bands |= (arc - inner) & hood
    paint(grid, bands, "g1")
    paint(grid, {(x, y) for x, y in bands if (x, y - 1) not in bands}, "g2")

    # The throat's pale scales down the front of the hood to the neck, banded.
    throat = raster(pa.polygon([(22, 30), (30, 28), (36, 40), (38, 63), (29, 63), (26, 44)])) & hood
    pa.shade_bands_across(grid, throat, ((0.5, "v1"), (1.01, "v0")))
    paint(grid, {(x, y) for x, y in throat if y % 4 == 0}, "v0")

    # The head on the front of the hood, its snout pointing left, its jaw below.
    skull = raster(pa.union(pa.ellipse(22.0, 21.0, 11.0, 7.4), pa.polygon([(5, 22), (12, 16), (18, 18), (16, 27), (8, 27)])))
    pa.shade_bands(grid, skull, ((0.3, "n3"), (0.6, "n2"), (1.01, "n1")))
    # Its scales: a few plates across the crown.
    for sx in (14, 19, 24, 29):
        paint(grid, raster(pa.capsule((sx, 15.5), (sx + 1.5, 19.5), 0.45, 0.45)) & skull, "n1")
    if open_jaw:
        mouth = raster(pa.polygon([(5, 24), (20, 24), (24, 27), (18, 33), (7, 31)]))
        jaw = raster(pa.polygon([(6, 31), (18, 33), (24, 28), (26, 31), (19, 37), (8, 35)]))
        paint(grid, mouth, "m")
        paint(grid, raster(pa.ellipse(15.0, 28.0, 4.0, 2.4)) & mouth, "g0")
        pa.shade_bands(grid, jaw, ((0.5, "n2"), (1.01, "n1")))
        paint(grid, raster(pa.capsule((8, 24.5), (9, 29), 0.6, 0.3)) | raster(pa.capsule((14, 24.5), (14.6, 28.4), 0.6, 0.3)), "t")
        pa.outline_against(grid, jaw, mouth, color="O")
    else:
        jaw = raster(pa.polygon([(6, 26), (20, 26), (25, 28), (19, 31), (8, 30)]))
        pa.shade_bands(grid, jaw, ((0.5, "n2"), (1.01, "n1")))
        paint(grid, raster(pa.capsule((6.5, 26.2), (21, 26.6), 0.5, 0.5)), "o")
        paint(grid, {(9, 27), (14, 27)}, "t")
        # Its tongue, flicking out.
        paint(grid, raster(pa.capsule((5.5, 26.5), (1.5, 27.5), 0.45, 0.4)) | {(1, 26), (1, 29)}, "g1")

    # The crest: three gilded points along the top of its head.
    crest = set()
    for cx, tall in ((17.0, 5.0), (22.0, 7.0), (27.0, 5.0)):
        crest |= raster(pa.polygon([(cx - 2.2, 15.5), (cx, 15.5 - tall), (cx + 2.2, 15.5)]))
    crest -= skull
    pa.shade_bands_across(grid, crest, ((0.5, "a0"), (1.01, "a1")))

    if wounds >= 1:
        glow_crack(grid, [(46.0, 10.0), (43.0, 16.0), (47.0, 22.0), (44.0, 30.0)], hood - throat)
    if wounds >= 2:
        glow_crack(grid, [(27.0, 13.5), (24.6, 17.0), (27.4, 20.0), (25.0, 23.0)], skull)
        crest = crest - raster(pa.ellipse(27.0, 10.0, 2.6, 3.0))
        paint(grid, {(x, y) for x, y in raster(pa.ellipse(27.0, 10.0, 2.6, 3.0)) if grid[y][x] in ("a0", "a1")}, ".")

    pa.outline_against(grid, skull | jaw, hood - skull - jaw, color="O")
    pa.outline_against(grid, throat, hood - throat, color="O")
    eye(grid, 19.0, 19.0, 2.4, 2.0, wounds)
    pa.outer_outline(grid, W, H)
    return grid


def eye(grid, cx, cy, rx, ry, wounds):
    """The cold eye, with a ring of the hood's light round it. Battered, it is screwed half shut."""
    if wounds >= 2:
        cy, ry = cy + ry * 0.35, ry * 0.6
    paint(grid, raster(pa.ellipse(cx, cy, rx + 1.0, ry + 0.9)), "g0")
    paint(grid, raster(pa.ellipse(cx, cy, rx, ry)), "e")
    paint(grid, raster(pa.ellipse(cx - 0.7, cy + 0.1, rx * 0.4, ry * 0.7)), "p")


# --- the body ------------------------------------------------------------------------------------


def section(thickness, wounds=0, tail=False):
    """
    A length of its body lying left to right, the front to the left: dark scales over a pale, banded
    belly, lit along its back, with a band of the hood's light round it - so the light runs the length
    of the body in rings, part after part.

    The tail is a cone narrowing to the right, curling up at its tip.
    """
    grid = pa.blank(W, H)
    cy = 32.0
    if tail:
        body = set()
        for i in range(48):
            t = i / 47.0
            x = 8.0 + i
            y = cy - 6.0 * t * t
            body |= raster(pa.ellipse(x, y, 1.2, max(1.0, thickness * (1.0 - t) ** 0.8)))
    else:
        body = raster(pa.capsule((32.0 - thickness * 1.1, cy), (32.0 + thickness * 1.1, cy), thickness, thickness))
    pa.shade_bands(grid, body, ((0.15, "n3"), (0.4, "n2"), (0.62, "n1"), (0.72, "n0"), (0.86, "v0"), (1.01, "v1")))
    # The belly's plates, a line across it every few pixels.
    for x, y in body:
        if grid[y][x] in ("v0", "v1") and x % 3 == 0:
            grid[y][x] = "v0"
    # A diamond of scales along its back, and the ring of light round it.
    paint(grid, {(x, y) for x, y in body if grid[y][x] in ("n1", "n2") and (x + y) % 5 == 0}, "n0")
    ring_x = 34.0 if not tail else 18.0
    ring = raster(pa.capsule((ring_x - 1.0, cy - thickness - 2), (ring_x + 1.0, cy + thickness + 2), 1.2, 1.2)) & body
    paint(grid, ring, "g1")
    paint(grid, {(x, y) for x, y in ring if x == int(ring_x)}, "g2")
    if wounds >= 1:
        glow_crack(grid, [(26.0, cy - thickness * 0.8), (24.4, cy - 1.0), (26.6, cy + thickness * 0.5)], body, 0.5)
    if wounds >= 2:
        glow_crack(grid, [(40.0, cy - thickness * 0.7), (38.0, cy + 0.6), (40.6, cy + thickness * 0.6)], body, 0.5)
        body_notch = raster(pa.polygon(pa.notch((44.0, cy - thickness), (36.0, cy - thickness), (40.0, cy), 0.35, width=0.6)))
        paint(grid, body_notch & body, ".")
    pa.outer_outline(grid, W, H)
    return grid


# --- the water -----------------------------------------------------------------------------------


def splash(frame):
    """
    Water thrown up off the sea, standing on the bottom of the frame: a boil, a spout climbing out of
    it, a burst at its height throwing drops, and the spray falling back as a spreading ring of foam.
    """
    grid = pa.blank(W, H)
    base = 64.0
    if frame == 0:
        shapes = [pa.ellipse(32, base, 18, 8), pa.ellipse(24, base - 4, 6, 4), pa.ellipse(40, base - 3, 5, 4)]
        drops = [(20, base - 12), (44, base - 10)]
    elif frame == 1:
        shapes = [pa.ellipse(32, base, 20, 7), pa.capsule((32, base - 4), (31, base - 30), 8, 5), pa.ellipse(31, base - 32, 7, 6)]
        drops = [(18, base - 18), (46, base - 22), (24, base - 34)]
    elif frame == 2:
        shapes = [pa.ellipse(32, base, 22, 6), pa.capsule((32, base - 4), (30, base - 38), 7, 4),
                  pa.ellipse(26, base - 44, 7, 6), pa.ellipse(37, base - 46, 6, 5), pa.ellipse(31, base - 50, 5, 4)]
        drops = [(14, base - 30), (50, base - 34), (20, base - 52), (44, base - 56), (32, base - 60)]
    else:
        shapes = [pa.ellipse(32, base, 26, 5), pa.ellipse(14, base - 4, 6, 3), pa.ellipse(50, base - 4, 6, 3)]
        drops = [(10, base - 20), (54, base - 22), (18, base - 34), (46, base - 38), (28, base - 44), (38, base - 30)]
    water = raster(pa.union(*shapes))
    for dx, dy in drops:
        water |= raster(pa.ellipse(dx, dy, 1.8, 2.2))
    pa.shade_bands_across(grid, water, ((0.3, "w2"), (0.7, "w1"), (1.01, "w0")))
    paint(grid, {(x, y) for x, y in water if (x, y - 1) not in water}, "w0")
    pa.outer_outline(grid, W, H, color="w2")
    return grid


def body_row(wounds):
    return [
        head(0, wounds),
        head(1, wounds),
        section(14.0, wounds),
        section(12.5, wounds),
        section(10.0, wounds),
        section(7.5, wounds),
        section(7.0, wounds, tail=True),
    ]


def main() -> int:
    rows = [body_row(level) + ([splash(frame) for frame in range(4)] if level == 0 else [None] * 4)
            for level in pa.WOUND_LEVELS]
    sheet = pa.save_rows(OUTPUT, rows, PALETTE, FRAME, FRAME)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {FRAMES} frames x {len(rows)} rows)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
