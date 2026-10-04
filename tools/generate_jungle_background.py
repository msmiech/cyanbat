# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the jungle the first stage scrolls past: one long, seamlessly repeating strip.

    uv run tools/generate_jungle_background.py

A South American rainforest grown over the ruins of something older and far bigger than anyone who
lives there now: wax palms and tree ferns, bromeliads perched on the trunks, moss hanging in beards
off the canopy - and through the trees, pale stone. Inca stonework in the near ruins, terraces of
fitted blocks with trapezoidal doorways, and in the haze behind, towers and broken bridges too big
to have been built for people.

The same contract as the cave's `generate_background.py`, and for the same reasons:

* **Seamless.** Tiled end to end while it scrolls, so every feature is built from noise that is
  periodic over the width, and every trunk, frond, wall and vine wraps its column index. The join is
  exact by construction, and checked at the end rather than trusted.
* **1440 wide**, two and a quarter framebuffers, so the jungle repeats every twelve seconds or so.
* **Darker than everything flying over it.** Lighter than the cave - it is a sunlit place, and its
  greens are lit - but nothing large goes above about a third brightness, and the middle of the frame,
  where the game is played, stays the darkest part of it. The jungle is green and its stone grey so
  the warm hostiles keep the only warm hues on screen, exactly as the cave's blue-grey keeps them.

Depth is layers, back to front:

1. **Light shafts** slanting down through gaps in the canopy, all leaning one way, as light from one
   sun does.
2. **The old ruins** in the haze: towers with trapezoidal windows, and bridges between them, broken.
   Paler than the trees in front of them, as anything far off in damp air is.
3. **A haze of distant trunks and wax palms**, the palms' small crowns high up under the canopy.
4. **The far jungle**: the terraces, fitted block by block, a doorway in each; tree ferns and palms;
   lianas slung between the trunks.
5. **The near jungle**: giants on buttress roots, bromeliads perched on them, moss hanging off the
   canopy, ferns and fallen blocks of the ruins on the floor.
6. **What frames the shot** in near-black: banana leaves and moss from the top, ferns, bromeliads
   and mossy boulders along the bottom.

What crosses the middle of the frame is far away and dim - trunks, towers, a few lianas - and never
mistaken for something to dodge. Each layer is lit down its left side and along the edge it shows to
the middle, where the cave's rock is lit: one light for the game. A few carved glyphs in the stone,
glowing caps on the floor and flowers on the vines play the cave's crystal: the one saturated thing in
the backdrop, used sparingly, tying it to the bat's cyan.
"""

import pathlib
import random
from math import cos, floor, pi, sin, sqrt

from PIL import Image

WIDTH = 1440
# The framebuffer's height: the strip is the whole of the frame from top to bottom.
HEIGHT = 360

PALETTE = {
    "void0": (12, 28, 22),
    "void1": (17, 37, 29),
    "void2": (22, 46, 36),
    # Light coming down through a gap in the canopy.
    "shaft": (32, 63, 46),
    # The old ruins, far off in the haze: paler than the trees in front of them.
    "ruin": (50, 72, 63),
    "ruin_lit": (64, 88, 75),
    "ruin_hole": (21, 41, 34),
    "haze": (27, 52, 39),
    "haze_lit": (35, 64, 47),
    "far": (30, 62, 41),
    "far_lit": (42, 80, 50),
    # The terraces' stone: lichen-grey, a darker grey in the joints, moss along the tops.
    "stone": (70, 86, 74),
    "stone_lit": (90, 108, 92),
    "stone_joint": (48, 61, 53),
    "stone_moss": (54, 94, 54),
    "mid": (21, 48, 30),
    "mid_lit": (34, 70, 41),
    # Blocks fallen from the ruins, on the near floor.
    "block": (50, 63, 55),
    "block_lit": (68, 84, 72),
    "near": (9, 27, 16),
    "near_lit": (17, 44, 24),
    "rock": (32, 43, 38),
    "rock_lit": (46, 60, 52),
    "glow": (36, 112, 100),
    "glow_hot": (92, 196, 170),
}

SEED = 20261004
OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "jungleBackground.png"


# --- periodic noise --------------------------------------------------------------------------------


def wrapped_noise(control_points, rng):
    """Smooth noise exactly periodic over [WIDTH]; see the cave generator for why it must be."""
    points = [rng.random() for _ in range(control_points)]

    def at(x):
        t = (x % WIDTH) / WIDTH * control_points
        index = int(t) % control_points
        frac = t - int(t)
        a = points[index]
        b = points[(index + 1) % control_points]
        frac = frac * frac * (3.0 - 2.0 * frac)
        return a + (b - a) * frac

    return at


def ridge(base_fraction, amplitude, harmonics, control_points, rng):
    """A per-column depth for one edge of one layer, in pixels, periodic over the width."""
    noise = wrapped_noise(control_points, rng)
    phases = [rng.uniform(0.0, 2.0 * pi) for _ in harmonics]
    weights = [rng.uniform(0.5, 1.0) for _ in harmonics]
    total = sum(weights)

    depths = []
    for x in range(WIDTH):
        swell = sum(
            weight * sin(2.0 * pi * harmonic * x / WIDTH + phase)
            for harmonic, phase, weight in zip(harmonics, phases, weights)
        ) / total
        depth = base_fraction + amplitude * (0.62 * swell + 0.76 * (noise(x) - 0.5))
        depths.append(max(2.0, depth * HEIGHT))
    return depths


def spread(count, rng, jitter=0.35):
    """Centers spread evenly-ish round the strip: a jittered grid, so no stretch is bare or a fence."""
    spacing = WIDTH / count
    return [(index * spacing + rng.uniform(-jitter, jitter) * spacing) % WIDTH for index in range(count)]


# --- a layer's silhouette, as a mask -----------------------------------------------------------------


class Mask:
    """The pixels one layer covers. Every write wraps its column, so nothing can break the seam."""

    def __init__(self):
        self.bits = bytearray(WIDTH * HEIGHT)

    def set(self, x, y):
        y = floor(y)
        if 0 <= y < HEIGHT:
            self.bits[y * WIDTH + floor(x) % WIDTH] = 1

    def clear(self, x, y):
        y = floor(y)
        if 0 <= y < HEIGHT:
            self.bits[y * WIDTH + floor(x) % WIDTH] = 0

    def has(self, x, y):
        if y < 0 or y >= HEIGHT:
            return False
        return self.bits[y * WIDTH + x % WIDTH] == 1

    def disc(self, cx, cy, radius):
        r = int(radius) + 1
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= radius * radius:
                    self.set(cx + dx, cy + dy)

    def stroke(self, points, r_start, r_end):
        """A tapering stroke along [points], stamped as discs: a trunk, a frond, a vine."""
        lengths = [0.0]
        for (ax, ay), (bx, by) in zip(points, points[1:]):
            lengths.append(lengths[-1] + sqrt((bx - ax) ** 2 + (by - ay) ** 2))
        total = lengths[-1] or 1.0
        for (ax, ay), (bx, by), la, lb in zip(points, points[1:], lengths, lengths[1:]):
            steps = int(max(abs(bx - ax), abs(by - ay))) + 1
            for step in range(steps + 1):
                t = step / steps
                along = (la + (lb - la) * t) / total
                self.disc(ax + (bx - ax) * t, ay + (by - ay) * t, r_start + (r_end - r_start) * along)

    def polygon(self, points):
        """Fills [points], sampled at each pixel's center; columns past either end wrap."""
        ys = [p[1] for p in points]
        for y in range(max(0, floor(min(ys))), min(HEIGHT, floor(max(ys)) + 1)):
            py = y + 0.5
            crossings = []
            j = len(points) - 1
            for i, (xi, yi) in enumerate(points):
                xj, yj = points[j]
                if (yi > py) != (yj > py):
                    crossings.append(xi + (py - yi) * (xj - xi) / (yj - yi))
                j = i
            crossings.sort()
            for left, right in zip(crossings[::2], crossings[1::2]):
                for x in range(floor(left + 0.5), floor(right + 0.5)):
                    self.set(x, y)

    def column_down(self, x, top, bottom):
        for y in range(max(0, floor(top)), min(HEIGHT, floor(bottom))):
            self.set(x, y)


def curve(start, control, end, steps=12):
    """Points along a quadratic curve from [start] to [end], bowed toward [control]."""
    out = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        out.append((
            u * u * start[0] + 2 * u * t * control[0] + t * t * end[0],
            u * u * start[1] + 2 * u * t * control[1] + t * t * end[1],
        ))
    return out


def normal(points, index):
    a = points[max(0, index - 1)]
    b = points[min(len(points) - 1, index + 1)]
    dx, dy = b[0] - a[0], b[1] - a[1]
    span = sqrt(dx * dx + dy * dy) or 1.0
    return -dy / span, dx / span


def trapezoid(mask, cx, bottom, bottom_width, top_width, height):
    """The Inca shape, of every doorway, niche and window they built: narrower at the top."""
    mask.polygon([
        (cx - bottom_width / 2, bottom), (cx - top_width / 2, bottom - height),
        (cx + top_width / 2, bottom - height), (cx + bottom_width / 2, bottom),
    ])


# --- the vegetation ----------------------------------------------------------------------------------


def canopy(mask, line, leaves, leaf_width, leaf_length, rng):
    """
    The canopy hanging from the top edge down to [line], and drip-tip leaves hanging off it, each
    tilted a little one way or the other, so its underside reads as a fringe of leaves rather than
    as a wood's rounded clumps - or as a row of teeth, which leaves hanging plumb come out as.
    """
    for x in range(WIDTH):
        mask.column_down(x, 0, line[x])
    for _ in range(leaves):
        x = rng.uniform(0, WIDTH)
        tilt = rng.uniform(-0.45, 0.45)
        leaf(mask, (x, line[int(x) % WIDTH] - 3), pi / 2 + tilt, rng.uniform(*leaf_length),
             rng.uniform(*leaf_width), droop=0.0)


def ground(mask, line):
    for x in range(WIDTH):
        mask.column_down(x, HEIGHT - line[x], HEIGHT)


def leaf(mask, base, angle, length, width, droop):
    """
    One leaf from [base] along [angle], about [width] at its widest: rounded at the stalk, broadest
    a third of the way along and drawn out into a point - a drip tip, which is how a rainforest leaf
    sheds the rain. It bends under its own weight by [droop] of its length.
    """
    rib = []
    for step in range(17):
        t = step / 16
        rib.append((
            base[0] + cos(angle) * length * t,
            base[1] + sin(angle) * length * t + droop * length * t * t,
        ))
    upper, lower = [], []
    for index, (x, y) in enumerate(rib):
        t = index / (len(rib) - 1)
        nx, ny = normal(rib, index)
        half = width * min(1.0, t * 3.0) ** 0.5 * (1.0 - t) ** 1.3 * 1.6
        upper.append((x + nx * half, y + ny * half))
        lower.append((x - nx * half, y - ny * half))
    mask.polygon(upper + lower[::-1])


def trunk(mask, center, half_width, top, rng, buttress=0.0):
    """
    A trunk swaying slowly down the frame from [top] to the bottom. A dead-straight trunk reads as a
    pillar, and a colonnade is a building. With a [buttress], its foot flares out into planks as far
    as that many of its own widths either side: the roots a rainforest giant stands on. Returns where
    along it its middle is, for what grows on it.
    """
    sway = rng.uniform(1.0, 2.2) * half_width * 0.45
    phase = rng.uniform(0.0, 2.0 * pi)

    def x_at(y):
        return center + sway * sin(2.0 * pi * y / (HEIGHT * 1.7) + phase)

    for y in range(max(0, int(top)), HEIGHT):
        low = max(0.0, (y - HEIGHT * 0.8) / (HEIGHT * 0.2))
        width = half_width * (1.0 + 0.5 * low * low)
        middle = x_at(y)
        for offset in range(-int(width) - 1, int(width) + 2):
            if abs(offset) <= width:
                mask.set(middle + offset, y)

    if buttress > 0.0:
        foot = x_at(HEIGHT - 1)
        start = HEIGHT * rng.uniform(0.66, 0.72)
        for side in (-1.0, 1.0):
            reach = half_width * buttress * rng.uniform(0.8, 1.2)
            # A plank: thin where it leaves the trunk, broad where it meets the ground, its top edge
            # sagging a little, as the wood does between trunk and root.
            edge = curve(
                (x_at(start) + side * half_width * 0.4, start),
                (foot + side * (half_width + reach * 0.35), HEIGHT * 0.9),
                (foot + side * (half_width + reach), HEIGHT + 1),
            )
            mask.polygon(edge + [(foot + side * half_width * 0.5, HEIGHT + 1)])
        # A third plank, square on, narrower: so the foot reads as fins rather than as a cone.
        reach = half_width * buttress * 0.45
        mask.polygon([
            (foot - half_width * 0.3, HEIGHT * 0.84),
            (foot + half_width * 0.3, HEIGHT * 0.84),
            (foot + reach * 0.25, HEIGHT + 1),
            (foot - reach * 0.25, HEIGHT + 1),
        ])
    return x_at


def crown(mask, top, fronds, length_range, droop_range, scale, rng, both_sides=False):
    """
    A crown of fronds out of [top], fanned from drooping left to drooping right, each combed with
    leaflets underneath - or on both sides, for a tree fern's, which are broader and lacier.
    """
    for index in range(fronds):
        angle = pi + (index + rng.uniform(-0.3, 0.3)) / (fronds - 1) * pi
        length = rng.uniform(*length_range) * scale
        droop = rng.uniform(*droop_range)
        points = []
        for step in range(13):
            t = step / 12
            points.append((
                top[0] + cos(angle) * length * t,
                top[1] + sin(angle) * length * t + droop * length * t * t,
            ))
        mask.stroke(points, 1.3 * scale, 0.5)
        for step in range(2, 12):
            t = step / 12
            x, y = points[step]
            leaflet = (2.0 + 5.0 * sin(pi * t)) * scale
            back = -0.35 if cos(angle) > 0 else 0.35
            mask.stroke([(x, y), (x + back * leaflet, y + leaflet)], 0.6, 0.4)
            if both_sides:
                mask.stroke([(x, y), (x - back * leaflet * 0.8, y - leaflet * 0.6)], 0.6, 0.4)


def wax_palm(mask, base_x, base_y, height, lean, rng, scale=1.0):
    """
    A wax palm, the Andes' own: a trunk impossibly tall and slender, and a small crown of fronds at
    the very top of it, high up under the canopy.
    """
    top = (base_x + lean, base_y - height)
    stem = curve((base_x, base_y), (base_x + lean * 0.15, base_y - height * 0.55), top, steps=24)
    mask.stroke(stem, 2.2 * scale, 1.4 * scale)
    crown(mask, top, rng.randint(7, 9), (18.0, 26.0), (0.55, 0.9), scale, rng)


def tree_fern(mask, base_x, base_y, height, rng, scale=1.0):
    """
    A tree fern: a shaggy trunk no taller than a house, and a great lacy crown of fronds arching out
    of its top and down - the plant the cloud forest is made of.
    """
    top = (base_x + rng.uniform(-6, 6), base_y - height)
    stem = curve((base_x, base_y), (base_x - (top[0] - base_x) * 0.3, base_y - height * 0.5), top, steps=16)
    mask.stroke(stem, 2.6 * scale, 2.0 * scale)
    crown(mask, top, rng.randint(9, 11), (22.0, 32.0), (0.7, 1.1), scale, rng, both_sides=True)


def bromeliad(mask, base_x, base_y, size, rng):
    """
    A bromeliad: a rosette of stiff, pointed leaves fanning up and out of one heart, perched on a
    branch or a trunk or the floor - the jungle's own, and found nowhere else.
    """
    leaves = rng.randint(7, 9)
    for index in range(leaves):
        angle = pi + (index + 0.5) / leaves * pi + rng.uniform(-0.12, 0.12)
        length = size * rng.uniform(0.7, 1.0) * (0.6 + 0.4 * sin(pi * (index + 0.5) / leaves))
        tip = (base_x + cos(angle) * length, base_y + sin(angle) * length * 0.8)
        bend_ = (base_x + cos(angle) * length * 0.5, base_y + sin(angle) * length * 0.75 - length * 0.1)
        mask.stroke(curve((base_x, base_y), bend_, tip, steps=8), size * 0.12 + 0.6, 0.5)


def moss_beard(mask, x, top, length, rng):
    """
    Moss hanging in a beard off the canopy - old man's beard, Spanish moss: a bunch of fine strands,
    longest in the middle, swaying together.
    """
    strands = rng.randint(4, 7)
    phase = rng.uniform(0, 2 * pi)
    for index in range(strands):
        offset = (index - (strands - 1) / 2) * 1.6
        reach = length * (1.0 - abs(offset) / (strands * 1.2)) * rng.uniform(0.75, 1.0)
        points = [
            (x + offset * (1.0 + t / 16) + sin(phase + t * 0.5) * 1.6, top + reach * t / 12)
            for t in range(13)
        ]
        mask.stroke(points, 0.6, 0.5)


def liana_loop(mask, line, x1, x2, sag, thickness):
    """A liana slung in a loop between two points on the canopy's underside."""
    y1 = line[int(x1) % WIDTH]
    y2 = line[int(x2) % WIDTH]
    points = []
    for step in range(41):
        t = step / 40
        points.append((x1 + (x2 - x1) * t, y1 + (y2 - y1) * t + sag * 4.0 * t * (1.0 - t)))
    mask.stroke(points, thickness, thickness)


def liana_drop(mask, line, x, length, thickness, rng):
    """A liana dangling straight down off the canopy, swaying, with a knot of leaves at its end."""
    top = line[int(x) % WIDTH] - 2
    phase = rng.uniform(0, 2 * pi)
    points = [(x + sin(phase + t * 0.9) * 2.5, top + length * t / 12) for t in range(13)]
    mask.stroke(points, thickness, thickness * 0.8)
    end = points[-1]
    for side in (-1.0, 1.0):
        mask.stroke([end, (end[0] + side * 3.5, end[1] + 2.5)], 1.2, 0.5)
    return end


def fern(mask, base_x, base_y, size, rng):
    """A fern: fronds arching up and out of one root, combed with leaflets on their upper side."""
    fronds = rng.randint(4, 6)
    for index in range(fronds):
        side = -1.0 if index % 2 == 0 else 1.0
        spread_ = (0.25 + 0.75 * (index // 2) / max(1, fronds // 2)) * side
        height = size * rng.uniform(0.75, 1.0) * (1.0 - 0.35 * abs(spread_))
        reach = size * spread_ * rng.uniform(0.7, 1.0)
        tip = (base_x + reach * 1.3, base_y - height * 0.35)
        points = curve((base_x, base_y), (base_x + reach * 0.5, base_y - height * 1.3), tip, steps=14)
        mask.stroke(points, 1.4, 0.5)
        for step in range(2, 13):
            x, y = points[step]
            t = step / 14
            leaflet = size * 0.16 * sin(pi * min(1.0, t * 1.2))
            mask.stroke([(x, y), (x + side * leaflet * 0.5, y - leaflet)], 0.6, 0.4)


def banana_leaf(mask, root_x, root_y, length, width, toward, rng):
    """
    A banana leaf hanging from the canopy: a long blade off a midrib that arches down and to
    [toward], widest in its middle, and torn here and there along its veins, which is what makes it
    read as a banana leaf rather than as a feather.
    """
    tip = (root_x + toward * length * 0.8, root_y + length * 0.55)
    rib = curve((root_x, root_y), (root_x + toward * length * 0.55, root_y - length * 0.05), tip, steps=24)
    upper, lower = [], []
    for index, (x, y) in enumerate(rib):
        t = index / (len(rib) - 1)
        nx, ny = normal(rib, index)
        half = width * sin(pi * t) ** 0.6
        upper.append((x + nx * half, y + ny * half))
        lower.append((x - nx * half, y - ny * half))
    mask.polygon(upper + lower[::-1])
    mask.stroke(rib[:4], 1.5, 1.0)

    # Tears along the veins, from the edge most of the way in to the rib, slanting toward the tip.
    for _ in range(rng.randint(4, 6)):
        index = rng.randint(8, len(rib) - 4)
        x, y = rib[index]
        nx, ny = normal(rib, index)
        side = rng.choice((-1.0, 1.0))
        t = index / (len(rib) - 1)
        half = width * sin(pi * t) ** 0.6
        dx, dy = rib[index + 1][0] - rib[index - 1][0], rib[index + 1][1] - rib[index - 1][1]
        span = sqrt(dx * dx + dy * dy) or 1.0
        for k in range(int(half * 3) + 4):
            depth = half + 1.5 - k * 0.34
            if depth < 1.2:
                break
            slant = (half - depth) * 0.6
            mask.clear(x + side * nx * depth + dx / span * slant, y + side * ny * depth + dy / span * slant)


def boulder(mask, cx, bottom, half_width, height):
    """A rounded stone half sunk in the floor, flatter than it is wide."""
    for dx in range(-int(half_width) - 1, int(half_width) + 2):
        away = abs(dx) / half_width
        if away >= 1.0:
            continue
        rise = height * sqrt(1.0 - away * away) ** 0.8
        mask.column_down(cx + dx, bottom - rise, bottom + 1)


# --- the ruins ---------------------------------------------------------------------------------------


def tower(body, holes, center, base, height, half_width, rng):
    """
    One of the old towers, far off in the haze: its walls leaning in as they rise, a ledge round it
    every few storeys, its top broken off in steps, and trapezoidal windows in rows up it.
    """
    top = base - height
    lean = half_width * 0.1
    # The broken top: whole courses missing, more on one side than the other.
    break_left = rng.uniform(0, height * 0.2)
    break_right = rng.uniform(0, height * 0.2)
    ledges = []
    y = base - rng.uniform(40, 50)
    while y > top + 20:
        ledges.append(y)
        y -= rng.uniform(42, 56)
    for dx in range(-int(half_width) - 4, int(half_width) + 5):
        t = (dx + half_width) / (2 * half_width)
        broken = round((break_left + (break_right - break_left) * min(1.0, max(0.0, t))) / 6.0) * 6.0
        for y in range(max(0, floor(top + broken)), min(HEIGHT, floor(base))):
            inset = -lean * (base - y) / height
            ledge = any(0 <= y - l < 3 for l in ledges)
            if abs(dx) <= half_width + inset + (3 if ledge else 0):
                body.set(center + dx, y)
    # Windows in rows between the ledges, each a trapezoid.
    for row in [base - 8] + [l - 6 for l in ledges]:
        if row - 12 < top + max(break_left, break_right):
            continue
        across = 3 if half_width > 26 else 2
        for index in range(across):
            offset = (index - (across - 1) / 2) * half_width * 0.62
            trapezoid(holes, center + offset, row, 7.0, 4.5, 12.0)


def bridge(body, x_from, toward, length, y):
    """
    What is left of a bridge out of a tower: a deck carried on an arcade of round arches, reaching
    [length] toward the next tower and broken off there, its end ragged, the rest long since fallen.
    """
    deck = 6
    span = 26
    for step in range(int(length)):
        x = x_from + toward * step
        # The broken end: the deck crumbling back in steps over its last few pixels.
        ragged = max(0, step - (length - 10)) * 0.9
        for yy in range(floor(y + ragged), floor(y + deck)):
            body.set(x, yy)
        phase = step % span
        # Piers between the arches, each arch a half circle sprung off them.
        arch = 0.0 if phase < 4 else sqrt(max(0.0, 1.0 - ((phase - 4 - (span - 4) / 2) / ((span - 4) / 2)) ** 2))
        bottom = y + deck + (span - 4) / 2 * (1.0 - arch) + (40 if phase < 4 else 0)
        if step < length - 6:
            for yy in range(floor(y + deck), floor(min(bottom, y + deck + 40))):
                body.set(x, yy)



def terrace(pixels, foot, left, width, steps, step_height, rng, glyphs):
    """
    A stepped terrace of fitted stone standing on the floor at [foot]: [steps] walls one above another,
    each set back from the one below, their courses laid with staggered joints and moss along their
    tops, and a trapezoidal doorway in the bottom one, dark inside. Painted straight onto the strip,
    between the far jungle and what grows in front of it.
    """
    setback = width * 0.14
    door = left + width * rng.uniform(0.3, 0.7)
    for step in range(steps):
        bottom = foot - step * step_height
        top = bottom - step_height
        x0 = left + step * setback
        x1 = left + width - step * setback * rng.uniform(0.6, 1.0)
        # Courses: each its own height, its joints placed afresh, so no two rows line up.
        course_tops = []
        y = top
        while y < bottom:
            course_tops.append(y)
            y += rng.uniform(4.5, 6.5)
        joints = {}
        for course in course_tops:
            x = x0 + rng.uniform(0, 8)
            row = []
            while x < x1:
                row.append(x)
                x += rng.uniform(7, 13)
            joints[course] = row
        for yy in range(max(0, floor(top)), min(HEIGHT, floor(bottom))):
            # A batter: the wall leans in a little as it rises, as every Inca wall does.
            lean = (bottom - yy) * 0.12
            course = max((c for c in course_tops if c <= yy + 0.5), default=course_tops[0])
            for xx in range(floor(x0 + lean), floor(x1 - lean) + 1):
                if yy < top + 2:
                    color = "stone_moss"
                elif xx < x0 + lean + 1.5:
                    color = "stone_lit"
                elif floor(course) == yy or any(abs(xx - j) < 0.5 for j in joints[course]):
                    color = "stone_joint"
                else:
                    color = "stone"
                pixels[xx % WIDTH, yy] = PALETTE[color]
        if step == 0:
            doorway = Mask()
            trapezoid(doorway, door, bottom, 13.0, 9.0, step_height * 0.85)
            for yy in range(max(0, floor(top)), min(HEIGHT, floor(bottom) + 1)):
                for xx in range(floor(door - 9), floor(door + 9)):
                    if doorway.has(xx, yy):
                        pixels[xx % WIDTH, yy] = PALETTE["ruin_hole"]
            # A glyph carved over the lintel, glowing faintly: the ruins are not quite dead.
            glyphs.append((door, top + 2))


# --- the layers --------------------------------------------------------------------------------------


def ruins(rng):
    body = Mask()
    holes = Mask()
    towers = []
    for center in spread(4, rng, jitter=0.2):
        half = rng.uniform(22, 32)
        height = rng.uniform(170, 240)
        tower(body, holes, center, HEIGHT * 0.8, height, half, rng)
        towers.append((center, half))
    # Two bridges, each out of one tower toward the next and broken off well short of it, high up
    # under the canopy rather than across the middle of the frame.
    for index in rng.sample(range(len(towers)), 2):
        center, half = towers[index]
        toward = rng.choice((-1.0, 1.0))
        gap = WIDTH / len(towers) - 2 * half
        bridge(body, center + toward * (half - 1), toward, gap * rng.uniform(0.35, 0.6),
               rng.uniform(78, 100))
    return body, holes


def haze_layer(rng):
    mask = Mask()
    line = ridge(0.24, 0.05, (1, 2), 6, rng)
    canopy(mask, line, 40, (4.0, 9.0), (6.0, 16.0), rng)
    ground(mask, ridge(0.22, 0.04, (1,), 5, rng))
    for center in spread(11, rng):
        trunk(mask, center, rng.uniform(2.5, 5.0), 0, rng)
    for center in spread(10, rng, jitter=0.45):
        wax_palm(mask, center, HEIGHT * 0.8, rng.uniform(170, 220), rng.uniform(-18, 18), rng, scale=0.8)
    for _ in range(10):
        x1 = rng.uniform(0, WIDTH)
        liana_loop(mask, line, x1, x1 + rng.uniform(40, 110), rng.uniform(30, 90), 0.7)
    return mask


def far_layer(rng):
    back = Mask()
    front = Mask()
    line = ridge(0.17, 0.05, (1, 3), 8, rng)
    canopy(back, line, 70, (4.0, 8.0), (6.0, 14.0), rng)
    floor_line = ridge(0.16, 0.04, (2, 3), 9, rng)
    ground(back, floor_line)
    for center in spread(7, rng):
        trunk(back, center, rng.uniform(5.0, 8.0), 0, rng, buttress=1.4)
    for center in spread(6, rng, jitter=0.45):
        base = HEIGHT - floor_line[int(center) % WIDTH] + 4
        wax_palm(back, center, base, rng.uniform(190, 230), rng.uniform(-30, 30), rng)
    for _ in range(8):
        x1 = rng.uniform(0, WIDTH)
        liana_loop(back, line, x1, x1 + rng.uniform(50, 130), rng.uniform(40, 110), 0.8)
    for _ in range(10):
        liana_drop(back, line, rng.uniform(0, WIDTH), rng.uniform(25, 110), 0.8, rng)
    for _ in range(12):
        x = rng.uniform(0, WIDTH)
        moss_beard(back, x, line[int(x) % WIDTH] - 2, rng.uniform(12, 28), rng)

    # The terraces stand on the floor among the trees; what grows on them and in front of them is
    # painted over them.
    terraces = []
    for left in spread(3, rng, jitter=0.3):
        width = rng.uniform(110, 170)
        foot = HEIGHT - floor_line[int(left + width / 2) % WIDTH] + 6
        steps = rng.randint(2, 3)
        step_height = rng.uniform(17, 22)
        terraces.append((foot, left, width, steps, step_height))
        # Tree ferns grown up on the top step, rooted in its moss.
        for _ in range(2):
            x = left + rng.uniform(0.3, 0.7) * width
            tree_fern(front, x, foot - steps * step_height + 1, rng.uniform(26, 40), rng, scale=0.9)
    for center in spread(8, rng, jitter=0.45):
        tree_fern(front, center, HEIGHT - floor_line[int(center) % WIDTH] + 4, rng.uniform(40, 70), rng)
    for _ in range(30):
        x = rng.uniform(0, WIDTH)
        fern(front, x, HEIGHT - floor_line[int(x) % WIDTH] + 6, rng.uniform(10, 16), rng)
    return back, terraces, front


def mid_layer(rng):
    mask = Mask()
    blocks = Mask()
    line = ridge(0.12, 0.04, (2, 5), 13, rng)
    canopy(mask, line, 95, (3.5, 7.0), (6.0, 15.0), rng)
    floor_line = ridge(0.11, 0.035, (3, 5), 13, rng)
    ground(mask, floor_line)
    for center in spread(5, rng):
        half = rng.uniform(9.0, 13.0)
        x_at = trunk(mask, center, half, 0, rng, buttress=2.2)
        # Bromeliads perched up the trunk, on the side it leans away from.
        for height in (rng.uniform(0.3, 0.45), rng.uniform(0.5, 0.62)):
            y = HEIGHT * height
            side = rng.choice((-1.0, 1.0))
            bromeliad(mask, x_at(y) + side * half, y, rng.uniform(9, 13), rng)
    for _ in range(7):
        x1 = rng.uniform(0, WIDTH)
        liana_loop(mask, line, x1, x1 + rng.uniform(70, 160), rng.uniform(30, 70), 1.1)
    ends = [liana_drop(mask, line, rng.uniform(0, WIDTH), rng.uniform(20, 70), 1.1, rng) for _ in range(9)]
    for _ in range(16):
        x = rng.uniform(0, WIDTH)
        moss_beard(mask, x, line[int(x) % WIDTH] - 2, rng.uniform(14, 34), rng)
    for _ in range(30):
        x = rng.uniform(0, WIDTH)
        fern(mask, x, HEIGHT - floor_line[int(x) % WIDTH] + 2, rng.uniform(13, 22), rng)
    # Blocks fallen from the ruins, half sunk in the floor: squared, tilted a little, one on another.
    for x in spread(9, rng, jitter=0.45):
        foot = HEIGHT - floor_line[int(x) % WIDTH] + 5
        w = rng.uniform(12, 20)
        h = rng.uniform(8, 12)
        tilt = rng.uniform(-0.25, 0.25)
        corners = [(-w / 2, 0), (-w / 2, -h), (w / 2, -h), (w / 2, 0)]
        blocks.polygon([(x + cx * cos(tilt) - cy * sin(tilt), foot + cx * sin(tilt) + cy * cos(tilt))
                        for cx, cy in corners])
        if rng.random() < 0.5:
            trapezoid(blocks, x + rng.uniform(-3, 3), foot - h + 1, w * 0.7, w * 0.55, h * 0.7)
    top = [HEIGHT - int(floor_line[x]) for x in range(WIDTH)]
    return mask, blocks, ends, top


def near_layer(rng):
    mask = Mask()
    rocks = Mask()
    line = ridge(0.075, 0.03, (3, 7), 21, rng)
    canopy(mask, line, 70, (4.0, 8.0), (8.0, 16.0), rng)
    floor_line = ridge(0.06, 0.03, (4, 7), 21, rng)
    ground(mask, floor_line)
    for root in spread(9, rng, jitter=0.4):
        length = rng.uniform(60, 92)
        banana_leaf(mask, root, line[int(root) % WIDTH] - 4, length, length * 0.17,
                    rng.choice((-1.0, 1.0)), rng)
    for _ in range(12):
        x = rng.uniform(0, WIDTH)
        moss_beard(mask, x, line[int(x) % WIDTH] - 2, rng.uniform(18, 40), rng)
    # Mossy boulders on the floor, the ferns growing up round them.
    for x in spread(10, rng, jitter=0.45):
        foot = HEIGHT - floor_line[int(x) % WIDTH] + 6
        boulder(rocks, x, foot, rng.uniform(12, 22), rng.uniform(9, 15))
    for x in spread(20, rng, jitter=0.45):
        fern(mask, x, HEIGHT - floor_line[int(x) % WIDTH] + 3, rng.uniform(24, 36), rng)
    for x in spread(8, rng, jitter=0.45):
        bromeliad(mask, x, HEIGHT - floor_line[int(x) % WIDTH] + 2, rng.uniform(16, 24), rng)
    return mask, rocks


# --- painting ----------------------------------------------------------------------------------------


def paint(pixels, mask, body, lit):
    """
    Paints a layer: its body, and lit wherever it shows an edge to the left or to the open middle of
    the frame - the underside of what hangs, the top of what stands.
    """
    middle = HEIGHT // 2
    for y in range(HEIGHT):
        toward = 1 if y < middle else -1
        for x in range(WIDTH):
            if not mask.has(x, y):
                continue
            edge = (
                not mask.has(x - 1, y)
                or not mask.has(x - 2, y)
                or not mask.has(x, y + toward)
                or not mask.has(x, y + 2 * toward)
            )
            pixels[x, y] = lit if edge else body


def paint_rocks(pixels, mask, body, lit, moss):
    """Stone lit from the left, with moss along its top: the floor's boulders, the fallen blocks."""
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if not mask.has(x, y):
                continue
            if not mask.has(x, y - 1) or not mask.has(x, y - 2):
                pixels[x, y] = moss
            elif not mask.has(x - 1, y) or not mask.has(x - 2, y):
                pixels[x, y] = lit
            else:
                pixels[x, y] = body


def shafts(pixels, rng):
    """
    Light slanting down through gaps in the canopy, laid over the air before anything stands in it.
    Each is a slanted band that fades out toward the floor - broken off in steps, since nothing here
    is blended - and every one leans the same way, as light from one sun does.
    """
    lean = 0.42
    for center in spread(6, rng, jitter=0.3):
        width = rng.uniform(18, 34)
        reach = HEIGHT * rng.uniform(0.62, 0.85)
        for y in range(HEIGHT):
            if y > reach:
                break
            # Narrowing as it goes down, so the band ends rather than being cut off.
            half = width * (1.0 - 0.5 * y / reach) / 2
            middle = center + lean * y
            for x in range(floor(middle - half), floor(middle + half) + 1):
                # Every other pixel over its last part, then every fourth: a fade made of steps.
                fade = (y - reach * 0.6) / (reach * 0.4)
                if fade > 0.5 and (x + y) % 4 != 0:
                    continue
                if fade > 0.0 and (x + y) % 2 != 0:
                    continue
                pixels[x % WIDTH, y] = PALETTE["shaft"]


def glyph(pixels, x, y):
    """A small carved sign in the stone, glowing: a ring with a stroke through it."""
    cx, cy = floor(x), floor(y)
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        pixels[(cx + dx) % WIDTH, cy + dy] = PALETTE["glow"]
    pixels[cx % WIDTH, cy] = PALETTE["glow_hot"]


def main() -> int:
    rng = random.Random(SEED)
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()

    for y in range(HEIGHT):
        away = abs(y - HEIGHT * 0.42) / (HEIGHT / 2)
        tone = "void2" if away < 0.45 else ("void1" if away < 0.8 else "void0")
        for x in range(WIDTH):
            pixels[x, y] = PALETTE[tone]

    shafts(pixels, rng)

    body, holes = ruins(rng)
    paint(pixels, body, PALETTE["ruin"], PALETTE["ruin_lit"])
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if holes.has(x, y) and body.has(x, y):
                pixels[x, y] = PALETTE["ruin_hole"]

    paint(pixels, haze_layer(rng), PALETTE["haze"], PALETTE["haze_lit"])

    back, terraces, front = far_layer(rng)
    paint(pixels, back, PALETTE["far"], PALETTE["far_lit"])
    glyphs = []
    for foot, left, width, steps, step_height in terraces:
        terrace(pixels, foot, left, width, steps, step_height, rng, glyphs)
    paint(pixels, front, PALETTE["far"], PALETTE["far_lit"])
    for x, y in glyphs:
        glyph(pixels, x, y)

    mid, blocks, vine_ends, mid_floor = mid_layer(rng)
    paint(pixels, mid, PALETTE["mid"], PALETTE["mid_lit"])
    paint_rocks(pixels, blocks, PALETTE["block"], PALETTE["block_lit"], PALETTE["mid_lit"])

    # Glowing caps along the mid layer's floor and flowers at the ends of its vines, seeded so the
    # jungle is the same every run.
    scatter = random.Random(SEED + 1)
    for _ in range(WIDTH // 36):
        x = scatter.randrange(WIDTH)
        y = mid_floor[x]
        size = scatter.randint(1, 2)
        for dx in range(-size, size + 1):
            pixels[(x + dx) % WIDTH, y - 1] = PALETTE["glow"]
        for dx in range(-size + 1, size):
            pixels[(x + dx) % WIDTH, y - 2] = PALETTE["glow_hot"]
        pixels[x % WIDTH, y] = PALETTE["glow"]
    for x, y in vine_ends[::2]:
        cx, cy = floor(x) % WIDTH, floor(y) + 3
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, 1)):
            pixels[(cx + dx) % WIDTH, min(HEIGHT - 1, cy + dy)] = PALETTE["glow"]
        pixels[cx, min(HEIGHT - 1, cy)] = PALETTE["glow_hot"]

    near, rocks = near_layer(rng)
    paint(pixels, near, PALETTE["near"], PALETTE["near_lit"])
    paint_rocks(pixels, rocks, PALETTE["rock"], PALETTE["rock_lit"], PALETTE["near_lit"])

    image.save(OUTPUT)
    print(f"{OUTPUT} ({WIDTH}x{HEIGHT})")

    def column_step(left, right):
        return sum(
            sum(abs(a - b) for a, b in zip(image.getpixel((left, y)), image.getpixel((right, y))))
            for y in range(HEIGHT)
        ) / HEIGHT

    seam = column_step(WIDTH - 1, 0)
    interior = sum(column_step(x, x + 1) for x in range(0, WIDTH - 1, 7)) / len(range(0, WIDTH - 1, 7))
    print(f"mean |edge-to-edge| {seam:.2f} vs mean neighboring-column step {interior:.2f} (of 765)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
