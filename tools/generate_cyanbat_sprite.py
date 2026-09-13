# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the player's sprite sheet: six frames of one wing-beat.

    uv run tools/generate_cyanbat_sprite.py

Kept as a script for the same reason `generate_shot_sprite.py` is - the art stays editable. The
shot is small enough to spell out pixel by pixel; a 45x40 bat drawn six times over is not, so this
describes the *animal* instead: a skeleton of bones, shapes hung off it, and a flap angle that is
the only thing which changes between frames. Redrawing a wing by hand six times is how frames end
up not quite matching each other.

Rendering is deliberately hard-edged. Shapes are sampled at 3x3 per pixel and thresholded, so the
result is flat pixel art with no anti-aliased fringe - the old sheet had 2169 distinct colors in
97x40 pixels, most of them ghosts of a resize, and this has as many colors as the palette below.

The sheet is 6 frames of 45x40, laid out left to right, which is what `EntityFactory.createBat`
addresses through `SpriteComponent.srcX`.
"""

import pathlib
from math import cos, hypot, radians, sin

from PIL import Image

FRAME_WIDTH = 45
FRAME_HEIGHT = 40
FRAME_COUNT = 6

# Supersampling used to decide whether a pixel is inside a shape. Coverage is thresholded rather
# than blended: the point is accurate silhouettes, not soft ones.
SUBSAMPLES = 3
COVERAGE_THRESHOLD = 0.5

# Cyan and deep blue, because that is what the enemies, the cave and the bat's own wake already
# are. The warm dot on the tail fin is the single exception, carried over from the old artwork.
PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (7, 20, 43, 255),      # outline
    "B": (13, 43, 82, 255),     # wing bones
    "M": (38, 112, 178, 255),   # wing membrane
    "N": (72, 160, 214, 255),   # wing membrane, lit panels
    "F": (24, 70, 124, 255),    # far wing, flat - depth is the whole of what it says
    "d": (23, 73, 138, 255),    # back
    "b": (41, 123, 195, 255),
    "c": (75, 185, 229, 255),
    "y": (175, 233, 247, 255),  # belly
    "w": (240, 253, 255, 255),  # highlight
    "e": (246, 252, 255, 255),  # eye
    "p": (9, 19, 37, 255),      # pupil
    "f": (47, 195, 223, 255),   # tail fin
    "g": (250, 226, 92, 255),   # the warm dot on it
}

# One beat, sampled six times. Angle is degrees above straight-back, so 90 is the wing held
# vertically; extension is how far the fingers are spread. They are not in phase, and that is the
# whole reason six frames beat two: a bat drives down with the wing open and recovers with it
# folded, so no two frames here are the same pose mirrored.
#
# `bob` is the body riding its own downstroke, one pixel either side of center.
FLAP = (
    # angle  extension  bob
    (84.0, 0.84, 1),   # top of the stroke, opening out
    (64.0, 1.00, 0),   # driving down, fully spread
    (40.0, 1.00, -1),
    (18.0, 0.94, -1),  # bottom of the stroke, swept back over the tail
    (42.0, 0.74, 0),   # recovering, folded in
    (68.0, 0.70, 1),
)

# The body, in frame coordinates before the bob is applied. The bat faces right, which is the only
# direction it ever flies.
#
# The shoulder sits on the crest of the back rather than inside the torso, which is what gives the
# wing a frame to open into: the animal is drawn low and long so that the top half of the 45x40
# frame belongs to the wing alone.
SHOULDER = (30.0, 22.0)
HIP = (21.0, 27.0)


# --- geometry ------------------------------------------------------------------------------------


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def capsule(p, q, r_start, r_end):
    """A segment with a radius that tapers from end to end - a limb, a tail, a finger bone."""
    px, py = p
    qx, qy = q
    dx, dy = qx - px, qy - py
    span = dx * dx + dy * dy

    def inside(x, y):
        t = 0.0 if span == 0 else max(0.0, min(1.0, ((x - px) * dx + (y - py) * dy) / span))
        return hypot(x - (px + dx * t), y - (py + dy * t)) <= r_start + (r_end - r_start) * t

    return inside


def polygon(points):
    def inside(x, y):
        hit = False
        j = len(points) - 1
        for i, (xi, yi) in enumerate(points):
            xj, yj = points[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                hit = not hit
            j = i
        return hit

    return inside


def union(*shapes):
    return lambda x, y: any(shape(x, y) for shape in shapes)


def offset(shape, dx, dy):
    return lambda x, y: shape(x - dx, y - dy)


def bezier(start, control, end, steps=8):
    """Points along a quadratic curve, for the scalloped trailing edge between two fingertips."""
    out = []
    for i in range(1, steps):
        t = i / steps
        u = 1 - t
        out.append(
            (
                u * u * start[0] + 2 * u * t * control[0] + t * t * end[0],
                u * u * start[1] + 2 * u * t * control[1] + t * t * end[1],
            )
        )
    return out


def distance_to_segments(point, segments):
    best = float("inf")
    x, y = point
    for (px, py), (qx, qy) in segments:
        dx, dy = qx - px, qy - py
        span = dx * dx + dy * dy
        t = 0.0 if span == 0 else max(0.0, min(1.0, ((x - px) * dx + (y - py) * dy) / span))
        best = min(best, hypot(x - (px + dx * t), y - (py + dy * t)))
    return best


# --- the animal ----------------------------------------------------------------------------------


def step(point, length, angle):
    """[length] along [angle], where 0 points straight back and 90 straight up."""
    return (point[0] - length * cos(angle), point[1] - length * sin(angle))


def wing(shoulder, hip, angle_degrees, extension, scale=1.0):
    """
    One wing's skeleton and the membrane stretched over it.

    Bones first, membrane second, because that is the order the shape actually depends on: the
    fingers decide where the trailing edge can reach, and pulling the edge between them is what
    makes it scallop instead of reading as a triangle of cloth.
    """
    base = radians(angle_degrees)
    elbow = step(shoulder, 7.5 * scale, base)
    forearm = base - radians(24)
    wrist = step(elbow, 7.0 * scale, forearm)

    # Tip-most finger first. The fan is what the extension closes: a folded wing is the same bones
    # drawn short, not a different drawing.
    fingers = [
        step(wrist, 10.0 * extension * scale, forearm),
        step(wrist, 9.0 * extension * scale, forearm - radians(42)),
        step(wrist, 6.5 * extension * scale, forearm - radians(84)),
    ]

    bones = [(shoulder, elbow), (elbow, wrist)] + [(wrist, tip) for tip in fingers]

    # Leading edge out along the arm, then back along a trailing edge bowed toward the wrist.
    outline = [shoulder, elbow, wrist, fingers[0]]
    for start, end in zip(fingers, fingers[1:] + [hip]):
        middle = ((start[0] + end[0]) / 2, (start[1] + end[1]) / 2)
        control = (
            middle[0] + (wrist[0] - middle[0]) * 0.24,
            middle[1] + (wrist[1] - middle[1]) * 0.24,
        )
        outline += bezier(start, control, end)
        outline.append(end)

    return bones, outline


def near_wing_layers(angle_degrees, extension, dy):
    """The near wing: membrane with its panels catching light, and bones ridged over it."""
    shoulder = (SHOULDER[0], SHOULDER[1] + dy)
    hip = (HIP[0], HIP[1] + dy)
    bones, outline = wing(shoulder, hip, angle_degrees, extension)
    membrane = polygon(outline)
    bone_shapes = [capsule(p, q, 1.5, 0.9) for p, q in bones[:2]]
    bone_shapes += [capsule(p, q, 1.0, 0.4) for p, q in bones[2:]]

    # A panel is membrane far enough from every bone to read as stretched skin rather than as
    # ridge. Quantized, not shaded: two flat tones is what the rest of the sheet uses.
    def lit(x, y):
        return membrane(x, y) and distance_to_segments((x, y), bones) > 3.0

    return [(membrane, "M"), (lit, "N"), (union(*bone_shapes), "B")]


def far_wing_layers(angle_degrees, extension, dy):
    """
    The far wing, flat and dark behind everything else.

    Drawn a few degrees behind the near one and a little short: both wings beat together, so the
    only thing separating them is that this one is further away.
    """
    shoulder = (SHOULDER[0] + 2.5, SHOULDER[1] - 1.5 + dy)
    hip = (HIP[0] + 3.5, HIP[1] - 1.5 + dy)
    _, outline = wing(shoulder, hip, angle_degrees - 10.0, extension * 0.92, scale=0.84)
    return [(polygon(outline), "F")]


def body_regions(dy):
    """
    The parts of the bat that never move, each tagged with the region it shades against.

    Regions exist because a single top-to-bottom gradient over the whole silhouette would read the
    ear tip as the animal's back and put the belly highlight halfway up its head. Each region is
    banded against its own extent instead.
    """
    # A big head on a slim body, which is the proportion a bat actually has and the one that keeps
    # the two reading as separate parts rather than as one tube.
    torso = ellipse(26.5, 28.6 + dy, 8.0, 4.4)
    head = ellipse(38.0, 25.6 + dy, 6.4, 6.0)
    snout = polygon(
        [(41.2, 26.0 + dy), (44.8, 28.0 + dy), (44.4, 31.0 + dy), (40.4, 31.4 + dy)]
    )
    tail = capsule((19.0, 29.4 + dy), (6.4, 30.6 + dy), 2.6, 0.9)
    leg = union(
        capsule((24.0, 31.4 + dy), (25.6, 34.2 + dy), 1.8, 1.2),
        capsule((25.6, 34.2 + dy), (28.2, 34.5 + dy), 1.2, 0.8),
    )

    # Tall and leaf-shaped rather than the horns this started as. Ears are what separate a bat from
    # every other thing that could be drawn cyan and pointed at this size, so they are worth the
    # pixels - but only just: any larger and they read as a second pair of wings.
    near_ear = polygon([(32.6, 22.8 + dy), (30.4, 12.4 + dy), (37.6, 20.2 + dy)])
    far_ear = polygon([(36.8, 21.2 + dy), (35.8, 14.6 + dy), (40.8, 20.4 + dy)])

    fin = polygon(
        [
            (10.4, 29.2 + dy),
            (5.0, 23.6 + dy),
            (2.6, 30.6 + dy),
            (5.2, 37.4 + dy),
            (10.4, 32.2 + dy),
        ]
    )

    return [
        ("far_ear", far_ear),
        ("near_ear", near_ear),
        ("fin", fin),
        ("head", union(head, snout)),
        ("body", union(torso, tail)),
        ("leg", leg),
    ]


# --- rasterizing ---------------------------------------------------------------------------------


def rasterize(shape):
    """A shape sampled onto the frame grid, hard-edged."""
    filled = set()
    weight = 1.0 / (SUBSAMPLES * SUBSAMPLES)
    for y in range(FRAME_HEIGHT):
        for x in range(FRAME_WIDTH):
            coverage = 0.0
            for sy in range(SUBSAMPLES):
                for sx in range(SUBSAMPLES):
                    px = x + (sx + 0.5) / SUBSAMPLES
                    py = y + (sy + 0.5) / SUBSAMPLES
                    if shape(px, py):
                        coverage += weight
            if coverage >= COVERAGE_THRESHOLD:
                filled.add((x, y))
    return filled


def neighbors(x, y):
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if dx or dy:
                yield x + dx, y + dy


def outline_against(grid, subject, others, color="o"):
    """Darkens every [subject] pixel that touches one of [others], so the two read apart."""
    edge = {p for p in subject if any(n in others for n in neighbors(*p))}
    for x, y in edge:
        grid[y][x] = color
    return edge


def shade_body(grid, region_pixels, materials, extent=None):
    """
    Bands a region from its own top edge to its own bottom edge, column by column.

    Per column rather than over the whole region, so the gradient follows the shape: the belly
    stays on the belly where the body is deep, and does not slide up onto the muzzle where it is
    shallow.

    @param extent the shape the bands are measured against, where that is wider than the pixels
        actually painted. The torso needs it: the head overlaps and claims its pixels first, and a
        torso banded against only what is left would put its dark back in the sliver under the jaw.
    """
    columns = {}
    for x, y in extent if extent is not None else region_pixels:
        low, high = columns.get(x, (y, y))
        columns[x] = (min(low, y), max(high, y))

    for x, y in region_pixels:
        top, bottom = columns[x]
        depth = 0.0 if bottom == top else (y - top) / (bottom - top)
        for threshold, material in materials:
            if depth < threshold:
                grid[y][x] = material
                break
        else:
            grid[y][x] = materials[-1][1]


BODY_BANDS = ((0.26, "d"), (0.56, "b"), (0.82, "c"), (1.01, "y"))
EAR_BANDS = ((0.55, "d"), (1.01, "b"))
LEG_BANDS = ((0.50, "d"), (1.01, "b"))

# One step further into shadow, for the crease where the head meets the shoulders. A step down the
# same ramp rather than a black line: a neck on a 45px sprite is a hint, not a seam.
DARKER = {"y": "c", "c": "b", "b": "d", "d": "d", "w": "c"}


def render_frame(index):
    angle, extension, bob = FLAP[index]
    grid = [["." for _ in range(FRAME_WIDTH)] for _ in range(FRAME_HEIGHT)]

    far = set()
    for shape, material in far_wing_layers(angle, extension, bob):
        pixels = rasterize(shape)
        far |= pixels
        for x, y in pixels:
            grid[y][x] = material

    near = set()
    for shape, material in near_wing_layers(angle, extension, bob):
        pixels = rasterize(shape)
        near |= pixels
        for x, y in pixels:
            grid[y][x] = material

    body = set()
    owned = {}
    for name, shape in body_regions(bob):
        extent = rasterize(shape)
        pixels = extent - body
        body |= extent
        owned[name] = pixels
        if name == "fin":
            for x, y in pixels:
                grid[y][x] = "f"
        elif name.endswith("ear"):
            shade_body(grid, pixels, EAR_BANDS, extent)
        elif name == "leg":
            shade_body(grid, pixels, LEG_BANDS, extent)
        else:
            shade_body(grid, pixels, BODY_BANDS, extent)

    # The crease where the head sits on the shoulders. Without it the head and torso are one
    # continuous gradient and the animal reads as a fish - which is exactly what it did read as
    # until this line existed.
    for x, y in owned["body"]:
        if any(n in owned["head"] for n in neighbors(x, y)):
            grid[y][x] = DARKER[grid[y][x]]

    # The inner ear, which is the detail that says "bat" rather than "horn".
    for x, y in rasterize(polygon([(33.6, 21.0 + bob), (32.2, 15.4 + bob), (36.4, 20.0 + bob)])):
        if (x, y) in owned["near_ear"]:
            grid[y][x] = "d"

    # A mouth along the muzzle, with a fang under it. Two materials and five pixels, and it is
    # most of what makes the head read as a head at this size.
    for x in range(42, 45):
        if (x, 29 + bob) in body:
            grid[29 + bob][x] = "o"
    if (43, 30 + bob) in body:
        grid[30 + bob][43] = "w"

    # The warm dot the old artwork carried on the tail fin, kept because it is the one spot of
    # color on the sprite that is not cyan and it is what the eye lands on at the back end.
    for x, y in ((6, 30 + bob), (7, 30 + bob), (6, 31 + bob)):
        if (x, y) in owned["fin"]:
            grid[y][x] = "g"

    # A rim light along the crown, which is what stops the head reading as a flat disc.
    for x, y in ((35, 21 + bob), (36, 20 + bob), (37, 20 + bob), (38, 20 + bob), (39, 20 + bob), (40, 21 + bob)):
        if (x, y) in owned["head"]:
            grid[y][x] = "w"

    # Depth, innermost first: the body silhouetted against the near wing, the near wing against the
    # far one. Each pass darkens the *further* layer, so nothing eats into the shape in front.
    near -= body
    far -= body | near
    outline_against(grid, near, body)
    outline_against(grid, far, near | body)

    # Eyes last, over the shading, so they stay the crispest thing on the sprite.
    for x, y in rasterize(ellipse(39.9, 24.0 + bob, 2.7, 2.3)):
        grid[y][x] = "e"
    for x, y in rasterize(ellipse(40.8, 24.2 + bob, 1.3, 1.5)):
        grid[y][x] = "p"

    # The outer outline goes on afterwards and outwards, into pixels nothing else claimed, so the
    # silhouette keeps the size every shape above was drawn at.
    filled = {(x, y) for y in range(FRAME_HEIGHT) for x in range(FRAME_WIDTH) if grid[y][x] != "."}
    for y in range(FRAME_HEIGHT):
        for x in range(FRAME_WIDTH):
            if grid[y][x] == "." and any(n in filled for n in neighbors(x, y)):
                grid[y][x] = "o"

    return grid


def main() -> int:
    output = pathlib.Path(__file__).resolve().parent.parent / "assets" / "cyanBat.png"
    sheet = Image.new("RGBA", (FRAME_WIDTH * FRAME_COUNT, FRAME_HEIGHT), (0, 0, 0, 0))

    for index in range(FRAME_COUNT):
        grid = render_frame(index)
        for y, row in enumerate(grid):
            for x, key in enumerate(row):
                if key != ".":
                    sheet.putpixel((index * FRAME_WIDTH + x, y), PALETTE[key])

    sheet.save(output)
    print(f"{output} ({sheet.width}x{sheet.height}, {FRAME_COUNT} frames)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
