# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the player's sprite sheets: six frames of one wing-beat, and five of dying.

    uv run tools/generate_cyanbat_sprite.py

Kept as a script for the same reason `generate_shot_sprite.py` is - the art stays editable. A 45x40
bat drawn eleven times over is too much to spell out pixel by pixel, so this describes the *animal*
instead: a body that never changes, and two wings built from bones whose pose is the only thing that
moves between frames. Redrawing a wing by hand six times is how frames end up not matching.

**Why it looks the way it does.** The bat this replaced read as a fish, and for three reasons that
are each worth not repeating:

* **A long body with a fin on the end.** It was drawn low and long, nose to tail fin across the
  whole frame. A bat is the opposite shape - a big head and a small, round, furry body - so this one
  is compact, and the fin is gone. What trails behind is feet and the tail membrane between them.
* **A wing that folded into a dorsal fin.** Seen exactly side on, a beating wing spends half the
  stroke edge-on, pointing at the camera, and flattens into a blade along the back. So the camera
  here sits a little above the animal: the far wing rises above it and the near wing hangs below,
  and even mid-beat there are two wings spread apart rather than one fin.
* **The screen stretched it.** The 480x320 framebuffer used to be scaled to fill the display,
  which on a 20:9 phone drew everything about half as wide again as it is tall, so anything long
  got longer. The game keeps its shape now - see `FrameFit` - but that is why every part that says
  "bat" here is upright: the ears, and the wings raised and lowered.

What it is recognised by, in the order the eye finds them: the ears, tall and pointed; the wings,
with the fingers that hold them open and the scallops pulled between the fingertips; and a face -
snout, nose, a fang - big enough to have an expression.

Rendering is deliberately hard-edged. Shapes are sampled at 3x3 per pixel and thresholded, so the
result is flat pixel art with as many colors as the palette below and not one more.

The sheet is 6 frames of 45x40, laid out left to right, which is what `EntityFactory.createBat`
addresses through `SpriteComponent.srcX` - and that row three times over, top to bottom: unhurt,
wounded and battered, which the game picks between by the bat's health (`WoundComponent`). The
death sheet is 5 frames of the same size, battered, because that is how the bat goes down.

**Wounds.** They are the bat's own parts, torn: an ear nicked and then torn short, the near wing
notched and then holed, claw marks down the pale of the cheek where no wing ever covers them, and at
the last the eye screwed half shut and a leg hanging limp. Cool colors only, like everything else on
the bat - a gash is the dark of its nose, not red, because red on this screen means something
hostile.
"""

import pathlib
import sys
from math import cos, radians, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME_WIDTH = 45
FRAME_HEIGHT = 40

# Cyan and deep blue, because that is what the bat's shots, its wake and the menu already are, and
# because every hostile in the game is warm: the player is the one cool thing on screen.
PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (7, 20, 43, 255),      # outline
    # fur, back to belly
    "d": (22, 64, 126, 255),
    "b": (37, 112, 186, 255),
    "c": (70, 176, 226, 255),
    "y": (168, 232, 248, 255),
    "w": (236, 252, 255, 255),  # highlight
    # the near wing: bones, membrane, and the panels of membrane stretched between the bones
    "B": (14, 44, 88, 255),
    "M": (32, 94, 164, 255),
    "N": (58, 140, 204, 255),
    # the far wing, flatter and darker - depth is the whole of what it says
    "F": (19, 56, 106, 255),
    "G": (27, 74, 134, 255),
    # face
    "i": (120, 204, 240, 255),  # inside of the ears
    "e": (246, 252, 255, 255),  # eye
    "p": (9, 19, 37, 255),      # pupil
    "n": (11, 30, 62, 255),     # nose and mouth
}

# How far above the animal the camera sits, in degrees. It is what separates the two wings on
# screen: the far wing is lifted by it and the near one lowered. At zero this is a side view again,
# and mid-beat both wings flatten into the fin this sprite was drawn to get rid of.
CAMERA_ELEVATION = 30.0

# One beat, sampled six times. `flap` is the wings' angle above level, in degrees; `spread` is how
# open they are, 1 fully out and 0 folded against the arm. They are not in phase, and that is why
# six frames beat two: a bat drives down with the wings open and brings them back up folded.
#
# `bob` is the body riding its own downstroke, a pixel either side of center.
FLAP = (
    # flap  spread  bob
    (88.0, 0.90, 1),    # top of the stroke, opening out
    (58.0, 1.00, 0),    # driving down, fully spread
    (4.0, 1.00, -1),
    (-26.0, 0.95, -1),  # bottom of the stroke
    (-6.0, 0.50, 0),    # recovering, folded in
    (60.0, 0.60, 1),
)

# The bat dying, in the same three numbers plus how open its eye is.
#
# A *sequence*, not a cycle: played once and held on the last frame while `DeathSystem` spins and
# drops the entity. So the wings do not beat here, they give up - thrown up and wide on the blow
# that killed it, then folding down until the animal is a limp bundle with its eye shut.
#
# The bob runs the other way from the flap's: it rises on the hit and then sags, which is the one
# pixel that makes the first frame read as recoil rather than as another wingbeat.
DEATH = (
    # flap  spread  bob  eye
    (96.0, 1.00, -1, 1.00),   # the hit: wings thrown up and open, eye wide
    (66.0, 0.80, -1, 0.70),
    (24.0, 0.55, 0, 0.35),    # folding, losing the light
    (-24.0, 0.30, 1, 0.10),
    (-62.0, 0.15, 1, 0.00),   # limp: wings hanging, eye shut
)

# The wing in its own plane, before it is posed: `a` runs along the body, forward positive, and `r`
# out along the span from the shoulder. Open is a wing at full stretch; folded is the same bones
# with the wrist drawn in and the fingers laid back along the arm, which is how a bat brings its
# wings up without pushing air.
#
# Three fingers, which is what a bat has in the membrane - the thumb stays out of it, as the claw
# on the leading edge. The fingertips run from the wingtip round to the back, and the heel is where
# the trailing edge meets the body, at the leg.
WING_OPEN = dict(
    elbow=(-3.0, 6.0),
    wrist=(-1.0, 13.0),
    thumb=(1.4, 14.2),
    tips=[(-9.5, 21.0), (-17.0, 15.5), (-19.0, 7.5)],
    heel=(-12.0, 0.0),
)
WING_FOLDED = dict(
    elbow=(-3.5, 5.0),
    wrist=(-3.0, 10.5),
    thumb=(-0.8, 11.6),
    tips=[(-13.0, 13.0), (-15.5, 10.0), (-16.0, 6.0)],
    heel=(-12.0, 0.0),
)

# Where the wings join, in frame coordinates before the bob. The far shoulder sits higher, because
# from above the far side of the body is further up the screen.
NEAR_SHOULDER = (23.0, 20.5)
FAR_SHOULDER = (25.0, 18.5)
# The far wing is further away, and drawn a little smaller for it.
FAR_WING_SCALE = 0.86

# The pixel the eye is centered on, before the bob. The rest of the face is placed around it.
EYE = (32.4, 17.0)

# How open the eye is at each of `pa.WOUND_LEVELS` in flight: wide until the bat is battered, and
# then screwed half shut. Dying brings its own.
WOUND_EYE = (1.0, 1.0, 0.62)

# How far the whole animal sits below where it was drawn, in pixels. At the top of its beat the far
# wing reaches all the way up the frame, and drawn a pixel higher its tip lands on the top row with
# no room left above it for the outline - a wing cut square by the edge of its own frame.
DROP = 1


# --- the wings -----------------------------------------------------------------------------------


def lerp(p, q, t):
    return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)


def wing(shoulder, flap_degrees, spread, far, wounds=0):
    """
    One wing, posed and projected: its bones, the membrane stretched over them, and whatever has
    been torn out of it.

    The wing is a flat sheet, so turning it about the body and looking at it from above comes down
    to one number - how much of its span shows on screen, and which way. That is `lift`: the sine of
    the flap angle, offset by the camera for each side, so the far wing shows more of itself on the
    way up and the near wing on the way down.

    Tears are placed off the posed bones before any of it is projected, so a hole stays between the
    same two fingers however far the wing is open or turned.
    """
    pose = {
        key: lerp(WING_FOLDED[key], WING_OPEN[key], spread)
        for key in ("elbow", "wrist", "thumb", "heel")
    }
    tips = [lerp(f, o, spread) for f, o in zip(WING_FOLDED["tips"], WING_OPEN["tips"])]

    tilt = CAMERA_ELEVATION if far else -CAMERA_ELEVATION
    lift = sin(radians(flap_degrees + tilt))
    scale = FAR_WING_SCALE if far else 1.0

    def place(point):
        a, r = point
        return (shoulder[0] + a * scale, shoulder[1] - r * lift * scale)

    # The near wing is the one the player watches, so it takes the first tear; the far one is only
    # torn once the bat is battered.
    wrist_plane = pose["wrist"]
    holes = []
    if far:
        if wounds >= 2:
            holes.append(pa.notch(tips[0], tips[1], wrist_plane, 0.62, width=0.55))
    else:
        if wounds >= 1:
            holes.append(pa.notch(tips[1], tips[2], wrist_plane, 0.66, width=0.62))
        if wounds >= 2:
            panel = lerp(lerp(tips[0], tips[1], 0.5), wrist_plane, 0.42)
            holes.append(pa.ring(panel, 3.0))
            holes.append(pa.notch(tips[2], pose["heel"], wrist_plane, 0.45, width=0.55))
    tears = [pa.polygon([place(point) for point in hole]) for hole in holes]

    elbow, wrist, thumb, heel = (place(pose[key]) for key in ("elbow", "wrist", "thumb", "heel"))
    tips = [place(tip) for tip in tips]

    bones = [(shoulder, elbow), (elbow, wrist)] + [(wrist, tip) for tip in tips]

    # Leading edge straight out to the wrist - the membrane in front of the elbow fills it - then
    # the trailing edge from fingertip to fingertip, each span bowed in toward the wrist so it
    # scallops instead of reading as a triangle of cloth.
    outline = [shoulder, wrist, tips[0]]
    for start, end in zip(tips, tips[1:] + [heel]):
        middle = lerp(start, end, 0.5)
        outline += pa.bezier(start, lerp(middle, wrist, 0.3), end)
        outline.append(end)

    return bones, outline, (wrist, thumb), tears


def torn(membrane, tears):
    return pa.without(membrane, pa.union(*tears)) if tears else membrane


def near_wing_layers(flap, spread, dy, wounds):
    """The near wing: membrane, the panels of it catching light, the bones ridged over it."""
    shoulder = (NEAR_SHOULDER[0], NEAR_SHOULDER[1] + dy)
    bones, outline, thumb, tears = wing(shoulder, flap, spread, far=False, wounds=wounds)
    membrane = torn(pa.polygon(outline), tears)

    # A panel is membrane far enough from every bone to read as stretched skin rather than ridge.
    def lit(x, y):
        return membrane(x, y) and pa.distance_to_segments((x, y), bones) > 2.4

    arm = pa.union(*(pa.capsule(p, q, 1.3, 0.9) for p, q in bones[:2]))
    fingers = pa.union(*(pa.capsule(p, q, 0.8, 0.35) for p, q in bones[2:]))
    claw = pa.capsule(thumb[0], thumb[1], 1.0, 0.5)
    return [(membrane, "M"), (lit, "N"), (pa.union(arm, fingers), "B"), (claw, "B")]


def far_wing_layers(flap, spread, dy, wounds):
    """The far wing, flat and dark behind the body."""
    shoulder = (FAR_SHOULDER[0], FAR_SHOULDER[1] + dy)
    bones, outline, _, tears = wing(shoulder, flap, spread, far=True, wounds=wounds)
    membrane = torn(pa.polygon(outline), tears)
    arm = pa.union(*(pa.capsule(p, q, 1.0, 0.7) for p, q in bones))
    return [(membrane, "F"), (arm, "G")]


# --- the body ------------------------------------------------------------------------------------


def body_parts(dy, wounds=0):
    """
    The parts of the bat that never move, back to front.

    Each is shaded against its own extent, because one gradient over the whole silhouette would
    read the ear tips as the animal's back and put the pale belly halfway up its face.

    [wounds] show on the near ear first: a nick out of its back edge, and then the tip torn off it.
    It is the first thing the eye finds on the bat, so a ragged one is the first thing that says the
    bat has been in a fight.
    """
    torso = pa.union(
        pa.ellipse(20.5, 23.8 + dy, 7.2, 5.6),
        # The chest, pushed forward and up into the head, so the head sits on shoulders rather than
        # on a stalk.
        pa.ellipse(25.0, 22.5 + dy, 5.0, 4.8),
    )
    # Legs trailing back, and the tail membrane pulled between them - the thing a bat has where
    # this sprite used to have a fin.
    legs = pa.union(
        pa.capsule((16.5, 26.5 + dy), (10.8, 28.6 + dy), 1.7, 1.0),
        # Battered, the near leg is past tucking up and hangs: a limp says it in the silhouette.
        pa.capsule((17.5, 27.5 + dy), (15.2, 33.6 + dy), 1.5, 0.9)
        if wounds >= 2 else pa.capsule((17.5, 27.5 + dy), (12.2, 30.6 + dy), 1.5, 0.9),
    )
    tail_membrane = pa.polygon(
        [(16.0, 24.0 + dy), (9.4, 27.6 + dy), (10.6, 30.0 + dy), (16.5, 29.0 + dy)]
    )

    head = pa.ellipse(31.0, 18.2 + dy, 6.1, 5.7)
    # A short, blunt snout: a bat's face is mostly eye and ear, and a long muzzle is what made the
    # old one read as a fish's head.
    snout = pa.ellipse(36.6, 20.4 + dy, 3.2, 2.5)

    # Tall and leaf-shaped, leaning back into the wind. The ears are what tell a bat from anything
    # else that could be drawn blue with wings at this size, so they are the biggest single part.
    near_ear = pa.polygon([(26.2, 15.6 + dy), (24.0, 3.2 + dy), (31.6, 13.0 + dy)])
    far_ear = pa.polygon([(30.6, 13.6 + dy), (33.2, 4.4 + dy), (36.0, 14.4 + dy)])
    if wounds >= 1:
        near_ear = pa.without(near_ear, pa.polygon([(22.0, 7.8 + dy), (27.4, 10.2 + dy), (22.0, 12.6 + dy)]))
    if wounds >= 2:
        near_ear = pa.without(near_ear, pa.polygon([
            (18.0, -1.0), (34.0, -1.0), (34.0, 5.6 + dy), (27.6, 6.6 + dy), (26.6, 5.2 + dy),
            (25.4, 7.4 + dy), (18.0, 6.6 + dy),
        ]))

    return dict(
        tail=pa.union(tail_membrane, legs),
        torso=torso,
        far_ear=far_ear,
        near_ear=near_ear,
        head=pa.union(head, snout),
    )


FUR_BANDS = ((0.24, "d"), (0.52, "b"), (0.80, "c"), (1.01, "y"))
HEAD_BANDS = ((0.20, "b"), (0.62, "c"), (1.01, "y"))
EAR_BANDS = ((0.45, "d"), (1.01, "b"))
TAIL_BANDS = ((0.50, "d"), (1.01, "b"))


# Claw marks raked down the cheek behind the eye, in the order they are taken. The cheek, because
# it is pale and no wing ever sweeps across it: marks on the body spend half the beat under one.
# Before the bob.
CLAW_MARKS = (
    ((26.4, 17.8), (27.4, 20.0), (27.8, 22.4)),
    ((28.5, 19.2), (29.4, 21.2), (29.7, 23.0)),
    ((30.6, 20.6), (31.2, 22.8)),
)
# How many of them each of the wound levels has taken.
CLAWED = (0, 2, 3)


def render_frame(flap, spread, bob, eye=1.0, wounds=0):
    grid = pa.blank(FRAME_WIDTH, FRAME_HEIGHT)
    bob += DROP

    def raster(shape):
        return pa.rasterize(shape, FRAME_WIDTH, FRAME_HEIGHT)

    def paint(pixels, material):
        for x, y in pixels:
            grid[y][x] = material

    far = set()
    for shape, material in far_wing_layers(flap, spread, bob, wounds):
        pixels = raster(shape)
        far |= pixels
        paint(pixels, material)

    parts = body_parts(bob, wounds)
    tail = raster(parts["tail"])
    pa.shade_bands(grid, tail, TAIL_BANDS)
    torso = raster(parts["torso"])
    pa.shade_bands(grid, torso, FUR_BANDS)
    body = tail | torso

    # The near wing over the body, so the downstroke visibly sweeps across the belly...
    near = set()
    for shape, material in near_wing_layers(flap, spread, bob, wounds):
        pixels = raster(shape)
        near |= pixels
        paint(pixels, material)

    # ...and the head over the near wing, so a wing on the upstroke never covers the face.
    far_ear = raster(parts["far_ear"])
    pa.shade_bands(grid, far_ear, EAR_BANDS)
    near_ear = raster(parts["near_ear"])
    pa.shade_bands(grid, near_ear, EAR_BANDS)
    head = raster(parts["head"])
    pa.shade_bands(grid, head, HEAD_BANDS)
    ears = far_ear | near_ear
    front = head | ears

    # The insides of the ears, which are the detail that says "ear" rather than "horn".
    paint(raster(pa.polygon([(27.2, 14.2 + bob), (25.4, 6.4 + bob), (30.0, 12.8 + bob)])) & near_ear, "i")
    paint(raster(pa.polygon([(32.0, 13.2 + bob), (33.3, 7.6 + bob), (34.8, 13.6 + bob)])) & (far_ear - near_ear), "i")

    # A ruff of pale fur down the throat and chest, notched along its edge so it reads as fur.
    ruff = raster(pa.ellipse(28.2, 24.2 + bob, 3.6, 3.4)) & (torso - near)
    paint(ruff, "y")
    for x, y in ((25, 23 + bob), (26, 26 + bob), (28, 27 + bob)):
        if (x, y) in ruff:
            grid[y][x] = "c"

    # The claw marks, dark across the pale of the cheek. Before the eye, which is drawn over them.
    for mark in CLAW_MARKS[:CLAWED[wounds]]:
        points = [(x, y + bob) for x, y in mark]
        paint(raster(pa.crack(points, 0.5)) & head, "n")

    # Depth, innermost first: each pass darkens the *further* layer where it meets a nearer one,
    # so nothing eats into the shape in front.
    pa.outline_against(grid, far - body - near - front, body | near | front)
    pa.outline_against(grid, body - near - front, near | front)
    pa.outline_against(grid, near - front, front)
    pa.outline_against(grid, far_ear - near_ear - head, near_ear | head)
    pa.outline_against(grid, ears - head, head)

    # The face, over all the shading.
    ex, ey = EYE[0], EYE[1] + bob
    # A rim light along the crown, which is what stops the head reading as a flat disc.
    for x, y in ((28, 13), (29, 12), (30, 12), (31, 12), (32, 12), (33, 13)):
        if (x, y + bob) in head and grid[y + bob][x] != "o":
            grid[y + bob][x] = "w"
    # The nose, a pug's rather than a muzzle's, and a mouth with a fang hanging from it.
    for x, y in ((39, 19), (39, 20), (38, 19)):
        grid[y + bob][x] = "n"
    for x in range(35, 39):
        grid[22 + bob][x] = "n"
    grid[23 + bob][37] = "w"

    # [eye] closes it: the white shrinks to a slit and then to nothing, and below a slit the lid
    # is drawn as a dark line instead. The eye is the one feature the player actually reads at this
    # size, so closing it is most of what says the animal has stopped flying.
    if eye > 0.05:
        paint(raster(pa.ellipse(ex, ey, 2.6, 2.6 * eye)), "e")
        if eye > 0.3:
            paint(raster(pa.ellipse(ex + 0.9, ey + 0.2, 1.3, 1.6 * eye)), "p")
            # A catchlight, which is the difference between an eye and a button.
            grid[round(ey - 1)][round(ex + 1)] = "w"
        # A brow, set low over the eye. It is what makes the face determined rather than startled.
        for x in range(round(ex - 2), round(ex + 3)):
            grid[round(ey - 2.8 * eye) - 1][x] = "o"
    else:
        paint(raster(pa.ellipse(ex, ey + 0.5, 2.6, 0.6)), "o")

    # Outwards, into pixels nothing else claimed, so the silhouette keeps the size every shape
    # above was drawn at.
    pa.outer_outline(grid, FRAME_WIDTH, FRAME_HEIGHT)
    return grid


def clipped(grid):
    """Whether anything but outline touches the frame's edge - a wing cut square by the border."""
    last_x, last_y = FRAME_WIDTH - 1, FRAME_HEIGHT - 1
    return any(
        grid[y][x] not in (pa.TRANSPARENT, pa.OUTLINE)
        for y in range(FRAME_HEIGHT)
        for x in range(FRAME_WIDTH)
        if x in (0, last_x) or y in (0, last_y)
    )


def main() -> int:
    assets = pathlib.Path(__file__).resolve().parent.parent / "assets"

    # Two sheets from one animal. That is the whole reason the death frames live in this file
    # rather than a generator of their own: the bat dying has to be recognisably the same bat, and
    # the surest way to guarantee that is for both sheets to come out of the same body.
    #
    # The flight is drawn at every wound level, a row apiece. The fall is drawn once, battered:
    # nearly every bat that dies was battered on its last frame of flight, and one that healed as
    # it fell would read as a glitch.
    sheets = (
        ("cyanBat.png", [
            [render_frame(flap, spread, bob, WOUND_EYE[level], level) for flap, spread, bob in FLAP]
            for level in pa.WOUND_LEVELS
        ]),
        ("cyanBatDeath.png", [[render_frame(*pose, wounds=pa.WOUND_LEVELS[-1]) for pose in DEATH]]),
    )
    for name, rows in sheets:
        for level, grids in enumerate(rows):
            for index, grid in enumerate(grids):
                if clipped(grid):
                    print(f"{name} row {level} frame {index} runs off the edge of its frame", file=sys.stderr)
                    return 1
        sheet = pa.save_rows(assets / name, rows, PALETTE, FRAME_WIDTH, FRAME_HEIGHT)
        print(f"{assets / name} ({sheet.width}x{sheet.height}, {len(rows)} rows of {len(rows[0])} frames)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
