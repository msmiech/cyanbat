# AGENTS.md

This file guides coding agents - Claude Code, Codex, Gemini CLI, Copilot and the like - working
with the code in this repository. Their skills are in `.agents/skills/`; see Agent files.

CyanBat is a side-scrolling shooter that runs on Android, on iPhone and iPad, on the desktop and in
the browser from one Kotlin Multiplatform codebase. The README covers features, controls and the
release secrets. This file holds what applies to every change; how each part of the game works is
in `docs/architecture/`, a file a part - read a part's before changing it (see Where to read more).

## Guard rails

What an agent does not do unasked. When one of these stands in the way of the task, stop and say so
rather than work around it.

**Git and releases.**

- Work on a branch (see Conventions). Never commit to `main` or push to it, never force-push or
  rewrite pushed history, and never merge a PR: the owner merges on GitHub.
- Commit, push and open a PR only when the task asks for it.
- Never create, move or push a tag, and never run the Release workflow: a tag publishes a release to
  players.
- Leave `cyanbat.version` and the iOS app's versions alone outside a version bump PR.
- Never skip a check to get a change through: no `--no-verify`, no disabled or deleted test, no
  loosened lint, no `@Suppress` added to quiet one.
- Other sessions may be at work in this checkout. Check which branch you are on before you commit,
  and never `git reset --hard`, `git clean`, `git checkout --` or stash over changes you did not
  make, nor delete a branch or worktree that is not yours.

**Secrets and privacy.**

- Never print, copy or commit a keystore, `local.properties`, `iosApp/Configuration/Local.xcconfig`
  or the value of an `ANDROID_*` signing variable. Signing reaches the build through the
  environment alone.
- Leave the signing configuration, the release workflow and the workflows' `permissions` alone
  unless they are the task.
- The game connects to nothing and has no analytics, tracking or crash reporting, which
  `PRIVACY_POLICY.md` promises its players. Add none of them, and no permission to the manifest,
  without the owner. Anything new kept on the device needs its row in the policy.

**Dependencies and the build.**

- Do not add a dependency, or change a version in `gradle/libs.versions.toml` or of a workflow's
  action, unless asked: Dependabot brings them monthly, grouped so Kotlin and Compose move
  together. `gradle/actions` stays below v6, which is the owner's call (`.github/dependabot.yml`).
- No new opt-in to an experimental API (`@OptIn`, `languageSettings.optIn`): find a stable, typed
  one. The few in the code are older than this rule; do not add to them.
- Do not take out a workaround because it looks odd - `StageComposeResources`, the download URLs the
  root build clears, the `- 1` in `drawPixmap`. Each has its reason written beside it; show the
  reason is gone first.

**Scope.**

- Do what was asked. No refactoring, renaming or reformatting along the way.
- Do not retune the game in passing: the numbers in `CyanBatConstants`, the wave designs and the
  effects' volumes were set by playing and measuring.
- Never edit a generated file; change its script and run it again. If a script you did not change
  writes different bytes on your machine, restore its output rather than commit it.
- Never rename or reuse a key a player's store already holds - `Achievement.key`,
  `cleared_stage_<id>`, a highscore's or a setting's name - without a migration, as
  `StageOrderMigration` is one. The desktop's key names are the browser's and iOS's too.
- A test that pins something - `SpriteSheetTest`, `MusicStemTest`, `TranslationsTest`,
  `GameTextLayoutTest`, `SoundEffectFilesTest`, `AppIconTest`, `ComposeGraphicsTest` - changes only
  together with what it pins, and on purpose.
- The lagoon's second boss is a secret. Nothing a player meets outside the run shows or names it:
  not the stage select, the README, the recorder's reel or a store listing.

**Before saying it is done.**

- Run the tests of what you touched, and `./gradlew build` before a PR. Report what ran and what it
  showed, failures included.
- Look at a change that reaches the screen (see Conventions).
- Say plainly what you could not check: how a stem sounds, anything on an iPhone, input on a real
  device.
- Do not wipe an emulator, clear the app's data or uninstall the app unless the task needs it - it
  takes the highscores with it - and do not stop an emulator you did not start.

## Commands

```bash
./gradlew build                                          # everything CI runs: assemble, lint, unit tests
./gradlew :engine:jvmTest :game:jvmTest :desktop:test :app:testDebugUnitTest   # just the unit tests
./gradlew :game:jvmTest --tests 'at.smiech.cyanbat.ScoreTrackerTest'   # one class
./gradlew :engine:jvmTest --tests '*CollisionSystem*'    # wildcards work too
./gradlew :app:lint                                      # lint exists only in :app
./gradlew :desktop:run                                   # play on the desktop, no emulator
./gradlew :web:wasmJsBrowserDevelopmentRun               # play in the browser, on localhost:8080
./gradlew :web:wasmJsBrowserDistribution                 # the optimized site, in web/build/dist/wasmJs/productionExecutable
./gradlew :app:installDebug                              # install on a running emulator
./gradlew :engine:iosSimulatorArm64Test :game:iosSimulatorArm64Test   # the shared tests on the iOS simulator; Mac only
cd iosApp && xcodegen generate && open CyanBat.xcodeproj  # the iPhone and iPad app, in Xcode; Mac only
./gradlew -Pkotlin.native.enableKlibsCrossCompilation=true :ios:compileKotlinIosArm64   # type-check the iOS code off a Mac
./gradlew :desktop:recordGameplay                        # re-record the README's GIF, docs/gameplay.gif
./gradlew :desktop:renderEndlessPreview                  # re-render the endless stage's card, stage5_preview.png
uv run tools/generate_enemy_sprites.py                   # regenerate an asset; each script declares its own deps
```

- `commonTest` runs on the JVM (`jvmTest`) and, on a Mac, on the iOS simulator. It does not run in
  the browser - the `wasmJs` test tasks are off - though `build` still compiles the shared code for
  Kotlin/Wasm and builds the site. There are no Android host or instrumentation tests; `:app` has
  plain JVM unit tests (`app/src/test`) for its DataStore code.
- **Off a Mac, `build` skips the iOS targets**, so it needs no Kotlin/Native toolchain. It still
  compiles `commonMain` as common code, so a JVM-only API there fails anywhere. What only
  Kotlin/Native catches - a comma in a backticked test name, for one - fails in CI's `ios` job, or
  locally with `-Pkotlin.native.enableKlibsCrossCompilation=true`, at a one-time download of about
  800 MB.
- After changing the Kotlin or Compose version, refresh `kotlin-js-store/wasm/yarn.lock` with
  `./gradlew kotlinWasmUpgradeYarnLock`.
- To run, drive and screenshot the app on an emulator, use the `run-cyanbat` skill
  (`.agents/skills/run-cyanbat/`). Its gotchas cover what trips up device testing.
- **In a git worktree** there is no `local.properties`, so Android tasks fail with "SDK location not
  found". Run them with `ANDROID_HOME` set to the SDK. JVM-only tasks - the tests, `:desktop:*` - do
  not need it.
- JDK 25 comes from the Gradle toolchain (`jvmToolchain` in the version catalog), and Gradle itself
  runs on the same JDK (`gradle/gradle-daemon-jvm.properties`); nothing to install. Bump the two
  together: the second is regenerated with `./gradlew updateDaemonJvm --jvm-version=N`.

## Architecture

`:engine` <- `:game` <- `:app` (Android), `:ios` (iPhone and iPad), `:desktop` (Compose Desktop)
and `:web` (Compose in the browser, on Kotlin/Wasm). The engine and the whole game, menu UI
included, are common code; the four platform modules only supply platform pieces. The few platform
differences inside `:game` are `expect`/`actual` (`ui/Platform.kt`).

**Two kinds of UI.** The menu - main screen, stage select, settings, credits - is shared Compose
(`CyanBatMenu`). The game itself is not laid out by Compose, but it is drawn through it: screens
draw a fixed **640x360 frame** through the engine's `Graphics`, and the host draws that into a
Compose `Canvas`. Each host builds a `CyanBatEnvironment` - assets, haptics, stores, settings,
text - and hands it to `GameScreen`. Persistence is interfaces in `:game`, backed by DataStore in
`:app`, `java.util.prefs` in `:desktop`, and the shared `KeyValueStores` in `:web` and `:ios`.

**Timing.** The shared `GameLoop` clamps a frame's delta to 50 ms, so a resume cannot fast-forward
the run. `GameScreen` steps `world.update` in fixed 19 ms ticks (`TICK_INITIAL`), and everything
that paces gameplay - `EnemyGenerator`, `ObstacleGenerator`, the stage clock - is fed that tick.
Never pace gameplay with the wall clock or a coroutine: a paused game has to be a paused stage.

**The frame's shape is part of the game design.** It is 16:9, 640x360 (`FRAME_BUFFER_WIDTH`,
`FRAME_BUFFER_HEIGHT`), and spawn points, boss stations and the overlays' rows are laid out against
it. Speeds are in frame pixels. `FrameFit` fits it to the screen by the player's `DisplayMode` and
maps touches back through the same rectangle. Do not widen the playfield for wide screens; it would
change difficulty by device.

**ECS** (`at.smiech.engine.ecs`):

- `World` stores components struct-of-arrays with a 64-bit signature per entity, so there can be at
  most 64 component types.
- Entity ids are recycled when removals are finalized at the end of `World.update`. Never keep an id
  across frames to look something up later. Copy what you need at spawn instead: a shot's color is
  read off its shooter when the shot is created, and the shot carries it on (`ColorwayComponent`).
- There is no `removeComponent`. Clamp or disarm a component rather than taking it off.
- Systems resolve `ComponentMapper`s in `onAttach` and iterate with the allocation-free `forEach`.
- Engine systems know nothing about CyanBat; game-specific work goes through callbacks
  (`WeaponSystem`, `TrailSystem`, `DeathSystem`, `CollisionSystem`).
- **System order is load-bearing.** It is set, with a comment per placement, in `GameScreen`'s
  `init`; read it before adding a system.
- Sprite rotation is cosmetic. Collision boxes always stay axis-aligned. Hostile art faces left, so
  something that turns to face its heading carries `FacesVelocityComponent(180f)` - all but the
  lagoon's shark, which comes from behind and so is drawn facing right (`drawnFacingRight`).

**What else holds everywhere.**

- **A run is one `GameScreen`.** Pause, the level-up offer and "stage complete" are state inside
  it, because swapping screens disposes the run. On Android the run lives only in
  `CyanBatGameActivity`, and recreating it starts the run over, so it takes every configuration
  change in place (`configChanges`, each with its reason in the manifest).
- **Balance is Hard's.** Stages, waves and bosses are tuned against Hard alone; the easier
  difficulties are discounts on it, and touch only `StageProgression.enemyDensity` and the bat's
  start in `PlayerLoadout`. Tests and the recorder fly Hard.
- **Whatever keeps the bat from harm sets its `hitCooldown`** (`InvulnerabilitySystem.grant`), and
  then shows by itself.
- **Every word on screen is a Compose resource**, in English, German and Polish. The game's logic
  names its words by `StringResource` and never holds a string; a run reads them from its
  `GameText`.
- **Everything but text is on the pixel grid**: aliased, nearest-neighbor, shapes worked out in
  `Raster` and never drawn as Skia's own. Text is placed by `Graphics.measureString`, never by a
  count of characters, since each platform's face has its own widths.
- **A new `Graphics` primitive** needs a default implementation or a `ComposeGraphics` override, and
  the test fakes updated.
- **The dark is built for old phones**: about 0.2 ms a frame on the UI thread. Measure before and
  after touching it.
- **A creature's sheet is three rows tall** (`WOUND_ROWS`). Its sprite sets `srcHeight` to one row,
  and anything sized off the sheet divides by the rows.
- **Music is never MP3.** Stems and tracks are IMA ADPCM WAVs decoded in common code, effects plain
  PCM WAVs. A piece's grid is declared in its generator and in its `StageMusic`, and
  `MusicStemTest` holds the two together. The mixer has no locks: the game thread only calls its
  setters.
- **The art and the music are generated** by `tools/generate_*.py`, byte for byte the same on every
  run. Palette rule: the player is cool, everything hostile is warm.
- **Assets live once**, in `assets/` at the root, and each host packages them its own way.
- **`RunProbe` reads `GameScreen`'s private fields by reflection.** Renaming one still compiles;
  `RunProbeTest` is what fails.

### Where to read more

| Before changing                                                                        | Read                                           |
|----------------------------------------------------------------------------------------|------------------------------------------------|
| The menu, a host - the activities, the desktop's window, the page, iOS - insets, theme | [platforms.md](docs/architecture/platforms.md) |
| `GameScreen`, overlays, a boss's fall, difficulty, power-ups, achievements             | [run.md](docs/architecture/run.md)             |
| A string, or adding a language                                                         | [languages.md](docs/architecture/languages.md) |
| A stage, its waves or backdrop, the lagoon, the endless stage, the sky's day           | [stages.md](docs/architecture/stages.md)       |
| A boss with a body, wounds, elites                                                     | [creatures.md](docs/architecture/creatures.md) |
| `Graphics`, anything drawn, the frame and its timing, the cave's dark and its boss     | [rendering.md](docs/architecture/rendering.md) |
| Music, the director, the mixer                                                         | [audio.md](docs/architecture/audio.md)         |
| Touch, the stick, keys, pads, prompts, vibration                                       | [input.md](docs/architecture/input.md)         |
| Assets, icons, a generator in `tools/`, the recorder and the tests it hosts            | [assets.md](docs/architecture/assets.md)       |

Keep them true: a change that makes one of them wrong changes it in the same PR. What applies to
every change goes in this file, and what explains one part goes in that part's.

## Conventions

- **"Stage" vs "level".** The jungle, the cave, the desert and the lagoon are *stages*. "Level" only
  ever means the bat's experience level, which buys power-ups. Keep the two apart in code, comments
  and on-screen text, in every language. The jungle was the forest, and stage 2, until it moved to
  the front; persisted highscores were moved with it (`StageOrderMigration`, and its desktop twin).
- **American English** everywhere, identifiers and player-facing text included: color, center,
  behavior, armor.
- **Comments explain why, in plain sentences.** Types get KDoc saying what they are for. Non-obvious
  choices - system order, clamps, arming delays - carry their reasoning inline. Match the
  surrounding density.
- **Formatting is Android Studio's**, as it reformats and optimizes imports out of the box: the
  Kotlin official style, wrapped at 100 columns (`.editorconfig`), an argument list too long for
  its line chopped down one argument a line, a wrapped `&&` or `||` operand indented 8 past its
  first line, no wildcard imports. Format what you change before committing, in the IDE (Code >
  Reformat File, with Optimize imports). Without the IDE, Android Studio's `bin/format.bat`
  (`format.sh` on Linux and a Mac) can check a file with `-dry -allowDefaults <files>`, but run
  outside the IDE it indents those operands 4, so keep the 8 by hand. It cannot run while the IDE
  has its settings open; point `STUDIO_PROPERTIES` at an `idea.properties` with an
  `idea.config.path` and `idea.system.path` of its own. Leave lines you did not change as they are.
- **Look at the result.** Unit tests cover game logic, not what reaches the screen, device input or
  the activity lifecycle, and draw-call assertions happily pass on output that looks wrong. Check
  visual changes by drawing a frame through `drawGameFrame` into an `ImageBitmap` the size of a
  screen, as `ComposeGraphicsTest` does, or by running the game (`:desktop:run`, `run-cyanbat`)
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
    - Pushing a bare tag makes the Release workflow build and publish the APK, the desktop
      installers and an unsigned iOS `.ipa`. A `-rcN` suffix publishes a pre-release. Running the
      workflow by hand is a rehearsal that publishes nothing. Both are the owner's to do.
    - `cyanbat.version` is kept in step with the newest tag through a `chore/bump-version-X.Y` PR
      titled "Stamp untagged builds X.Y". The same PR bumps the iOS app's `MARKETING_VERSION` and
      `CURRENT_PROJECT_VERSION` in `iosApp/Configuration/Config.xcconfig`.
    - The iOS app goes to the App Store from a Mac, by `iosApp/archive.sh X.Y` on the tagged commit,
      not from the Release workflow, whose `.ipa` is unsigned; see `docs/ios-release.md`.

## Agent files

- This file is the one set of instructions for every coding agent. Claude Code reads `AGENTS.md`
  itself when there is no `CLAUDE.md`, so do not add one; anything only one agent needs goes in
  that agent's own settings, outside the repository.
- Skills live in `.agents/skills/<name>/`, a `SKILL.md` and whatever it runs, where agents that
  follow the shared layout find them. Claude Code looks only in `.claude/skills/`, so each skill
  has a stub there of the same name and description that sends it on to the real one. A symlink
  would do the same with no stub, but a Windows checkout without `core.symlinks` turns it into a
  text file.
- A new skill is its folder under `.agents/skills/` and its stub under `.claude/skills/`; change a
  skill's description in both.
