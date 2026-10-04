# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the desert's enemy sheet: five hostiles, four frames each.

    uv run tools/generate_desert_enemy_sprites.py

As in the jungle, each silhouette says what the thing is going to do:

* **Locust** - small, long-legged, a flicker of wings. It never comes alone; it comes as a cloud.
* **Hawk** - wings, a hooked beak and a fanned tail. It throws a loop on its way past, and turns to
  point along it, so it is drawn to read the right way up at any angle it is turned to.
* **Wyrmling** - a ridged worm with a round, glowing maw: the Sand Wyrm's brood. It comes in under
  the sand with only its **back** showing, so the top eleven rows of its frame are a crest of spines
  that has to read as *something is coming* on its own.
* **Djinn** - a genie of smoke with a flame for hair. It hangs in the air and throws fans of fire.
* **Scarab** - a domed shell rimmed in gold, rolling the sun in front of it. The tank, whose shell
  grows back.

The desert needs its hostiles to read on two very different skies: a bleached noon that is paler
than anything in the jungle, and a purple night darker than it. So they sit in the middle - warm,
saturated, mid-dark bodies that stand out against the pale, with bright highlights, glowing eyes
and throats that stand out against the dark - and the near-black outline every hostile has does
the rest in daylight. **Hostiles are warm**, as they are everywhere: crimson, rust, magenta and gold.

Laid out like the other sheets: 32x29 frames, four per type, types left to right in the order
`EnemySpecies` addresses them - 0 LOCUST, 1 HAWK, 2 WYRMLING, 3 DJINN, 4 SCARAB. Everything faces
left, toward the bat. The row is drawn three times over, top to bottom: unhurt, wounded and
battered (`WoundComponent`).

The wounds follow the jungle's rules - wings and feathers tear, shells crack, what sticks out snaps
off, battered eyes narrow - except in the two that burn inside. The wyrmling's plates and the
djinn's smoke split open onto the fire in them, so their wounds glow: at night, the desert's
darkest hour, a crack drawn dark would be lost against the sky.
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
    "o": (18, 8, 12, 255),
    "O": (40, 16, 22, 255),
    # locust: rust-crimson, with pale wings
    "l0": (84, 22, 18, 255),
    "l1": (146, 46, 28, 255),
    "l2": (204, 84, 40, 255),
    "l3": (242, 146, 70, 255),
    "w0": (178, 108, 82, 255),
    "w1": (234, 186, 148, 255),
    "k0": (40, 14, 14, 255),
    "k1": (72, 28, 22, 255),
    # hawk: rust back, cream breast, dark flight feathers, a gold beak
    "h0": (74, 30, 20, 255),
    "h1": (134, 58, 30, 255),
    "h2": (192, 98, 46, 255),
    "h3": (234, 152, 82, 255),
    "c0": (206, 162, 118, 255),
    "c1": (244, 218, 180, 255),
    "y0": (206, 140, 34, 255),
    "y1": (252, 206, 88, 255),
    # wyrmling: crimson armor, a pale belly and a molten throat - the Sand Wyrm's own colors
    "r0": (84, 18, 26, 255),
    "r1": (148, 34, 36, 255),
    "r2": (208, 72, 50, 255),
    "r3": (244, 132, 76, 255),
    "b0": (212, 158, 112, 255),
    "b1": (244, 210, 164, 255),
    "g0": (255, 150, 36, 255),
    "g1": (255, 228, 120, 255),
    # djinn: magenta smoke, gold bands, a flame for hair
    "m0": (92, 20, 82, 255),
    "m1": (158, 42, 134, 255),
    "m2": (216, 88, 186, 255),
    "m3": (248, 164, 230, 255),
    "f0": (214, 70, 26, 255),
    "f1": (252, 150, 40, 255),
    "f2": (255, 230, 140, 255),
    # scarab: a crimson dome with a gold rim, and the sun it rolls
    "s0": (78, 12, 32, 255),
    "s1": (138, 28, 52, 255),
    "s2": (198, 56, 72, 255),
    "s3": (244, 138, 132, 255),
    "a0": (184, 118, 30, 255),
    "a1": (246, 200, 84, 255),
    "a2": (255, 244, 180, 255),
    # the eye, shared with every hostile: the one cold thing on a warm body
    "e": (226, 252, 255, 255),
    "p": (16, 10, 28, 255),
}

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "desertEnemies.png"

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


# --- locust --------------------------------------------------------------------------------------


def locust(frame, wounds=0):
    """
    A long body, a blunt head with a big eye, and the hind leg folded up high - the jumping leg is
    what says "locust" rather than "wasp" at this size. Small, like the wasp, because it comes as a
    cloud of six.

    Wounded, its wing is notched and an antenna snapped; battered, the wing's tip is torn away and
    the jumping leg is broken off at the knee.
    """
    grid = pa.blank(W, H)
    bob = (0, -1, 0, 1)[frame]
    cy = 15 + bob

    up = frame % 2 == 0
    wing_tip = (29, cy - (6 if up else 2))
    wing = raster(pa.polygon([(13, cy - 2), (19, cy - (10 if up else 6)), wing_tip, (22, cy - 1)]))
    if wounds >= 1:
        wing = torn(wing, pa.notch(wing_tip, (22, cy - 1), (15, cy - 4), 0.5, width=0.55))
    if wounds >= 2:
        wing = torn(wing, pa.ring(wing_tip, 3.8))
    pa.shade_bands(grid, wing, ((0.45, "w1"), (1.01, "w0")))

    head = pa.ellipse(8.0, cy + 0.5, 3.8, 3.6)
    thorax = pa.ellipse(13.0, cy, 3.6, 3.4)
    abdomen = pa.capsule((15, cy + 0.5), (27, cy + 2.5), 3.2, 2.0)
    body = raster(pa.union(head, thorax, abdomen))
    pa.shade_bands(grid, body, ((0.3, "l3"), (0.58, "l2"), (0.85, "l1"), (1.01, "l0")))
    # Bands along the abdomen.
    for sx in (18, 21, 24):
        paint(grid, raster(pa.capsule((sx, cy - 2), (sx + 0.6, cy + 4), 0.45, 0.45)) & body, "l1")

    # The hind leg: thigh folded up and back, shin down to the foot.
    kick = (0.0, 1.0, 0.0, -1.0)[frame]
    knee = (22 + kick, cy - 5)
    # Battered, the shin is gone and the thigh ends in a stump at the knee.
    shin = pa.capsule(knee, (18 + kick, cy + 7), 0.7, 0.5) if wounds < 2 else pa.capsule(knee, knee, 0.9, 0.9)
    legs = raster(pa.union(
        pa.capsule((15, cy + 1), knee, 1.2, 0.8),
        shin,
        pa.capsule((11, cy + 2), (9, cy + 7), 0.55, 0.45),
        pa.capsule((13, cy + 2), (14, cy + 7), 0.55, 0.45),
    )) - body
    paint(grid, legs, "k1")
    paint(grid, raster(pa.capsule((6, cy - 2), (2, cy - 7) if wounds < 1 else (4.2, cy - 4.4), 0.5, 0.4)), "k1")

    pa.outline_against(grid, wing - body, body, color="O")
    eye(grid, 7.2, cy - 0.2, 1.8, 1.9, pupil_dx=-0.5, wounds=wounds)
    pa.outer_outline(grid, W, H)
    return grid


# --- hawk ----------------------------------------------------------------------------------------


def wing_shape(shoulder, angle_degrees, length, chord):
    """
    One wing as a feathered blade from [shoulder], swept back along [angle_degrees] - measured from
    pointing straight back, negative for raised - with a notched trailing edge for the primaries.
    """
    a = angle_degrees * pi / 180.0
    dx, dy = cos(a), sin(a)
    px, py = -dy, dx  # across the wing, toward its trailing edge
    sx, sy = shoulder

    def at(along, across):
        return (sx + dx * along + px * across, sy + dy * along + py * across)

    points = [
        at(0.0, -1.0), at(length * 0.55, -1.8), at(length, 0.0), at(length * 0.86, chord * 0.45),
        at(length * 0.74, chord * 0.3), at(length * 0.62, chord * 0.75), at(length * 0.48, chord * 0.6),
        at(length * 0.34, chord), at(0.0, chord * 0.8),
    ]
    return pa.polygon(points), at(length * 0.72, chord * 0.35), points


def plucked(wing, points, shoulder, gaps):
    """
    [wing] with feathers gone from its trailing edge: a notch between each pair of its outline
    [points] named in [gaps], cut in toward the [shoulder].
    """
    return torn(wing, *(pa.notch(points[a], points[b], shoulder, 0.62, width=0.75) for a, b in gaps))


def hawk(frame, wounds=0):
    """
    Side on, flying left: a hooked gold beak, a pale breast, a barred fan of a tail, and wings that
    beat through a full stroke. It gets turned right round as it loops, so it is drawn to read by
    silhouette alone - the beak and the tail are what say which way it is going.

    Wounded, feathers are gone from the near wing; battered, from both wings and the tail.
    """
    grid = pa.blank(W, H)
    # The near wing's sweep through one beat: raised, driving down, at the bottom, recovering.
    sweep = (-62.0, -18.0, 34.0, -4.0)[frame]
    cy = 15 + (1, 0, -1, 0)[frame]

    far, _, far_points = wing_shape((15, cy - 1), sweep - 14.0, 11.0, 5.0)
    far = raster(far)
    if wounds >= 2:
        far = plucked(far, far_points, (15, cy - 1), [(4, 6)])
    pa.shade_bands(grid, far, ((0.6, "h0"), (1.01, "h1")))

    tail = pa.polygon([(21, cy), (30, cy - 3), (31, cy + 3), (21, cy + 3)])
    body = pa.ellipse(15.5, cy + 1.0, 8.0, 4.2)
    head = pa.ellipse(7.5, cy - 1.0, 3.8, 3.4)
    torso = raster(pa.union(tail, body, head))
    if wounds >= 2:
        torso = torn(torso, pa.notch((30, cy - 3), (31, cy + 3), (22, cy + 1.5), 0.4, width=0.5))
    pa.shade_bands(grid, torso, ((0.3, "h3"), (0.62, "h2"), (1.01, "h1")))
    breast = raster(pa.ellipse(12.0, cy + 3.0, 5.4, 2.4)) & torso
    pa.shade_bands(grid, breast, ((0.5, "c1"), (1.01, "c0")))
    for bx in (10, 13, 16):
        paint(grid, {(bx, cy + 3), (bx + 1, cy + 4)} & breast, "c0")
    # Bars across the tail.
    for tx in (25, 28):
        paint(grid, raster(pa.capsule((tx, cy - 2), (tx, cy + 3), 0.45, 0.45)) & torso, "h0")

    beak = raster(pa.polygon([(4.5, cy - 2.2), (0.8, cy - 0.4), (2.2, cy + 1.6), (4.8, cy + 0.2)]))
    pa.shade_bands(grid, beak, ((0.5, "y1"), (1.01, "y0")))

    near, primaries, near_points = wing_shape((12, cy - 1), sweep, 15.0, 6.5)
    near = raster(near)
    if wounds >= 1:
        near = plucked(near, near_points, (12, cy - 1), [(4, 6)] if wounds == 1 else [(4, 6), (6, 8)])
    pa.shade_bands(grid, near, ((0.35, "h3"), (0.7, "h2"), (1.01, "h1")))
    # Dark primaries toward the tip, which is what makes a wing read as feathers.
    paint(grid, raster(pa.ellipse(primaries[0], primaries[1], 3.6, 3.6)) & near, "h0")
    pa.outline_against(grid, near, torso - near, color="O")
    pa.outline_against(grid, far - torso - near, torso | near, color="O")

    eye(grid, 7.0, cy - 1.8, 1.5, 1.5, pupil_dx=-0.4, wounds=wounds)
    pa.outer_outline(grid, W, H)
    return grid


# --- wyrmling ------------------------------------------------------------------------------------


# Which spines of the crest are broken at each wound level, front first, and how much of each is left.
BROKEN_SPINES = ({}, {1: 0.35}, {0: 0.45, 1: 0.35, 3: 0.3})


def wyrmling(frame, wounds=0):
    """
    A worm of crimson plates with a crest of spines along its back and a round maw at the front,
    glowing in its throat. It undulates rather than flaps.

    The crest is the tell: coming in under the sand, only the top eleven rows show, so everything
    above row eleven is spines and the ridge of its back.

    Wounded, a plate is split open on the fire inside and a spine of the crest is snapped; battered,
    a second plate is split and three spines are snapped - so even the tell looks the worse for it.
    """
    grid = pa.blank(W, H)
    phase = frame * pi / 2.0

    def centerline(x):
        return 16.0 + 1.8 * sin(x * 0.32 + phase)

    # The body as a run of overlapping plates, tapering to the tail.
    plates = []
    for index, x in enumerate(range(10, 31, 4)):
        radius = 6.4 - index * 0.75
        plates.append(pa.ellipse(x, centerline(x), radius + 0.6, radius))
    body = raster(pa.union(*plates))
    pa.shade_bands(grid, body, ((0.26, "r3"), (0.52, "r2"), (0.8, "r1"), (1.01, "r0")))
    belly = {(x, y) for x, y in body if y > centerline(x) + 2.2}
    pa.shade_bands(grid, belly, ((0.5, "b1"), (1.01, "b0")))
    for x in range(13, 31, 4):
        paint(grid, raster(pa.capsule((x, centerline(x) - 6), (x - 0.5, centerline(x) + 5), 0.45, 0.45)) & body, "r0")
    # Split plates, following the body's own curve so they ride its undulation.
    for x in (15.0, 21.0)[:wounds]:
        gash(grid, [(x + dx, centerline(x + dx) + dy) for dx, dy in ((-0.4, -4.6), (0.9, -2.0), (-0.6, 0.4), (0.8, 2.8))], body, "g0")

    # The crest: a spine per plate, leaning back.
    spines = set()
    for index, x in enumerate(range(9, 30, 4)):
        base_y = centerline(x) - (6.0 - index * 0.75)
        height = (5.5 - index * 0.55) * BROKEN_SPINES[wounds].get(index, 1.0)
        spines |= raster(pa.polygon([(x - 2.0, base_y + 1.5), (x + 1.6, base_y - height), (x + 2.2, base_y + 1.5)]))
    spines -= body
    pa.shade_bands(grid, spines, ((0.5, "r2"), (1.01, "r1")))

    # The head: a blunt collar round a round maw, rows of teeth and a glowing throat.
    head_y = centerline(6)
    head = raster(pa.ellipse(6.5, head_y, 5.6, 6.2))
    pa.shade_bands(grid, head, ((0.3, "r3"), (0.6, "r2"), (1.01, "r1")))
    gape = (1.8, 2.3, 2.6, 2.1)[frame]
    maw = raster(pa.ellipse(3.8, head_y + 0.3, gape, gape + 0.6))
    paint(grid, maw, "p")
    paint(grid, raster(pa.ellipse(4.3, head_y + 0.3, gape * 0.55, gape * 0.6)), "g0")
    paint(grid, raster(pa.ellipse(4.6, head_y + 0.3, gape * 0.25, gape * 0.3)), "g1")
    for angle in range(0, 360, 60):
        tx = 3.8 + cos(angle * pi / 180) * (gape + 0.4)
        ty = head_y + 0.3 + sin(angle * pi / 180) * (gape + 1.0)
        paint(grid, raster(pa.ellipse(tx, ty, 0.6, 0.6)) & (head | maw), "b1")

    pa.outline_against(grid, head, body - head, color="O")
    pa.outline_against(grid, spines, body, color="O")
    eye(grid, 8.4, head_y - 3.4, 1.4, 1.3, pupil_dx=-0.3, wounds=wounds)
    pa.outer_outline(grid, W, H)
    return grid


# --- djinn ---------------------------------------------------------------------------------------


def djinn(frame, wounds=0):
    """
    Upright, the way a genie hangs in the air: a gold turban with a jewel in it, a pointed beard,
    broad shoulders with the arms folded across them, and below the waist nothing but a curl of
    smoke. The smoke sways from frame to frame while the rest of it holds still, which is exactly
    what it does on station.

    Wounded, a fissure opens across its chest onto the fire it is made of; battered, a second one
    runs down its face, the plume is gone from its turban and its smoke is coming apart.
    """
    grid = pa.blank(W, H)
    sway = (0.0, 1.0, 0.0, -1.0)[frame]
    bob = (0, 0, 1, 1)[frame]

    # The smoke tail: down from the waist, then back and up into a hook.
    tail = set()
    for index in range(10):
        t = index / 9.0
        cx = 14.0 + t * 13.0 + sway * t * t * 2.0
        cy = 20.0 + bob + sin(t * pi * 0.9) * 5.0 - t * t * 4.0
        tail |= raster(pa.ellipse(cx, cy, 4.4 - t * 3.0, 3.8 - t * 2.4))
    if wounds >= 2:
        # A gap blown through the smoke, a little way down it.
        t = 0.45
        gap = (14.0 + t * 13.0 + sway * t * t * 2.0, 20.0 + bob + sin(t * pi * 0.9) * 5.0 - t * t * 4.0 - 1.2)
        tail = torn(tail, pa.ring(gap, 2.2))
    pa.shade_bands(grid, tail, ((0.35, "m2"), (0.7, "m1"), (1.01, "m0")))

    y = bob
    shoulders = pa.ellipse(14.5, 15.0 + y, 7.0, 4.6)
    head = pa.ellipse(11.0, 9.0 + y, 3.6, 3.6)
    ear = pa.polygon([(13.8, 8.0 + y), (16.6, 6.4 + y), (14.2, 10.0 + y)])
    body = raster(pa.union(shoulders, head, ear))
    pa.shade_bands(grid, body, ((0.28, "m3"), (0.58, "m2"), (0.84, "m1"), (1.01, "m0")))

    # Arms folded across the chest, gold at the wrists.
    arms = raster(pa.capsule((8.5, 16.0 + y), (20.0, 16.0 + y), 1.8, 1.8)) & body
    paint(grid, arms, "m1")
    paint(grid, raster(pa.capsule((8.5, 14.6 + y), (19.5, 14.6 + y), 0.45, 0.45)) & arms, "m2")
    paint(grid, raster(pa.ellipse(9.3, 16.0 + y, 1.2, 1.6)) | raster(pa.ellipse(19.0, 16.0 + y, 1.2, 1.6)), "a1")
    paint(grid, raster(pa.ellipse(15.2, 10.0 + y, 0.8, 0.9)), "a1")

    # A pointed beard under the chin.
    beard = raster(pa.polygon([(8.2, 10.5 + y), (12.5, 11.0 + y), (9.0, 15.5 + y)]))
    paint(grid, beard, "m0")

    # Fissures onto the fire inside, a glowing core with a hot edge.
    fissures = (
        ((18.4, 11.0), (16.6, 13.4), (18.6, 15.2), (16.8, 18.2)),
        ((12.8, 7.4), (12.0, 9.2), (13.6, 10.6), (12.6, 12.4)),
    )
    for fissure in fissures[:wounds]:
        points = [(fx, fy + y) for fx, fy in fissure]
        gash(grid, points, body, "f0", 0.75)
        gash(grid, points, body, "f2", 0.4)

    # The turban: gold, wound round, with a flame-colored jewel at its front and a plume.
    turban = raster(pa.ellipse(11.5, 5.6 + y, 4.8, 2.9))
    pa.shade_bands(grid, turban, ((0.35, "a2"), (0.7, "a1"), (1.01, "a0")))
    paint(grid, raster(pa.capsule((7.5, 6.2 + y), (15.8, 4.8 + y), 0.45, 0.45)) & turban, "a0")
    paint(grid, raster(pa.ellipse(7.8, 5.6 + y, 1.3, 1.4)), "f1")
    paint(grid, {(7, 5 + y)}, "f2")
    if wounds < 2:
        plume = raster(pa.polygon([(12.0, 3.4 + y), (15.0, -0.5 + y), (14.4, 3.2 + y)])) - turban
        paint(grid, plume, "f0")

    pa.outline_against(grid, tail - body, body, color="O")
    pa.outline_against(grid, turban, body - turban, color="O")
    # Glowing eyes under the turban's edge: the cold eye, narrowed.
    paint(grid, {(8, 8 + y), (9, 8 + y)}, "e")
    paint(grid, {(8, 9 + y)}, "p")
    pa.outer_outline(grid, W, H)
    return grid


# --- scarab --------------------------------------------------------------------------------------


def scarab(frame, wounds=0):
    """
    A heavy domed beetle rolling a sun in front of it: the old image of the sun being pushed across
    the sky, which a desert that watches the sun go down is the place for. The dome is rimmed in
    gold and sits under a shield bubble most naturally, which is why it is the one that always has
    one - and grows it back.

    Wounded, its dome is cracked through the shine; battered, it is cracked again with a chunk
    broken out of its back, and the sun it rolls is cracked too.
    """
    grid = pa.blank(W, H)
    bob = (0, 0, 1, 1)[frame]
    cx, cy = 18.0, 15.0 + bob

    # Membrane wings buzzing out from under the back of the shell.
    spread = (0.0, 3.0, 5.0, 2.0)[frame]
    wings = raster(pa.polygon([(22, cy - 5), (29, cy - 9 - spread), (31.5, cy - 3 - spread * 0.4), (26, cy - 1)]))
    pa.shade_bands(grid, wings, ((0.5, "w1"), (1.01, "w0")))

    legs = set()
    for lx in (12, 17, 22):
        step = 1.5 if (frame + lx) % 2 else -1.5
        legs |= raster(pa.capsule((lx, cy + 4), (lx - 1.5 + step, cy + 10), 0.7, 0.6))
    paint(grid, legs, "k1")

    shell = pa.ellipse(cx + 1.0, cy, 10.0, 8.0)
    head = pa.ellipse(cx - 9.0, cy + 2.2, 4.0, 3.4)
    # The rake on its head, the scarab's own crown.
    rake = pa.polygon([(cx - 12.5, cy - 0.5), (cx - 13.5, cy - 3.5), (cx - 11.0, cy - 2.0), (cx - 10.0, cy - 4.0), (cx - 8.5, cy - 0.5)])
    body = raster(pa.union(shell, head, rake))
    body = {(x, y) for x, y in body if y <= cy + 6}
    if wounds >= 2:
        body = torn(body, pa.ring((cx + 9.8, cy - 4.6), 2.8))
    pa.shade_bands(grid, body, ((0.18, "s3"), (0.42, "s2"), (0.74, "s1"), (1.01, "s0")))
    # The gold rim along the bottom of the dome, and the seam down its back.
    rim = {(x, y) for x, y in body if cy + 4 <= y <= cy + 6 and x > cx - 8}
    paint(grid, rim, "a0")
    paint(grid, {(x, y) for x, y in rim if y == cy + 4}, "a1")
    paint(grid, raster(pa.capsule((cx - 1, cy - 7.5), (cx + 9, cy + 3), 0.55, 0.55)) & body, "s0")
    paint(grid, raster(pa.ellipse(cx - 2.0, cy - 4.0, 2.4, 1.3)) & body, "s3")
    if wounds >= 1:
        gash(grid, [(cx - 0.4, cy - 7.8), (cx - 2.2, cy - 4.6), (cx + 0.6, cy - 2.4), (cx - 1.0, cy + 1.2), (cx + 0.4, cy + 3.6)], body)
    if wounds >= 2:
        gash(grid, [(cx + 7.4, cy - 5.4), (cx + 5.2, cy - 2.8), (cx + 7.0, cy - 0.4), (cx + 4.8, cy + 3.2)], body)

    # The sun it rolls, held in front of it: a gold disc with a hot core.
    turn = frame * pi / 2.0
    sun = raster(pa.ellipse(4.0, cy + 5.5, 3.6, 3.6))
    paint(grid, sun, "a0")
    paint(grid, raster(pa.ellipse(3.6 + 0.6 * cos(turn), cy + 5.0 + 0.6 * sin(turn), 2.2, 2.2)) & sun, "a1")
    paint(grid, raster(pa.ellipse(3.2, cy + 4.6, 0.9, 0.9)) & sun, "a2")
    if wounds >= 2:
        gash(grid, [(2.0, cy + 2.2), (4.4, cy + 4.8), (3.4, cy + 6.4), (5.6, cy + 8.6)], sun)

    pa.outline_against(grid, wings - body, body, color="O")
    pa.outline_against(grid, legs - body, body, color="O")
    pa.outline_against(grid, sun, body | legs, color="O")
    eye(grid, cx - 9.8, cy + 1.6, 1.6, 1.5, pupil_dx=-0.5, wounds=wounds)
    pa.outer_outline(grid, W, H)
    return grid


TYPES = (locust, hawk, wyrmling, djinn, scarab)


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
