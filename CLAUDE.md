# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

CyanBat is a side-scrolling shooter that runs on Android and on the desktop from one Kotlin
Multiplatform codebase. The README covers features, controls and the release secrets; this file
covers what you need to change the code.

## Commands

```bash
./gradlew build                                          # everything CI runs: assemble, lint, unit tests
./gradlew :engine:jvmTest :game:jvmTest :desktop:test :app:testDebugUnitTest   # just the unit tests
./gradlew :game:jvmTest --tests 'at.smiech.cyanbat.ScoreTrackerTest'   # one class
./gradlew :engine:jvmTest --tests '*CollisionSystem*'    # wildcards work too
./gradlew :app:lint                                      # lint exists only in :app
./gradlew :desktop:run                                   # play on the desktop, no emulator
./gradlew :app:installDebug                              # install on a running emulator
./gradlew :engine:iosSimulatorArm64Test :game:iosSimulatorArm64Test   # the shared tests on the iOS simulator; Mac only
./gradlew :desktop:recordGameplay                        # re-record the README's GIF, docs/gameplay.gif
uv run tools/generate_enemy_sprites.py                   # regenerate an asset; each script declares its own deps
```

- `commonTest` runs on the JVM (`jvmTest`) and, on a Mac, on the iOS simulator
  (`iosSimulatorArm64Test`, which needs Xcode; on a Mac `build` runs it too). No Android host tests
  or instrumentation tests are configured. `:app` has plain JVM unit tests of its own
  (`app/src/test`), for its DataStore code.
- **Off a Mac, `build` skips the iOS targets** (`kotlin.native.enableKlibsCrossCompilation=false`
  in `gradle.properties`), so it needs no Kotlin/Native toolchain. It still compiles `commonMain`
  as common code, so a JVM-only API there fails the build anywhere. What only Kotlin/Native
  catches - a comma in a backticked test name, for one, which it rejects - fails in CI's `ios`
  job, or locally with `-Pkotlin.native.enableKlibsCrossCompilation=true`, at the cost of a
  one-time download of about 800 MB. An IDE sync downloads that toolchain regardless.
- To run, drive and screenshot the app on an emulator, use the `run-cyanbat` skill
  (`.claude/skills/run-cyanbat/`). Its gotchas cover what trips up device testing: the game
  activity is not exported, `monkey` destroys it, and binary output needs `adb exec-out`.
- **In a git worktree** (such as `.claude/worktrees/*`) there is no `local.properties`, since it
  is gitignored, so Android tasks fail with "SDK location not found". Run them with
  `ANDROID_HOME` set to the SDK. JVM-only tasks - the tests, `:desktop:*` - do not need it.
- JDK 21 comes from the Gradle toolchain; nothing to install.

## Architecture

`:engine` <- `:game` <- `:app` (Android) and `:desktop` (Compose Desktop). The engine and the whole
game, menu UI included, are common code; the two platform modules only supply platform pieces.
The few platform differences inside `:game` are `expect`/`actual` (`ui/Platform.kt`).
`:engine` and `:game` also have iOS targets (`iosArm64`, `iosSimulatorArm64`), but there is no iOS
app yet: the targets are there so that the shared code stays portable to one.

**Two kinds of UI.** The menu - main screen, stage select, settings, credits - is shared Compose:
`CyanBatMenu`, fed by a `MenuHost`, navigated by a hand-rolled `MenuBackStack`. The game itself is
not laid out by Compose, but it is drawn through it: screens draw a fixed **640x360 frame**
through the engine's `Graphics`, and the host draws that into a Compose `Canvas` - see Rendering.
- Android: `MainActivity` shows the menu and starts `CyanBatGameActivity` (a subclass of the
  engine's `AndroidGameActivity`), passing the stage as the `at.smiech.cyanbat.STAGE_ID` extra.
- Desktop: one window swaps between `CyanBatMenu` and `GameSurface`/`DesktopGame`.
- Each host builds a `CyanBatEnvironment` (assets, haptics, highscore and stage unlock stores,
  audio settings, exit-to-menu) and hands it to `GameScreen`. Persistence is interfaces in `:game`,
  backed by DataStore in `:app` and `java.util.prefs` in `:desktop`.

**Timing.** The shared `GameLoop` clamps a frame's delta to 50 ms, so a resume cannot fast-forward
the run. `GameScreen` steps `world.update` in fixed 19 ms ticks (`TICK_INITIAL`), and everything
that paces gameplay - `EnemyGenerator`, `ObstacleGenerator`, the stage clock - is fed that tick.
Never pace gameplay with the wall clock or a coroutine: a paused game has to be a paused stage.
On Android the loop marks each frame's `Screen.update` and `Screen.present` in the system trace
(`FrameTrace`), and the activity marks the frame's drawing (`Frame.draw`), so a Perfetto or Android
Studio trace shows what a slow frame spent its time on.

**The frame's shape is part of the game design.** It is 16:9, 640x360 - `FRAME_BUFFER_WIDTH` and
`FRAME_BUFFER_HEIGHT` in `:game`, which both hosts and the recorder hand the engine - and spawn
points, boss stations, the overlays' rows and the desert's sky are laid out against it. Speeds are
in frame pixels, so a wider frame shows more of a stage at once rather than a faster one.
`gameover.png`, hand-drawn for the 480x320 frame the game had before, is centered on it.
`FrameFit` fits it to the screen according to the player's `DisplayMode` (stretch, black bars, or
the default ambient bars) and maps touches back through the same rectangle. Do not widen the
playfield for wide screens; it would change difficulty by device.

**ECS** (`at.smiech.engine.ecs`):
- `World` stores components struct-of-arrays with a 64-bit signature per entity, so there can be
  at most 64 component types.
- Entity ids are recycled when removals are finalized at the end of `World.update`. Never keep an
  id across frames to look something up later. Copy what you need at spawn instead: a shot's color
  is read off its shooter when the shot is created.
- There is no `removeComponent`. Clamp or disarm a component rather than taking it off.
- Systems resolve `ComponentMapper`s in `onAttach` and iterate with the allocation-free `forEach`.
- Engine systems know nothing about CyanBat; game-specific work goes through callbacks
  (`WeaponSystem`, `TrailSystem`, `DeathSystem`, `CollisionSystem`).
- **System order is load-bearing.** It is set, with a comment per placement, in `GameScreen`'s
  `init`; read it before adding a system.
- Sprite rotation is cosmetic. Collision boxes always stay axis-aligned. Hostile art faces left,
  so something that turns to face its heading carries `FacesVelocityComponent(180f)` - all but the
  lagoon's shark, which comes from behind and so is drawn facing right (`drawnFacingRight`).

**A run.** `GameScreen` is one run of one stage.
- Pause, the level-up offer and "stage complete" are state inside it rather than separate
  `Screen`s, because swapping screens disposes the run. `GameOverScreen` is its own screen.
- Moving to the next stage builds a fresh `GameScreen`. That is what resets score, experience and
  power-ups.
- The boss going down does not put "stage complete" up at once. Everything hostile goes with it,
  and the run plays on for `STAGE_COMPLETE_DELAY_SECONDS` (`playOutVictory`) under a banner that
  the boss has fallen: the bat flies on, unarmed and untouchable, the wreck bursts again at
  `BOSS_AFTERSHOCK_SECONDS`, the stage's music stops dead for the boss's blast, and a fanfare comes
  in. The overlay lands on the fanfare's drop. The aftershocks are timed to the blasts in
  `bossDeath.wav` and the delay to the fanfare's `LANDING_BEAT`; change each with its script.
- Overlays read taps through `TapDetector` plus an arming delay, so the finger that was steering
  when an overlay opened does not pick something when it lifts.
- In-game text (HUD, banners, overlays) is literal strings drawn at frame coordinates in
  `GameScreen` and `GameOverScreen`. The rows are hard-coded, but every centered or right-aligned
  line is placed by its measured width, so rewording one needs no new x. Menu text is in
  `composeResources/values/strings.xml`.

**Stage content is split four ways.**
- `Stage` (registered in `GameAssets.load`) is how a stage looks and sounds. Its `Backdrop` is
  either a `Strip` (the jungle, the cave: one tiled image) or a `Sky` (the desert and the lagoon: a
  sky the game draws, changing with the time of day on the stage clock, over parallax bands of
  ground). Its `Approach`, where it has one, is what its obstacles turn into on the way to its boss.
- `StageDesign` is its waves and boss; `StageDesign.forStage` maps a stage id to its design. Its
  `bossToughness` keeps a boss harder than the waves that lead to it: the Moth Queen is fought at
  the strength she had as stage 2's boss, at the end of an eased opening stage.
- `StageProgression` turns seconds elapsed into the current wave's stats, scaled up per stage, and
  the boss by its own `bossDifficulty`; what a boss calls in arrives as its `escortWave`, at the
  boss's difficulty.
- `EnemyGenerator` owns only the clock and the dice, and spawns through `EntityFactory`.

Adding a stage touches all four, plus new generators in `tools/` (its music among them, with a
`StageMusic` in `GameAssets`), a preview card, the stage select's strings, and the recorder's
`STAGE_COVERAGE`, which says how much of the stage the README's reel shows.

**The lagoon** (stage 4) is the hardest stage: its waves are the desert's design pushed further, with
sharks that come in from behind along the waterline (`Squad.FROM_BEHIND`, a `LEAP` cruising right)
and puffers that throw rings. A temple comes into sight on the way to the Naga: three of its bands
switch to temple strips, and its obstacles to temple stones.

**The sky's day.** A `Day` is a pure function of how far through its day the stage is -
`Day.position`, elapsed time over the boss's arrival - and says what the sky, sun, moon and stars
look like then: `Daylight` is the desert's, from noon into night, and `Daybreak` the lagoon's, from
night into noon. `SkySystem` draws a `Backdrop.Sky` by its day, first in the system order, and puts
every entity carrying a `CrossfadeComponent` (the obstacles) in the same light.
- Scenery lit by the day is drawn once per entry of its day's `keyframes` (the desert's noon, golden
  hour, sunset and night; the lagoon's night, dawn, sunrise and noon), stacked top to bottom on its
  sheet, and crossfaded between neighbors with `drawPixmapFaded`. A generator writes the shapes once
  as materials and each keyframe as a palette, so the rows cannot drift apart.
- The sun the player can see is on the right - it sets there in the desert and rises there in the
  lagoon - so the scenery is lit from the right; the night palettes turn that round, to the moon on
  the left.
- The lagoon's sun rises out of the sea banded by the haze (`Day.sunBands`), drawn a row at a time,
  and lays a path of glints on the water (`Day.sunGlitter`) over the band with `water` rows.
- A band can turn into other scenery on the way to the boss (`ParallaxLayer.ahead`, from its
  `aheadFrom`): each stretch of it not yet in view is drawn from the other strip, so the new scenery
  scrolls in from the right. The two strips of a pair share their first and last columns, which the
  generator keeps to the band's own water and haze, so either follows on from the other.

**The cave's dark.** The cave is flown in the dark (`Stage.lighting`, a `StageLighting`); the jungle
and the desert are flown in daylight, and none of this runs there.
- `LightingSystem` sits between two `RenderSystem` passes split at `LIGHTS_FROM` (z 15). Under it are
  the scenery, the halos, the obstacles and everything hostile, as lit as whatever reaches them; over
  it the shots, the bat and the blasts, which are lights and are drawn as bright as they are. In
  daylight the two passes draw what one did.
- What gives off light (`LightComponent`: the bat, each shot in its bolt's color - `SHOT_BODY_COLORS`,
  which `SpriteSheetTest` holds to the sheet - blasts, the flash where a shot is spent, elites, the
  cave's boss) and what throws shadows (`OccluderComponent`: obstacles and creatures) is set in
  `EntityFactory`, and only when its `lit` is set. The numbers are under "The dark" in
  `CyanBatConstants`.
- The light is worked out on the tick into a `Lighting` and drawn on the frame by
  `Graphics.drawLighting`. `ComposeGraphics` turns it into a picture on the CPU, a pixel of it to
  each 2x2 cell of frame pixels (`LIGHT_CELL`): each light a cached sprite of banded rings in its color
  (`Lighting.rings`, laid down with `Raster.oval`), laid over the dark with its shadows erased from a
  scratch copy first. The frame takes that picture in one GPU draw, multiplied (`Modulate`), with a
  little of it added back as `glow`. A light no tick has changed (`Lighting.version`) is not drawn
  again.
- It is built for old phones, and measured on the emulator before and after: the light costs the UI
  thread about 0.2 ms a frame. Keep it that way. On the CPU only plain copies (`Src`) and `SrcOver`
  are fast in Skia - adding light (`Plus`) or tinting it as it is drawn costs several times as much a
  pixel - which is why lights are laid over rather than added, and why each color of light is its own
  cached sprite. The first light, the bat's, lands on nothing but the dark, so it is copied down whole
  with the dark already in it and its shadows painted on in the dark's color (`layFirst`), which comes
  out the same pixel for pixel. Half the frame's resolution is a quarter of the pixels to fill, light
  and upload every tick; the cost is a shadow's edge stepping two pixels at a time.
- A shadow is cast from the convex hull of the sprite's current frame (`Silhouettes`), out from its
  far side, so whatever throws one stays lit, and a creature's shadow beats its wings. Every light
  throws them, the shots' included; a light inside an outline (an elite's glow) throws none from it.
- Glints (`Gloss`): each frame's outline is taken as a rounded bevel, and every light reaching
  something with a `shine` adds a thin rim on the side it comes from, in its color - unless that thing
  stands in another's shadow. Sixteen directions, three steps, masks cached per frame and direction
  (`FrameCache`) and added after the light. They are meant to stay subtle and to help the player read
  which way the light falls and where things are; past the frame's edge the art counts as carrying on,
  so a rock's cut base never glints.
- Silhouettes and glints read the art through `Pixmap.readPixels`. A test double reads nothing and
  counts as its whole frame.

**The Caco Imp's dark.** The cave's boss uses the dark as a weapon. `CacoImpBrain` runs its fight in
three phases off its health, as the other bosses' brains do: it smoulders, alight; at its first
wound it puts its light out and goes round a prowl (`CacoImpBrain.Prowl`) - douse, glide unlit to a
station of its choosing (`EnemyMovementType.GLIDE`), lurk, flare up, fire, burn - and at its second
it blazes for good and calls in strikers. It never fires in the dark, since its bolts are lights.
What finds it there is light: the bat's, its shots', the flash of a hit. Its health bar is pinned
under the timer (`BOSS_BAR_*`, shared with the Sand Wyrm), because one hung under it would show where
it had got to. Its light is the factory's `LightComponent`, changed in place, and only its intensity
moves tick to tick: a light's sprite is cached by radius and color, never by strength.

**Bosses with a body.** The Sand Wyrm is a head plus nine plates, each an entity, laid by
`SandWyrmBrain` along the path the head has flown. The Naga is a hooded head plus twelve parts, laid
by `NagaBrain` along a curve from where it came up out of the water to its head while it rears and
strikes, and along its head's path while it swims; it changes between the two only under the water.
Every part carries a `BossPartComponent`: a shot that hits a part lands on the head, which carries
the health (a plate passes on only its `share` of it); the bat flying into it lands nothing. The
brain holds the plates' ids for the whole fight, which is safe only because nothing else moves,
culls or kills them; `GameScreen` removes them when the boss dies. Its health bar is pinned to the
screen (`HealthBarComponent.pinnedTo`), because the head spends half the fight under the sand. The
Naga's is pinned for the same reason, and its fight is five phases, at fifths of its health
(`NAGA_PHASE_*_AT`), where every other boss changes phase at its wounds' marks.

**Wounds.** Every creature's sheet - the bat's, the enemy sheets, the boss sheets - stacks
each frame three times, top to bottom: unhurt, wounded, battered (`WOUND_ROWS`). `WoundSystem`
moves a sprite down a row as its health falls past `WOUND_MARKS`, read off the health every tick,
so healing undoes it. The bosses change phase at the same marks.
- A creature's pixmap is three pictures tall. Its sprite must set `srcHeight` to one row - the
  default is the whole pixmap - and anything sized off the sheet divides by `WOUND_ROWS`.
- The Sand Wyrm's plates and the Naga's parts have no health; their brains draw them from the head's
  row. The sand plume and the Naga's splash are drawn on the top row only.
- The dead are left alone: the bat falls on its one-row death sheet, which is drawn battered.
- A wound also slows an ordinary enemy: `GameScreen` sets its `PaceComponent` from the row
  (`WOUNDED_PACE`, `WOUNDED_FIRE_RATE`) through `WoundSystem`'s callback. A pace is a clock of the
  entity's own, read by the movement, behavior, animation and weapon systems, so a slowed pattern
  keeps its shape and only takes longer. Bosses carry no pace and are never slowed. The recorder's
  forecast copies the pace, or it would dodge a wounded enemy where it is not going to be.
- A generator draws each wound from the creature's own parts (`pa.notch`, `pa.crack`, `pa.ring`,
  `pa.tear`) so it stays put through the animation, and writes the rows with `pa.save_rows`.
  `SpriteSheetTest` checks that every wounded frame differs from the one above it.

**Elites.** From a stage's second minute, a group can arrive with an elite in it: a loner, one of a
swarm, or a formation's leader.
- `WaveDesign.eliteChance` is rolled once per group in `EnemyGenerator.spawnGroup`. Boss summons
  skip that roll, so a boss never calls up an elite.
- An elite has `ELITE_HIT_POINT_FACTOR` its kind's health (not its shield), and fires its kind's
  gun - or the issued one, for a kind that carries none, aimed for one that comes from behind - at
  `ELITE_FIRE_INTERVAL_FACTOR` the interval. Its `ElitePalette` is the color of its `AuraComponent`
  and of its `shot.png` colorway.
  `SpriteSheetTest` holds each colorway's body to its palette's rim.
- `GameScreen` reads its `EliteComponent` as it dies, to pay `ELITE_SCORE_FACTOR` and
  `ELITE_EXPERIENCE_FACTOR` kills' worth; it is still one kill to the streak.
- An aura's halo is drawn between two `RenderSystem` passes: the scenery strip (below z 0), then
  every other sprite. In one pass the strip covered every halo in the jungle and the cave.

**Player progression** is per run and never persisted:
- `PlayerProgress` tracks experience and the bat's level.
- `PowerUp` builds the three-card offer. Maxed cards drop out of it, and the uncapped ones
  guarantee it can always be filled.
- `PlayerLoadout` holds the stats the picks derive.
- `ScoreTracker` carries fractional points between awards.
- Three picks are weapons of their own, each on its own clock: Guardian Orb, Charged Trail and Frost
  Beam. What the first two deal is a share of `shotDamage`, so the gun's upgrades pay into them, and a
  card can say something else once held (`PowerUp.describe`).
  - Orbs and charged wake segments collide as `CollisionGroup.PLAYER_CONTACT`, which meets enemies and
    nothing else, and land through `GameScreen.strike`. They are never spent: a target takes each
    `ContactWeapon` at most once a rehit time, kept on the target (`ContactCooldownComponent`) so it
    goes with it. A boss part's is its boss's, or a wake the Sand Wyrm pours through would land once a
    plate.
  - `OrbitSystem` carries the orbs round the bat clockwise, evenly spaced in creation order.
  - `FrostBeamSystem` freezes ordinary enemies only - never a boss, a part of one or an elite
    (`GameScreen.canFreeze`). A freeze is `FrostSystem` holding the enemy's `PaceComponent` at zero,
    drifting it with the scenery and washing it blue (`TintComponent`, which `RenderSystem` draws like
    a flash). `slowWounded` leaves a frozen enemy's pace alone; the thaw hands back its wound's pace.
    Frozen, it is harmless: `handleCollision` lets the bat through it with no hit either way, while
    the bat's weapons still land.

**Layered music.** A stage's music is one piece cut into eight stems, one per `MusicLayer` (bed,
pulse, drive, lead, boom, roll, chop, fury), mixed live the way Doom 2016 and SSX 3 score their
action. Past the tune the layers are a trap beat growing under the stage's own instruments: 808s
(boom), rolling hi-hats (roll) and chopped voices (chop).
- `MusicDirector` (`:game`) decides what plays. The wave raises a floor and the combo builds on
  it, compared against each layer's threshold. The combo counts in rungs of the HUD's heat ladder
  (`ComboHeat.rung`), not steps of the multiplier, every rung up to SUPERNOVA, so a new title on
  the readout and a new layer land together. Under the tune the thresholds are two rungs apart,
  over it one; fury is the boss's, or a SUPERNOVA streak's. A hit is a thud (a
  muffle and a dip), the level-up dialog holds the music under a muffle instead of pausing it, the
  boss and each boss phase get a one-bar drop and a slam on the downbeat. `GameScreen` feeds it at
  the very top of `update`, ahead of every early return, because the music carries on under the
  level-up dialog. Pause pauses it; the boss's death stops it dead and hands over to the victory's
  fanfare, and the bat's death to the game over's track.
- `StemMixer` (`:engine`, common) decodes the stems as it mixes and lands every change on the
  music's grid (`MusicGrid`, `Quantum`): a layer coming in is all the way up on the beat, one
  going out plays out its beat first. It also runs the muffle, a swept low-pass.
  `DesktopLayeredMusic` and `AndroidLayeredMusic` are only threads pulling frames from it into a
  `SourceDataLine` or an `AudioTrack`.
- The menu's, the game over's and the victory's music go through the same mixer: `Audio.newMusic`
  is one stem at full level (`TrackMusic`), looping or played once. A track that does not loop
  stops at its end (`StemMixer.isLooping`, `hasEnded`) and starts from the top when played again.
- Two threads touch the mixer, and there are no locks, which common code could not take anyway.
  The game thread only calls the setters, which swap in immutable orders; the audio thread owns
  the rest. Every stem is decoded every chunk, heard or not: a stem's place in the music is how far
  it has been read.
- `GameScreen` opens its stage's music the first time it plays it and disposes it with itself, so
  a run with music off never reads the stems and the next stage's run does not play over this one.
- A stage's boss can be fought to a piece of its own (`StageMusic.boss`; the lagoon's Naga): as it
  arrives the stage's music is disposed and the boss's opened, with a director of its own, which
  holds it on its bed for a bar and slams everything in. Every layer is up for the whole fight, so
  its stems are there for the drops at its phases. `MusicStemTest` holds every piece to its grid.
  The fanfare is the same (`GameAssets.VICTORY_MUSIC`): each won run opens its own, because a
  track paused partway resumes from there, and a fanfare has to start from the top.
- The grid is declared twice, in the stage's generator and in its `StageMusic` in `GameAssets`.
  `MusicStemTest` holds them together: every stem whole bars at the declared tempo, and every
  stem's length dividing the longest, so the layers stay in step however long the run goes.

**Input.**
- `PointerTouchHandler` handles touch. On desktop, mouse motion counts as a drag.
- `ControlHandler` turns keyboard and gamepad into the `Controls` intent: two axes plus
  edge-triggered `GameButton`s. `ComposeKeyAdapter` (desktop) and `AndroidKeyAdapter` (keyboard and
  pads) feed it.
- Android's Back arrives as `GameButton.BACK` from the back-pressed dispatcher, because gesture
  navigation raises no key event.

**Rendering.** One `Graphics` for every platform: `ComposeGraphics`, in the engine's common code,
drawn through Compose's Canvas - HWUI, and so the GPU, on Android; Skia on the desktop (and on
iOS, for an app to come). There is no framebuffer bitmap.
- `present` is recorded, call by call, and the host draws the recording in Compose's draw phase
  with `drawGameFrame`: scaled from frame pixels to the `FrameFit` rectangle in one transform, and
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
  sprites and rectangles keep their frame pixels, ovals and outlines are worked out on the grid
  with their weight kept, and turned sprites turn on it. Lines stay on the frame's grid, since
  `Raster` only draws them one grid pixel wide. A setting for it would be wired up the way the
  `DisplayMode` is, from the host.
- The deliberate `- 1` in `drawPixmap` paints a column and a row short, and is why background
  tiles overlap by one column. A "fix" would shift every sprite by a pixel.
- Text is the exception, by choice: laid out in frame pixels, so the game places it as before, but
  drawn at the screen's resolution and antialiased. An outline is a stroke around the glyphs
  (`drawOutlinedString`), not the string stamped around itself. Each platform draws its own
  sans-serif face (Roboto on Android, Arial on Windows, usually DejaVu Sans on Linux), and the same
  string comes out at different widths. Right-align, center or wrap text by
  `Graphics.measureString`, never by a count of characters.
- A translucent blend can round a step apart on the GPU, on the CPU and between scales; the tests
  allow a step a channel and no more.
- The desktop decodes assets with Skia and marks them immutable (`DesktopGame.loadImage`). Compose's
  desktop canvas wraps a bitmap in a new Skia image every time it draws it, and copies one that
  could still change: the desert's ground was a copy of its whole sheet for every strip.
- A new `Graphics` primitive needs a default implementation or a `ComposeGraphics` override, and the
  test fakes need updating.

**Assets.**
- `assets/` at the root is packaged as Android assets by `:app` and as classpath resources by
  `:desktop`.
- Menu strings and drawables are Compose resources in `game/src/commonMain/composeResources`, with
  the `Res` class in `at.smiech.cyanbat.resources`.
- CMP 1.12 does not copy those resources into the APK. The `StageComposeResources` task in
  `app/build.gradle.kts` works around that; without it the app crashes on the first
  `painterResource`.
- The death sound is MP3, which the desktop decodes through the mp3spi/jlayer service providers.
  No code references them; `Mp3DecodingTest` guards it.
- Music is not MP3. The stages' stems and the menu's, game over's and victory's tracks are IMA
  ADPCM WAVs in `assets/music/`, decoded in common code (`ImaAdpcmClip`), because a platform MP3
  decoder pads and trims a file's ends its own way: stems a few milliseconds apart flam on every
  drum hit, and a loop gets a gap. They are 22.05 kHz stereo, a quarter of their PCM size.
- Generated sound effects are WAV: `javax.sound.sampled` reads PCM natively, and SoundPool can
  `openFd` them uncompressed from the APK.
- Every effect is a `SoundEffect` - its file, its volume, and the least gap between two plays of
  it - and a run plays them through its `SoundBoard`. The gap is what keeps a fan of hits landing
  on one tick from playing five times over: SoundPool would stack the copies, where a desktop
  `Clip` starts over. The files are written near full scale, so the volumes are the whole balance,
  between the effects and against the music; they were set by measuring each against a stage's
  middle layers, on a full-range speaker and through a phone's.
- The launcher icon is adaptive: `mipmap-anydpi/ic_launcher.xml` over three vector layers in
  `app/src/main/res/drawable`, which redraw the pixel art crisply at any size a launcher picks.
- The desktop installers take `desktop/icons/cyanbat.{icns,ico,png}` through `nativeDistributions`.
  The window hands the OS every size in `desktop/src/main/resources/icons` itself (`WindowIcon`),
  because Compose's `icon` parameter renders one image and Windows shrinks it into noise.
  `AppIconTest` checks both sets, since nothing else reads the installers' before a release.

**The art is generated.** `tools/generate_*.py`, built on `tools/pixelart.py`, produce every
sprite sheet, background, obstacle, stage preview, the framed title, the app icons and the WAV
effects; `tools/generate_*_music.py`, built on `tools/musicsynth.py`, produce all of the music.
The death sound's MP3, `gameover.png` and `tools/title_lettering.png` are the exceptions.
- To change art, change the script and re-run it; never edit its output, the icons' vector XML
  included.
- The scripts are deterministic: re-running an unchanged one must reproduce the committed file
  byte for byte.
- `SpriteSheetTest` pins each sheet's dimensions and color ceiling.
- Re-run `generate_stage_previews.py` and `generate_icons.py` after changing any sheet they
  compose.
- Palette rule: the player is cool, everything hostile is warm.
- The music scripts are scores - which notes, on which instrument, when - and `musicsynth.py` is
  the band. Everything in it is circular: a note, a reverb tail or an echo that runs past the end
  of a loop carries on at its start, so a stem has no seam. They pin numpy and scipy, because
  floating-point results are only byte for byte on the same versions. A stage's stems are leveled
  as a set, so the balance between the layers is the score's; change a part's level in its script.
  The menu's and game over's tracks are leveled to a loudness (`write_track`), set against the
  stages' mixes.
- The game over's track plays once, so its script renders it with room past its end and cuts it
  there; the circular tools would otherwise wrap its last gong round onto its first beat.
- Nobody can hear a stem from its numbers. After changing a score, listen to it: the stems are
  ordinary WAVs, and a run in the game is the real test.

**The README's GIF is recorded, not generated.** `recordGameplay` runs the `recorder` source
set in `:desktop` (`desktop/src/recorder`), which never ships in the app.
- It flies each stage in a real `DesktopGame`, headless, stepping the shared `GameLoop` on a fake
  clock. An `Autopilot` steers through `ControlHandler.onAxis`, as a game pad would, and forecasts
  enemies by running copies of them through the engine's own movement systems. `Montage` then cuts
  each run to a few short clips, and `GifEncoder` writes the stages as one reel, each stage on a
  palette of its own.
- The reel shows less of each stage than of the one before (`STAGE_COVERAGE`), to leave the later
  stages to discover: the jungle whole, a few glimpses of the cave, fewer of the desert, and only the
  lagoon's sunrise. Neither the desert's boss nor the lagoon's is ever shown: a stage whose footage
  is only its hours (`Coverage.onlyScenery`) is flown no further than the last of them, so no frame
  of either boss is taped - and the autopilot need not survive the lagoon to its Naga.
- Runs are random, so unlike the art a re-recording is never byte for byte the same. Re-record after
  a visible change.
- `RunProbe` reads a handful of `GameScreen`'s private fields by reflection (`world`, `batId`,
  `offer`, `stageComplete`, `progress`, `scoring`, `bannerText`, `bannerTime`). Renaming one still
  compiles; `RunProbeTest`, which flies a few seconds of the cave, is what fails.
- The same hosting tests `GameScreen` itself: `GameScreenTest` builds a run on a `DesktopGame`,
  sets up a moment through `RunProbe` and the public `enmGen`, and steps it with `update`.

## Conventions

- **"Stage" vs "level".** The jungle, the cave, the desert and the lagoon are *stages*. "Level" only
  ever means the bat's experience level, which buys power-ups. Keep the two apart in code, comments
  and on-screen text. The jungle was the forest, and stage 2, until it moved to the front; persisted
  highscores were moved with it (`StageOrderMigration`, and its desktop twin).
- **American English** everywhere, identifiers and player-facing text included: color, center,
  behavior, armor.
- **Comments explain why, in plain sentences.** Types get KDoc saying what they are for.
  Non-obvious choices - system order, clamps, arming delays - carry their reasoning inline. Match
  the surrounding density.
- **Look at the result.** Unit tests cover game logic, not what reaches the screen, device input
  or the activity lifecycle, and draw-call assertions happily pass on output that looks wrong.
  Check visual changes by drawing a frame through `drawGameFrame` into an `ImageBitmap` the size of
  a screen, as `ComposeGraphicsTest` does, or by running the game (`:desktop:run`, `run-cyanbat`)
  and looking at the screenshots.
- **Branches and PRs.**
  - Branch as `feat/`, `fix/`, `chore/`, `ci/`, `perf/` or `refactor/` and open a PR against
    `main`. The owner merges on GitHub.
  - Commit messages and PR titles are plain sentences about the change ("Give the bat a death
    animation"), with no conventional-commit prefixes.
  - PR bodies cover what changed, why, notes for review, and testing with concrete evidence: the
    commands run, test counts, what the emulator showed.
- **Versions and releases.**
  - Every artifact's version comes from `cyanbat.version` in `gradle.properties`. The root build
    derives `versionCode` as `major * 10000 + minor * 100 + patch`.
  - Pushing a bare tag (`git tag 2.4 && git push origin 2.4`) makes the Release workflow build and
    publish the APK and the desktop installers. A `-rcN` suffix publishes a pre-release. Running
    the workflow by hand is a rehearsal that publishes nothing.
  - Keep `cyanbat.version` in step with the newest tag, through a `chore/bump-version-X.Y` PR
    titled "Stamp untagged builds X.Y".
