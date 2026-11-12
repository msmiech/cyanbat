# Rendering and light

Part of the notes for coding agents; [AGENTS.md](../../AGENTS.md) has the rules that hold everywhere
and the list of these files.

## Rendering

**Rendering.** One `Graphics` for every platform: `ComposeGraphics`, in the engine's common code,
drawn through Compose's Canvas - HWUI, and so the GPU, on Android; Skia on the desktop, in the
browser and on iOS. There is no framebuffer bitmap.

- `present` is recorded, call by call, and the host draws the recording in Compose's draw phase with
  `drawGameFrame`: scaled from frame pixels to the `FrameFit` rectangle in one transform, and
  clipped to it. A frame is drawn more than once: `AmbientBars` draws its edges again into a small
  picture of its own to read them, and `drawInto` draws it at frame size, on the CPU, for the
  recorder and the tests (`DesktopGame.capture`).
- Every screen clears first, and the clear is what starts the recording over.
- Everything but text is pixel art on a grid, at any screen size: drawn aliased and
  nearest-neighbor, in frame pixels under one scale. Lines, ovals and oval outlines are worked out
  in common code (`Raster`) and filled as rectangles or as polygons with pixel corners - never as
  Skia's own shapes, whose edges are not the grid's. A turned sprite is turned on the grid first,
  into a picture of its own, and then drawn upright: turned at the screen's resolution its pixels
  would come out as squares tilted against the grid and finer than it. `ComposeGraphicsTest` holds
  this to the pixel: at three times the frame's size, every frame pixel is a solid block.
- The grid is the frame's own pixels by default, and ready to be made finer: `gridScale` on
  `ComposeGraphics` is grid pixels to a frame pixel, taken up at the next frame. On a finer grid,
  sprites and rectangles keep their frame pixels, ovals and outlines are worked out on the grid with
  their weight kept, and turned sprites turn on it. Lines stay on the frame's grid, since
  `Raster` only draws them one grid pixel wide. A setting for it would be wired up the way the
  `DisplayMode` is, from the host.
- The deliberate `- 1` in `drawPixmap` paints a column and a row short, and is why background tiles
  overlap by one column. A "fix" would shift every sprite by a pixel. Art drawn out to a frame's
  last column or row is cut off there, so a sprite leaves both clear, as the shots do.
- Text is the exception, by choice: laid out in frame pixels, so the game places it as before, but
  drawn at the screen's resolution and antialiased. An outline is a stroke around the glyphs
  (`drawOutlinedString`), not the string stamped around itself. Each platform draws its own
  sans-serif face (Roboto on Android, Arial on Windows, usually DejaVu Sans on Linux), and the same
  string comes out at different widths. Right-align, center or wrap text by
  `Graphics.measureString`, never by a count of characters.
- A translucent blend can round a step apart on the GPU, on the CPU and between scales; the tests
  allow a step a channel and no more.
- A warp (`Graphics.drawWarped`, the poison's ripple) shifts rows of whatever it draws sideways by
  whole frame pixels, wrapping at the edges, so it stays on the grid. It is no offscreen picture:
  the recording groups rows that shift alike into runs, and draws the warp's commands once a run,
  clipped to its rows and moved, skipping those outside them, plus the strip a shift leaves bare,
  from the far edge. A gentle ripple is a few dozen runs; its cost grows with how many.
- A see-through sprite is dithered rather than blended (`DitherComponent`, `drawPixmapDithered`):
  only the share of its pixels that `Dither`, a four by four ordered pattern in the sprite's own
  pixels, keeps is drawn, so the art stays whole pixels on the grid. Each frame is dithered once per
  level, on the CPU, and kept.
- The desktop decodes assets with Skia and marks them immutable (`DesktopGame.loadImage`). Compose's
  desktop canvas wraps a bitmap in a new Skia image every time it draws it, and copies one that
  could still change: the desert's ground was a copy of its whole sheet for every strip.
- A picture can be made from pixels as the game runs (`Graphics.newPixmap(width, height, pixels)`),
  as the endless stage's land is. Each host hands `ComposeGraphics` its platform's own way of
  wrapping pixels in an immutable bitmap (`makeImage`); without one, `paintImage` paints them in run
  by run on the CPU, and a test double keeps them as a `PixelPixmap`.
- A new `Graphics` primitive needs a default implementation or a `ComposeGraphics` override, and the
  test fakes need updating.

## The cave's dark

**The cave's dark.** The cave is flown in the dark (`Stage.lighting`, a `StageLighting`); the jungle
and the desert are flown in daylight, and none of this runs there. The endless stage is flown in it
by night only: its ambient and its lights' strength and reach are set as the day goes round, and at
`LightingSystem.DAYLIGHT` nothing is computed or drawn.

- `LightingSystem` sits between two `RenderSystem` passes split at `LIGHTS_FROM` (z 15). Under it
  are the scenery, the halos, the obstacles and everything hostile, as lit as whatever reaches them;
  over it the shots, the bat and the blasts, which are lights and are drawn as bright as they are.
  In daylight the two passes draw what one did.
- What gives off light (`LightComponent`: the bat, each shot in its bolt's color -
  `SHOT_BODY_COLORS`, which `SpriteSheetTest` holds to the sheet - blasts, a shot's hit, which
  flares wider than the shot's own light, elites, the cave's boss) and what throws shadows
  (`OccluderComponent`: obstacles and creatures) is set in `EntityFactory`, and only when its `lit`
  is set. The numbers are under "The dark" in `CyanBatConstants`.
- The light is worked out on the tick into a `Lighting` and drawn on the frame by
  `Graphics.drawLighting`. `ComposeGraphics` turns it into a picture on the CPU, a pixel of it to
  each 2x2 cell of frame pixels (`LIGHT_CELL`): each light a cached sprite of banded rings in its
  color (`Lighting.rings`, laid down with `Raster.oval`), laid over the dark with its shadows erased
  from a scratch copy first. The frame takes that picture in one GPU draw, multiplied (`Modulate`),
  with a little of it added back as `glow`. A light no tick has changed (`Lighting.version`) is not
  drawn again.
- It is built for old phones, and measured on the emulator before and after: the light costs the UI
  thread about 0.2 ms a frame. Keep it that way. On the CPU only plain copies (`Src`) and `SrcOver`
  are fast in Skia - adding light (`Plus`) or tinting it as it is drawn costs several times as much
  a pixel - which is why lights are laid over rather than added, and why each color of light is its
  own cached sprite. The first light, the bat's, lands on nothing but the dark, so it is copied down
  whole with the dark already in it and its shadows painted on in the dark's color (`layFirst`),
  which comes out the same pixel for pixel. Half the frame's resolution is a quarter of the pixels
  to fill, light and upload every tick; the cost is a shadow's edge stepping two pixels at a time.
- A shadow is cast from the convex hull of the sprite's current frame (`Silhouettes`), out from its
  far side, so whatever throws one stays lit, and a creature's shadow beats its wings. Every light
  throws them, the shots' included; a light inside an outline (an elite's glow) throws none from it.
- Glints (`Gloss`): each frame's outline is taken as a rounded bevel, and every light reaching
  something with a `shine` adds a thin rim on the side it comes from, in its color - unless that
  thing stands in another's shadow. Sixteen directions, three steps, masks cached per frame and
  direction (`FrameCache`) and added after the light. They are meant to stay subtle and to help the
  player read which way the light falls and where things are; past the frame's edge the art counts
  as carrying on, so a rock's cut base never glints.
- Silhouettes and glints read the art through `Pixmap.readPixels`. A test double reads nothing and
  counts as its whole frame.

## The Caco Imp's fight

**The Caco Imp's fight.** The cave's boss is fought in five phases at fifths of its health
(`CACO_IMP_PHASE_*_AT`, as the Naga's), with `CACO_IMP_VITALITY` its extra health. By the time a
player reaches it their shots, orbs and wake light the cave from end to end, so nothing in the fight
rests on the dark: it is fought the same by day, as an endless round's boss.

- `CacoImpBrain` runs it: it smoulders on station; then charges (`CacoImpBrain.Move`: rest, wind up,
  rush, return); then spits embers; then by turns burns inside a fire shield and goes cold and
  charges; and ends ablaze, charging, spitting and calling in strikers.
- The charge: a wind-up of `CACO_IMP_WINDUP_SECONDS` - rearing back, shuddering, washed white-hot
  (`TintComponent`), its light flaring and `SoundEffect.IMP_WINDUP` playing - then a rush along the
  line through where the bat was, aimed once, out of whichever edge it crosses, and back in from the
  right. A run's every tell must survive a frame lit by the bat: the boss is drawn under the dark,
  so the bat's cyan light tints its wash, and its hit flash would bury it, which is why the brain
  swaps in a colorless `HitFlashComponent` through the wind-up and the rush. The sound and the
  shudder are what nothing on screen can hide. It fires only on station.
- Embers (`ShotPattern.EMBER`, `EmberComponent`, `EmberSystem`) are enemy shots fanned wide of the
  bat, so its own gun, firing ahead, does not put them out by the way; they slow to a hang, flash,
  and burst into a ring where they hang. One shot down or spent on the bat goes without its ring.
  The system holds no ember across ticks, and the run fires the ring (`burstEmber`).
- The fire shield is the factory's `ShieldComponent`, down until the brain raises it, in the shield's
  ordinary bubble; shot down or burnt out, the imp goes cold - light out, washed ashen, silent - and
  only charges, every shot landing.
- Its health bar is pinned under the timer (`BOSS_BAR_*`, shared with the Sand Wyrm), since it
  charges out of the frame. Its light is the factory's `LightComponent`, changed in place: only its
  intensity moves tick to tick, between two radii and colors, since a light's sprite is cached by
  radius and color, never by strength.

## The frame and its timing

**Timing.** The shared `GameLoop` clamps a frame's delta to 50 ms, so a resume cannot fast-forward
the run. `GameScreen` steps `world.update` in fixed 19 ms ticks (`TICK_INITIAL`), and everything
that paces gameplay - `EnemyGenerator`, `ObstacleGenerator`, the stage clock - is fed that tick.
Never pace gameplay with the wall clock or a coroutine: a paused game has to be a paused stage. On
Android the loop marks each frame's `Screen.update` and `Screen.present` in the system trace
(`FrameTrace`), and the activity marks the frame's drawing (`Frame.draw`), so a Perfetto or Android
Studio trace shows what a slow frame spent its time on.

**The frame's shape is part of the game design.** It is 16:9, 640x360 - `FRAME_BUFFER_WIDTH` and
`FRAME_BUFFER_HEIGHT` in `:game`, which both hosts and the recorder hand the engine - and spawn
points, boss stations, the overlays' rows and the desert's sky are laid out against it. Speeds are
in frame pixels, so a wider frame shows more of a stage at once rather than a faster one.
`gameover.png`, from artwork hand-drawn for the 480x320 frame the game had before, is centered on
it.
`FrameFit` fits it to the screen according to the player's `DisplayMode` (stretch, black bars, or
the default ambient bars) and maps touches back through the same rectangle. Do not widen the
playfield for wide screens; it would change difficulty by device.
