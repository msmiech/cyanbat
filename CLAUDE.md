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
not Compose: screens draw into a fixed **480x320 framebuffer** through the engine's `Graphics`,
and the host blits that to the window.
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

**The framebuffer's shape is part of the game design.** It is 3:2, and spawn points, boss stations
and wave pacing are all tuned to 480x320. `FrameFit` fits it to the screen according to the
player's `DisplayMode` (stretch, black bars, or the default ambient bars) and maps touches back
through the same rectangle. Do not widen the playfield for wide screens; it would change difficulty
by device.

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
  so something that turns to face its heading carries `FacesVelocityComponent(180f)`.

**A run.** `GameScreen` is one run of one stage.
- Pause, the level-up offer and "stage complete" are state inside it rather than separate
  `Screen`s, because swapping screens disposes the run. `GameOverScreen` is its own screen.
- Moving to the next stage builds a fresh `GameScreen`. That is what resets score, experience and
  power-ups.
- Overlays read taps through `TapDetector` plus an arming delay, so the finger that was steering
  when an overlay opened does not pick something when it lifts.
- In-game text (HUD, banners, overlays) is literal strings drawn at framebuffer coordinates in
  `GameScreen` and `GameOverScreen`. The rows are hard-coded, but every centered or right-aligned
  line is placed by its measured width, so rewording one needs no new x. Menu text is in
  `composeResources/values/strings.xml`.

**Stage content is split four ways.**
- `Stage` (registered in `GameAssets.load`) is how a stage looks and sounds. Its `Backdrop` is
  either a `Strip` (the cave, the forest: one tiled image) or a `Nightfall` (the desert: a sky the
  game draws, going from noon to night on the stage clock, over parallax bands of ground).
- `StageDesign` is its waves and boss; `StageDesign.forStage` maps a stage id to its design.
- `StageProgression` turns seconds elapsed into the current wave's stats, scaled up per stage.
- `EnemyGenerator` owns only the clock and the dice, and spawns through `EntityFactory`.

Adding a stage touches all four, plus new generators in `tools/` (its music among them, with a
`StageMusic` in `GameAssets`), a preview card, the stage select's strings, and the recorder's
`STAGE_COVERAGE`, which says how much of the stage the README's reel shows.

**The desert's day.** `Daylight` is a pure function of how far through its day the stage is -
`Daylight.position`, elapsed time over the boss's arrival - and says what the sky, sun, moon and
stars look like then. `NightfallSystem` draws it, first in the system order, and puts every entity
carrying a `CrossfadeComponent` (the desert's obstacles) in the same light.
- Scenery lit by the day is drawn once per `Daylight.KEYFRAMES` entry (noon, golden hour, sunset,
  night), stacked top to bottom on its sheet, and crossfaded between neighbors with
  `drawPixmapFaded`. A generator writes the shapes once as materials and each keyframe as a palette,
  so the rows cannot drift apart.
- The sun the player can see sets on the right, so the desert's scenery is lit from the right; the
  night palettes turn that round, to the moon on the left.

**Bosses with a body.** The Sand Wyrm is a head plus nine plates, each an entity, laid by
`SandWyrmBrain` along the path the head has flown. Every part carries a `BossPartComponent`: a
shot that hits a part lands on the head, which carries the health (a plate passes on only its
`share` of it); the bat flying into it lands nothing. The brain holds the plates' ids for the whole fight, which is safe only because nothing
else moves, culls or kills them; `GameScreen` removes them when the boss dies. Its health bar is
pinned to the screen (`HealthBarComponent.pinnedTo`), because the head spends half the fight under
the sand.

**Wounds.** Every creature's sheet - the bat's, the three enemy sheets, both boss sheets - stacks
each frame three times, top to bottom: unhurt, wounded, battered (`WOUND_ROWS`). `WoundSystem`
moves a sprite down a row as its health falls past `WOUND_MARKS`, read off the health every tick,
so healing undoes it. The bosses change phase at the same marks.
- A creature's pixmap is three pictures tall. Its sprite must set `srcHeight` to one row - the
  default is the whole pixmap - and anything sized off the sheet divides by `WOUND_ROWS`.
- The Sand Wyrm's plates have no health; `SandWyrmBrain` draws them from the head's row. Its sand
  plume is drawn on the top row only.
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
  gun - or the issued one, for a kind that carries none - at `ELITE_FIRE_INTERVAL_FACTOR` the
  interval. Its `ElitePalette` is the color of its `AuraComponent` and of its `shot.png` colorway.
  `SpriteSheetTest` holds each colorway's body to its palette's rim.
- `GameScreen` reads its `EliteComponent` as it dies, to pay `ELITE_SCORE_FACTOR` and
  `ELITE_EXPERIENCE_FACTOR` kills' worth; it is still one kill to the streak.
- An aura's halo is drawn between two `RenderSystem` passes: the scenery strip (below z 0), then
  every other sprite. In one pass the strip covered every halo in the cave and the forest.

**Player progression** is per run and never persisted:
- `PlayerProgress` tracks experience and the bat's level.
- `PowerUp` builds the three-card offer. Maxed cards drop out of it, and the uncapped ones
  guarantee it can always be filled.
- `PlayerLoadout` holds the stats the picks derive.
- `ScoreTracker` carries fractional points between awards.

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
  boss and each boss phase get a one-bar drop and a slam on the downbeat, and a won stage winds
  down to its bed. `GameScreen` feeds it at the very top of `update`, ahead of every early return,
  because the music carries on under the overlays. Pause pauses it; death hands over to the game
  over's track.
- `StemMixer` (`:engine`, common) decodes the stems as it mixes and lands every change on the
  music's grid (`MusicGrid`, `Quantum`): a layer coming in is all the way up on the beat, one
  going out plays out its beat first. It also runs the muffle, a swept low-pass.
  `DesktopLayeredMusic` and `AndroidLayeredMusic` are only threads pulling frames from it into a
  `SourceDataLine` or an `AudioTrack`.
- The menu's and the game over's music go through the same mixer: `Audio.newMusic` is one stem at
  full level (`TrackMusic`), looping or played once. A track that does not loop stops at its end
  (`StemMixer.isLooping`, `hasEnded`) and starts from the top when played again.
- Two threads touch the mixer, and there are no locks, which common code could not take anyway.
  The game thread only calls the setters, which swap in immutable orders; the audio thread owns
  the rest. Every stem is decoded every chunk, heard or not: a stem's place in the music is how far
  it has been read.
- `GameScreen` opens its stage's music the first time it plays it and disposes it with itself, so
  a run with music off never reads the stems and the next stage's run does not play over this one.
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

**Rendering parity.**
- `AndroidGraphics` and `DesktopGraphics` must put the same pixels on the framebuffer. That goes
  down to the deliberate `- 1` in `drawPixmap`, which paints a column short and is why background
  tiles overlap by one column.
- Lines, ovals and oval outlines are worked out once, in common code (`Raster`), and laid down as
  rectangles, which both backends fill alike. Neither backend draws its own: Skia and Java2D light
  different edge pixels even with antialiasing off. Android fills an oval's rectangles as one
  polygon instead, for speed, and that covers exactly the same pixels.
- Shapes are never antialiased. Android's `Paint()` antialiases by default since Android 12, so the
  shape paint turns that off by hand; Android's text has a paint of its own, which keeps it on.
- Text is the exception. Each backend draws it in its platform's sans-serif face (Roboto on
  Android, Arial on Windows, usually DejaVu Sans on Linux), and the same string comes out at
  different widths. Right-align, center or wrap text by `Graphics.measureString`, never by a count
  of characters: a layout counted out in Arial runs off the frame in DejaVu Sans.
- A new `Graphics` primitive needs a default implementation or both overrides, and the test fakes
  need updating.
- Blits are nearest-neighbor.

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
- Music is not MP3. The stages' stems and the menu's and game over's tracks are IMA ADPCM WAVs in
  `assets/music/`, decoded in common code (`ImaAdpcmClip`), because a platform MP3 decoder pads
  and trims a file's ends its own way: stems a few milliseconds apart flam on every drum hit, and
  a loop gets a gap. They are 22.05 kHz stereo, a quarter of their PCM size.
- Generated sound effects are WAV: `javax.sound.sampled` reads PCM natively, and SoundPool can
  `openFd` them uncompressed from the APK.
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
  stages to discover: the cave whole, a few glimpses of the forest, fewer of the desert. The desert's
  boss is never shown; its flight stops before the Sand Wyrm arrives, so no frame of it is taped.
- Runs are random, so unlike the art a re-recording is never byte for byte the same. Re-record after
  a visible change.
- `RunProbe` reads a handful of `GameScreen`'s private fields by reflection (`world`, `batId`,
  `offer`, `stageComplete`, `progress`, `scoring`, `bannerText`, `bannerTime`). Renaming one still
  compiles; `RunProbeTest`, which flies a few seconds of the cave, is what fails.
- The same hosting tests `GameScreen` itself: `GameScreenTest` builds a run on a `DesktopGame`,
  sets up a moment through `RunProbe` and the public `enmGen`, and steps it with `update`.

## Conventions

- **"Stage" vs "level".** The cave, the forest and the desert are *stages*. "Level" only ever means the bat's
  experience level, which buys power-ups. Keep the two apart in code, comments and on-screen text.
  The cave enemies are drawn as imps, but some internal names still say drone
  (`BossKind.CAVE_DRONE`).
- **American English** everywhere, identifiers and player-facing text included: color, center,
  behavior, armor.
- **Comments explain why, in plain sentences.** Types get KDoc saying what they are for.
  Non-obvious choices - system order, clamps, arming delays - carry their reasoning inline. Match
  the surrounding density.
- **Look at the result.** Unit tests cover game logic, not what reaches the screen, device input
  or the activity lifecycle, and draw-call assertions happily pass on output that looks wrong.
  Check visual changes by rendering through the real `DesktopGraphics` into a `BufferedImage`, or
  by running the game (`:desktop:run`, `run-cyanbat`) and looking at the screenshots.
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
