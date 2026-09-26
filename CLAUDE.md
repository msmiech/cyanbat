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
uv run tools/generate_enemy_sprites.py                   # regenerate an asset; each script declares its own deps
```

- `commonTest` runs on the JVM (`jvmTest`) and, on a Mac, on the iOS simulator
  (`iosSimulatorArm64Test`, which needs Xcode; on a Mac `build` runs it too). No Android host tests
  or instrumentation tests are configured. `:app` has plain JVM unit tests of its own
  (`app/src/test`), for its DataStore code.
- **Every host compiles `:engine` and `:game` for iOS** as part of `build`, test code included,
  since Kotlin cross-compiles Apple libraries. So a JVM-only API in `commonMain` fails the build
  anywhere, and so does a comma in a backticked test name, which Kotlin/Native rejects. Only
  linking and running need a Mac; CI's `ios` job does that.
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
- Sprite rotation is cosmetic. Collision boxes always stay axis-aligned.

**A run.** `GameScreen` is one run of one stage.
- Pause, the level-up offer and "stage complete" are state inside it rather than separate
  `Screen`s, because swapping screens disposes the run. `GameOverScreen` is its own screen.
- Moving to the next stage builds a fresh `GameScreen`. That is what resets score, experience and
  power-ups.
- Overlays read taps through `TapDetector` plus an arming delay, so the finger that was steering
  when an overlay opened does not pick something when it lifts.
- In-game text (HUD, banners, overlays) is literal strings drawn at hard-coded framebuffer
  coordinates in `GameScreen`. A longer string can knock an overlay out of line. Menu text is in
  `composeResources/values/strings.xml`.

**Stage content is split four ways.**
- `Stage` (registered in `GameAssets.load`) is how a stage looks and sounds.
- `StageDesign` is its waves and boss; `StageDesign.forStage` maps a stage id to its design.
- `StageProgression` turns seconds elapsed into the current wave's stats, scaled up per stage.
- `EnemyGenerator` owns only the clock and the dice, and spawns through `EntityFactory`.

Adding a stage touches all four, plus new generators in `tools/`, a preview card, and the stage
select's strings.

**Player progression** is per run and never persisted:
- `PlayerProgress` tracks experience and the bat's level.
- `PowerUp` builds the three-card offer. Maxed cards drop out of it, and the uncapped ones
  guarantee it can always be filled.
- `PlayerLoadout` holds the stats the picks derive.
- `ScoreTracker` carries fractional points between awards.

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
- Music is MP3, which the desktop decodes through the mp3spi/jlayer service providers. No code
  references them; `Mp3DecodingTest` guards them.
- Generated sound effects are WAV: `javax.sound.sampled` reads PCM natively, and SoundPool can
  `openFd` them uncompressed from the APK.

**The art is generated.** `tools/generate_*.py`, built on `tools/pixelart.py`, produce every
sprite sheet, background, obstacle, stage preview, the framed title and the WAV effects. The MP3s,
`gameover.png` and `tools/title_lettering.png` are the exceptions.
- To change art, change the script and re-run it; never edit the PNG.
- The scripts are deterministic: re-running an unchanged one must reproduce the committed file
  byte for byte.
- `SpriteSheetTest` pins each sheet's dimensions and color ceiling.
- Re-run `generate_stage_previews.py` after changing any sheet it composes.
- Palette rule: the player is cool, everything hostile is warm.

## Conventions

- **"Stage" vs "level".** The cave and the forest are *stages*. "Level" only ever means the bat's
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
