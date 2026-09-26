# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the forest's enemy sheet: five hostiles, four frames each.

    uv run tools/generate_forest_enemy_sprites.py

Stage 2 needed enemies of its own rather than the cave's drones recolored, because the forest is a
different fight: things arrive in swarms and formations, some of them shoot, and some carry a
shield. A player has to be able to tell at a glance which of those a given sprite is going to do,
and the only thing a 32-pixel sprite can say that fast is its silhouette.

So each type has a silhouette that *is* its behavior:

* **Wasp** - small, striped, a sting on the back. It never comes alone; it comes as a swarm.
* **Beetle** - a heavy round shell with a horn. The one that is always shielded, and hard to stop.
* **Spitter** - a seed pod hanging from a spinning leaf rotor, mouth open. It stops and shoots.
* **Owl** - wings and two big eyes. It picks the player's lane and dives.
* **Wisp** - a flame with a face. They fly in formation.

The two rules from the cave's sheet still hold. **Hostiles are warm** - amber, crimson, magenta,
rust and flame - so over a forest of cool greens they stand out the way the cave's drones did over
blue-grey rock. And **one outline**: every sprite is ringed in the same near-black, so the five read
as one bestiary rather than as five pieces of borrowed art.

Laid out like `enemies.png`: 32x29 frames, four per type, types left to right in the order
`EnemySpecies` addresses them - 0 WASP, 1 BEETLE, 2 SPITTER, 3 OWL, 4 WISP.
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
    "o": (16, 10, 14, 255),     # outline, near-black with a little warmth in it
    "O": (30, 20, 24, 255),     # inner outline, where a part crosses another
    # wasp: amber and black
    "y0": (120, 70, 10, 255),
    "y1": (196, 128, 18, 255),
    "y2": (240, 186, 40, 255),
    "y3": (255, 232, 130, 255),
    "k0": (26, 18, 16, 255),
    "k1": (54, 40, 34, 255),
    # translucent-looking wings, pale and warm, drawn solid
    "w0": (176, 150, 132, 255),
    "w1": (232, 214, 196, 255),
    # beetle: crimson carapace with a metallic highlight
    "c0": (80, 14, 34, 255),
    "c1": (142, 26, 56, 255),
    "c2": (204, 52, 84, 255),
    "c3": (250, 150, 170, 255),
    # spitter: magenta pod, rust leaves
    "m0": (86, 20, 80, 255),
    "m1": (150, 40, 132, 255),
    "m2": (212, 84, 186, 255),
    "m3": (248, 170, 232, 255),
    "r0": (104, 46, 20, 255),
    "r1": (168, 84, 30, 255),
    "r2": (220, 136, 56, 255),
    # owl: rust-brown plumage, pale face
    "b0": (70, 36, 18, 255),
    "b1": (120, 66, 30, 255),
    "b2": (174, 104, 50, 255),
    "b3": (224, 170, 110, 255),
    "f0": (208, 180, 150, 255),
    "f1": (246, 228, 204, 255),
    # wisp: flame
    "h0": (150, 34, 18, 255),
    "h1": (222, 84, 26, 255),
    "h2": (252, 160, 44, 255),
    "h3": (255, 230, 150, 255),
    "h4": (255, 252, 232, 255),
    # the eye, shared with the cave's drones: the one cold thing on a warm body
    "e": (226, 252, 255, 255),
    "p": (16, 10, 28, 255),
}

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "forestEnemies.png"

W, H = FRAME_WIDTH, FRAME_HEIGHT


def raster(shape):
    return pa.rasterize(shape, W, H)


def paint(grid, pixels, key):
    for x, y in pixels:
        grid[y][x] = key


def eye(grid, cx, cy, rx=2.2, ry=2.0, pupil_dx=-0.6):
    paint(grid, raster(pa.ellipse(cx, cy, rx, ry)), "e")
    paint(grid, raster(pa.ellipse(cx + pupil_dx, cy + 0.1, rx * 0.48, ry * 0.55)), "p")


# --- wasp ----------------------------------------------------------------------------------------


def wasp(frame):
    """
    Small on purpose. A swarm is six of these on screen at once, and six full-frame sprites would
    be a wall rather than a swarm - the collision box is shrunk to match in `EnemySpecies`.
    """
    grid = pa.blank(W, H)
    bob = (0, -1, 0, 1)[frame]
    cy = 15 + bob

    # Wings first, behind the body: a pair of pale blades that blur between two poses, which is
    # what a wasp's wings look like at any frame rate.
    up = frame % 2 == 0
    wing_tip = (19, cy - 12) if up else (22, cy - 9)
    wing_back = (26, cy - 6) if up else (27, cy - 3)
    wings = raster(pa.union(
        pa.polygon([(14, cy - 2), wing_tip, wing_back, (18, cy - 1)]),
        pa.polygon([(16, cy - 2), (wing_tip[0] + 4, wing_tip[1] + 3), (wing_back[0] + 2, wing_back[1] + 1)]),
    ))
    pa.shade_bands(grid, wings, ((0.45, "w1"), (1.01, "w0")))

    head = pa.ellipse(8.5, cy, 3.6, 3.4)
    thorax = pa.ellipse(13.5, cy - 0.2, 3.4, 3.0)
    abdomen = pa.ellipse(21.0, cy + 1.2, 6.0, 4.0)
    sting = pa.polygon([(26, cy), (30.5, cy + 3.2), (26, cy + 3.4)])
    legs = pa.union(
        pa.capsule((12, cy + 2), (10, cy + 6), 0.6, 0.5),
        pa.capsule((15, cy + 2), (15, cy + 7), 0.6, 0.5),
    )
    body = raster(pa.union(head, thorax, abdomen, sting, legs))
    pa.shade_bands(grid, body, ((0.3, "y3"), (0.6, "y2"), (0.85, "y1"), (1.01, "y0")))

    # The stripes, which are the whole of what says "wasp" at this size.
    stripes = set()
    for sx in (18.5, 22.5):
        stripes |= raster(pa.polygon([(sx, cy - 4), (sx + 1.8, cy - 4), (sx + 2.6, cy + 6), (sx + 0.8, cy + 6)]))
    paint(grid, stripes & raster(abdomen), "k0")
    paint(grid, raster(legs) | raster(sting), "k1")

    # Antennae, forward and up.
    paint(grid, raster(pa.capsule((7, cy - 3), (4, cy - 7), 0.55, 0.45)), "k1")

    pa.outline_against(grid, wings - body, body, color="O")
    eye(grid, 7.6, cy - 0.4, 1.7, 1.8, pupil_dx=-0.5)
    pa.outer_outline(grid, W, H)
    return grid


# --- beetle --------------------------------------------------------------------------------------


def beetle(frame):
    """
    A round domed shell, a horn off the front, and membrane wings buzzing out from under the wing
    cases. The dome is the shape a shield bubble sits over most naturally, which is why the beetle
    is the one that always carries one.
    """
    grid = pa.blank(W, H)
    bob = (0, 0, 1, 1)[frame]
    cx, cy = 16.5, 15.5 + bob

    # Hind wings, flickering out behind the shell.
    spread = (0.0, 3.0, 5.0, 2.0)[frame]
    wings = raster(pa.polygon([
        (19, cy - 5), (28, cy - 9 - spread), (31, cy - 4 - spread * 0.5), (24, cy - 1),
    ]))
    pa.shade_bands(grid, wings, ((0.5, "w1"), (1.01, "w0")))

    legs = set()
    for lx, reach in ((11, -3), (16, 0), (21, 3)):
        step = (1.5 if (frame + lx) % 2 else -1.5)
        legs |= raster(pa.capsule((lx, cy + 4), (lx + reach * 0.3 + step, cy + 10), 0.7, 0.6))
    paint(grid, legs, "k1")

    shell = pa.ellipse(cx + 1.5, cy, 11.0, 8.2)
    head = pa.ellipse(cx - 9.0, cy + 2.2, 4.4, 3.8)
    horn = pa.polygon([(cx - 11, cy - 0.5), (cx - 15.5, cy - 7.5), (cx - 13.5, cy - 7.0), (cx - 8.5, cy - 1.5)])
    body = raster(pa.union(shell, head, horn))
    # Flat-bottomed: the lower edge of the shell is its belly, not more dome.
    body = {(x, y) for x, y in body if y <= cy + 6}
    pa.shade_bands(grid, body, ((0.18, "c3"), (0.42, "c2"), (0.72, "c1"), (1.01, "c0")))

    # The seam down the wing cases, and a metallic glint on the dome.
    seam = raster(pa.capsule((cx - 2, cy - 7.5), (cx + 10, cy + 2), 0.55, 0.55)) & body
    paint(grid, seam, "c0")
    paint(grid, raster(pa.ellipse(cx - 1.0, cy - 4.2, 2.6, 1.4)) & body, "c3")

    pa.outline_against(grid, wings - body, body, color="O")
    pa.outline_against(grid, legs - body, body, color="O")
    eye(grid, cx - 9.8, cy + 1.6, 1.8, 1.7, pupil_dx=-0.5)
    pa.outer_outline(grid, W, H)
    return grid


# --- spitter -------------------------------------------------------------------------------------


def spitter(frame):
    """
    A seed pod slung under a spinning three-leaf rotor, with its mouth open toward the player.

    The rotor is the animation: four frames of a leaf going round, so the thing reads as hovering
    in place rather than flying - which is exactly what it does before it opens fire.
    """
    grid = pa.blank(W, H)
    cx, cy = 16.0, 18.0

    # The rotor, seen edge-on, so a spinning leaf is a blade getting longer and shorter.
    hub = (cx + 1.0, 6.5)
    leaves = set()
    for index in range(3):
        angle = frame * (pi / 6.0) + index * (2.0 * pi / 3.0)
        reach = 11.0 * cos(angle)
        depth = sin(angle)
        tip = (hub[0] + reach, hub[1] - 1.0 + depth * 1.2)
        leaves |= raster(pa.polygon([
            (hub[0], hub[1] - 1.5), (tip[0], tip[1] - 1.3), (tip[0], tip[1] + 1.3), (hub[0], hub[1] + 1.5),
        ]))
    stalk = raster(pa.capsule(hub, (cx + 0.5, cy - 6), 1.0, 1.2))
    pa.shade_bands(grid, leaves, ((0.4, "r2"), (0.8, "r1"), (1.01, "r0")))
    paint(grid, stalk, "r0")

    pulse = (0.0, 0.4, 0.8, 0.4)[frame]
    pod = pa.ellipse(cx + 1.5, cy, 8.4 + pulse, 7.6 + pulse * 0.5)
    lips = pa.polygon([(cx - 8.5, cy - 3.5), (cx - 12.0, cy - 1.0), (cx - 12.0, cy + 2.5), (cx - 8.5, cy + 4.0)])
    body = raster(pa.union(pod, lips))
    pa.shade_bands(grid, body, ((0.22, "m3"), (0.5, "m2"), (0.8, "m1"), (1.01, "m0")))

    # Ridges down the pod, so it reads as a seed rather than a ball.
    for rx in (cx + 2.0, cx + 6.0):
        paint(grid, raster(pa.capsule((rx, cy - 6.5), (rx + 1.0, cy + 6.5), 0.5, 0.5)) & body, "m1")

    # The mouth: dark, with the round it is about to fire glowing in the back of it.
    mouth = raster(pa.ellipse(cx - 10.4, cy + 0.8, 1.6, 2.2))
    paint(grid, mouth, "p")
    paint(grid, raster(pa.ellipse(cx - 9.8, cy + 0.8, 0.8, 0.9)), "r2")

    pa.outline_against(grid, stalk - body, body, color="O")
    eye(grid, cx - 3.5, cy - 3.0, 2.2, 2.0)
    pa.outer_outline(grid, W, H)
    return grid


# --- owl -----------------------------------------------------------------------------------------


def owl(frame):
    """
    Side on, flying left: a round body, ear tufts, a hooked beak and the two big eyes an owl is
    recognised by. The wings are the whole animation, and they beat big - this is the one that
    lunges, and it should look like it has the reach to.
    """
    grid = pa.blank(W, H)
    bob = (1, 0, -1, 0)[frame]
    cx, cy = 17.0, 16.0 + bob

    # The far wing, darker and behind everything.
    beat = (-0.9, 0.2, 1.0, 0.3)[frame]  # -1 up, +1 down
    root = (cx + 1.0, cy - 2.0)
    far_tip = (cx + 10.0, cy - 11.0 + beat * 12.0)
    far = raster(pa.polygon([root, (cx - 3.0, cy - 7.0 + beat * 8.0), far_tip, (cx + 13.0, cy + 1.0)]))
    pa.shade_bands(grid, far, ((0.6, "b0"), (1.01, "b1")))

    tail = pa.polygon([(cx + 7, cy + 2), (cx + 14, cy + 4), (cx + 13, cy + 8), (cx + 6, cy + 6)])
    body = pa.ellipse(cx + 1.5, cy + 2.0, 7.4, 7.0)
    head = pa.ellipse(cx - 5.0, cy - 1.5, 6.0, 5.6)
    tufts = pa.union(
        pa.polygon([(cx - 8.5, cy - 5.0), (cx - 10.5, cy - 10.5), (cx - 6.0, cy - 6.5)]),
        pa.polygon([(cx - 3.0, cy - 6.0), (cx - 2.5, cy - 11.0), (cx + 0.5, cy - 5.5)]),
    )
    torso = raster(pa.union(tail, body, head, tufts))
    pa.shade_bands(grid, torso, ((0.3, "b2"), (0.65, "b1"), (1.01, "b0")))

    # The breast, pale and barred.
    breast = raster(pa.ellipse(cx - 1.0, cy + 4.5, 4.6, 4.2)) & torso
    pa.shade_bands(grid, breast, ((0.5, "b3"), (1.01, "b2")))
    for by in (cy + 3.0, cy + 6.0):
        paint(grid, raster(pa.capsule((cx - 3.5, by), (cx + 1.5, by + 0.6), 0.4, 0.4)) & breast, "b1")

    # The facial disc and the eyes.
    face = raster(pa.ellipse(cx - 6.5, cy - 1.0, 4.0, 3.8)) & torso
    pa.shade_bands(grid, face, ((0.5, "f1"), (1.01, "f0")))
    paint(grid, raster(pa.polygon([(cx - 10.0, cy + 0.2), (cx - 12.6, cy + 1.8), (cx - 9.6, cy + 3.2)])), "r1")

    # The near wing last: over the body, so the downstroke visibly sweeps across it.
    near_tip = (cx + 7.0, cy - 12.0 + beat * 16.0)
    near = raster(pa.polygon([
        (cx - 2.0, cy - 1.0), (cx - 4.0, cy - 5.0 + beat * 9.0), near_tip, (cx + 11.0, cy + 1.0 + beat * 2.0),
    ]))
    pa.shade_bands(grid, near, ((0.35, "b3"), (0.7, "b2"), (1.01, "b1")))
    pa.outline_against(grid, near, torso - near, color="O")
    pa.outline_against(grid, far - torso - near, torso | near, color="O")

    # Drawn after the wing so the eye is never covered: the eyes are the owl.
    eye(grid, cx - 7.6, cy - 1.8, 2.0, 2.1, pupil_dx=-0.4)

    pa.outer_outline(grid, W, H)
    return grid


# --- wisp ----------------------------------------------------------------------------------------


def wisp(frame):
    """
    A ball of flame with a face in it, trailing fire behind. It flickers rather than flaps: the
    tail's tongues move frame to frame and the core pulses, so a formation of five reads as lit
    torches moving in step.
    """
    grid = pa.blank(W, H)
    cx, cy = 11.5, 15.0
    pulse = (0.0, 0.6, 1.0, 0.5)[frame]

    tongues = set()
    for index, (dy, length) in enumerate(((-4.5, 15.0), (0.0, 19.0), (4.5, 14.0))):
        wave = sin(frame * pi / 2.0 + index * 1.7) * 2.2
        tip = (cx + length, cy + dy + wave)
        tongues |= raster(pa.polygon([
            (cx + 1.0, cy + dy - 3.2), tip, (cx + 1.0, cy + dy + 3.2),
        ]))
    tongues |= raster(pa.ellipse(cx + 4.0, cy, 6.0, 6.2))
    pa.shade_bands_across(grid, tongues, ((0.3, "h2"), (0.65, "h1"), (1.01, "h0")))

    core = raster(pa.ellipse(cx, cy, 7.4 + pulse, 7.0 + pulse))
    pa.shade_bands(grid, core, ((0.2, "h4"), (0.45, "h3"), (0.8, "h2"), (1.01, "h1")))
    paint(grid, raster(pa.ellipse(cx - 1.0, cy - 0.5, 3.6 + pulse * 0.5, 3.4 + pulse * 0.5)), "h4")

    # A pair of dark eyes, so it has a face and so it has a front.
    paint(grid, raster(pa.ellipse(cx - 3.8, cy - 1.2, 0.9, 1.5)), "p")
    paint(grid, raster(pa.ellipse(cx - 0.8, cy - 1.2, 0.9, 1.5)), "p")

    pa.outer_outline(grid, W, H)
    return grid


TYPES = (wasp, beetle, spitter, owl, wisp)


def main() -> int:
    grids = [draw(frame) for draw in TYPES for frame in range(FRAME_COUNT)]
    sheet = pa.save_sheet(OUTPUT, grids, PALETTE, FRAME_WIDTH, FRAME_HEIGHT)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {TYPE_COUNT} types x {FRAME_COUNT} frames)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
