---
name: run-cyanbat
description: Build, install, launch, drive and screenshot the CyanBat game - on an Android emulator, or as the Compose Desktop build with no emulator at all. Use when asked to run, start, launch, play, or screenshot the app, to verify a gameplay or rendering change on a real device, or to test pause/resume and highscore persistence. Covers adb, uiautomator, and the emulator.
---

# Running CyanBat

CyanBat is a landscape game sharing one Kotlin Multiplatform codebase between Android and
desktop. On Android a Compose menu (`MainActivity`) launches a custom-framebuffer game activity
(`CyanBatGameActivity`); on desktop a single Compose window swaps between the two.

Unit tests cover the ECS, maths and spawn pacing, but nothing covers rendering, input or the
activity lifecycle - for those, run it.

Everything is driven by `.claude/skills/run-cyanbat/driver.sh`. All paths below are relative to
the repo root; run the driver from there.

## Prerequisites

Android SDK with `platform-tools` and `emulator`, plus one AVD, for the Android commands.
`uv` is used for the HUD crop (it resolves Pillow itself); without it `hud` still takes the
screenshot and just skips the crop. The `desktop` command needs neither.

The driver finds
the SDK via `$ANDROID_HOME`, `$ANDROID_SDK_ROOT`, or `%LOCALAPPDATA%\Android\Sdk`,
and picks the first AVD from `emulator -list-avds` (override with `$CYANBAT_AVD`).

Verified on Windows 11 + Git Bash against `Medium_Phone` (API 37, 1080x2400). Nothing in the
driver is Windows-specific.
JDK 21 comes from the Gradle toolchain; no separate install needed.

```bash
emulator -list-avds
```

## Run (agent path)

One command does the whole loop — boot, install, launch, screenshot, pause/resume,
read persisted state, check for crashes:

```bash
.claude/skills/run-cyanbat/driver.sh smoke
```

It exits non-zero on a crash or a failed launch, and drops artifacts in
`.artifacts/run-cyanbat/` (gitignored). Expect ~30s warm; a cold boot added ~20s here.

Individual commands, for iterating:

```bash
.claude/skills/run-cyanbat/driver.sh boot          # start emulator, wait for boot
.claude/skills/run-cyanbat/driver.sh install       # ./gradlew :app:installDebug
.claude/skills/run-cyanbat/driver.sh start         # menu -> tap "Start Game" -> in game
.claude/skills/run-cyanbat/driver.sh shot g1       # full screenshot -> .artifacts/run-cyanbat/g1.png
.claude/skills/run-cyanbat/driver.sh hud g1        # screenshot + cropped, readable HUD
.claude/skills/run-cyanbat/driver.sh play 45       # drive the bat with swipes for 45s
.claude/skills/run-cyanbat/driver.sh pause-resume  # real onPause/onResume, HUD before+after
.claude/skills/run-cyanbat/driver.sh highscore     # decode the persisted DataStore value
.claude/skills/run-cyanbat/driver.sh focus         # which activity is foreground
.claude/skills/run-cyanbat/driver.sh logs          # crash buffer + runtime errors
.claude/skills/run-cyanbat/driver.sh stop
.claude/skills/run-cyanbat/driver.sh desktop     # Compose Desktop build - no emulator
```

**Always look at the screenshots.** `shot` only asserts the PNG is non-trivial in
size; it cannot tell gameplay from a black frame.

Use `hud` rather than `shot` whenever you need to read the score, highscore, or
lives: the game renders into a 480x320 framebuffer that is stretched to the full
window, so HUD text is blurry and small in a full-size capture.

`tap` finds nodes by label through the accessibility tree, so it survives a
different screen size — never hardcode coordinates:

```bash
.claude/skills/run-cyanbat/driver.sh tap "Settings"
```

## Run (human path)

Open in Android Studio and hit Run, or:

```bash
./gradlew :app:installDebug
adb shell am start -n at.smiech.cyanbat/.MainActivity
```

Then tap Start Game on the emulator window. Useless without a display.

## Build / check

```bash
./gradlew build
```

Assembles debug + release and runs lint. There are **no unit or instrumentation
tests in this repo** (no `test/` or `androidTest/` source set in either module),
so a green build says nothing about behavior. Verify on the emulator.

## Gotchas

- **`CyanBatGameActivity` is not exported.** `am start -n at.smiech.cyanbat/.activity.CyanBatGameActivity`
  fails with `SecurityException: Permission Denial ... not exported from uid`.
  Tapping "Start Game" on the menu is the only way in — which is why `start`
  goes through `uiautomator`.

- **`monkey -c android.intent.category.LAUNCHER` destroys the game activity.**
  The usual "bring the app back to front" trick delivers a launcher intent, which
  resets the task to `MainActivity` and finishes the game activity. It looks like
  the app crashed. `pause-resume` covers the game with the Settings window and
  presses BACK instead, which keeps the same window ID — check that the driver
  prints `same window`, otherwise you measured an activity recreation, not a resume.

- **`adb shell cat` corrupts binary output.** It rewrites every `0x0a` as `0d 0a`,
  so the DataStore protobuf decodes to a wrong number (a stored 1350 reads back as
  1734). Use `adb exec-out` for anything binary, screenshots included.

- **The game comes back from a pause still paused.** `onPause` sets the run's own
  pause flag, and `onResume` deliberately does not clear it, so `pause-resume`
  leaves `pr_after` showing the PAUSED overlay with the score frozen where it was.
  That is correct, not a hang: tap the screen (or press BACK twice to quit) to get
  moving again. Score and lives do not advance across the pause any more.

- **An unattended bat dies within seconds.** It holds position with no finger on
  it and takes hits standing still, scoring a few hundred before game over.
  Capture what you need immediately after `start`, or use `play`.

- **The bat is dragged, so `input swipe` places it precisely.** It ends the swipe
  centred on the release point when the swipe started away from it, or offset by
  wherever on the sprite it was grabbed. A press and hold (`input swipe x y x y
  1200`) also works: one `TOUCH_DOWN` is enough, and the bat flies over to it.
  That makes screenshots of a chosen position repeatable - see
  `PlayerInputSystem`. Screen pixels map to the 480x320 framebuffer at
  x/5 and y*320/1080.

- **`play` finishes runs, it does not survive them.** Once the bat dies, the next
  swipe's `TOUCH_UP` dismisses `GameOverScreen` back to the menu. That is the way
  to exercise the highscore write path, not a way to reach late-game state.

- **The highscore persists on death, or on quitting from the pause screen.**
  `saveHighscore()` runs when the bat loses its last life and when BACK leaves a
  paused run, so `highscore` reads stale until one of those happens.

- **BACK no longer finishes the game activity; it pauses.** A second BACK on the
  paused screen quits to the menu. So `input keyevent KEYCODE_BACK` once looks
  like nothing happened to `focus` — assert on a screenshot. `adb shell input
  keyevent 111` (ESCAPE) toggles pause too, and `input keyevent 51/29/47/32`
  (W/A/S/D) steers the bat, which is a far cheaper way to move it than swiping.

- **Screenshots are 2400x1080** (landscape) even though `adb shell wm size` reports
  `1080x2400`. The app is locked to landscape; `uiautomator` bounds are already in
  the rotated frame, so they match `input tap` directly.

- **Menu navigation does not change the foreground activity.** Settings and Credits
  are Navigation3 destinations inside `MainActivity`, so `focus` still reports
  `MainActivity` after `tap "Settings"`. Only `Start Game` crosses an activity
  boundary. Assert on a screenshot, not on `focus`, for in-menu navigation.

## Troubleshooting

| Symptom                                         | Cause / fix                                                                                          |
|-------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `no UI node with text "Start Game"`             | Not on the menu. Run `menu` first, or check `focus`.                                                 |
| Successive screenshots are byte-identical       | The game is not rendering — usually the app fell back to `MainActivity`. Check `focus`.              |
| `pause-resume` prints `WARNING: window changed` | Something reset the task (see the `monkey` gotcha). The resume was not measured.                     |
| `highscore` says "no datastore file yet"        | No run has ended. `play 45`, then re-check.                                                          |
| `no AVD found`                                  | `emulator -list-avds` is empty; create one in Android Studio or set `$CYANBAT_AVD`.                  |
| Driver hangs at boot                            | Boot took ~20s here but varies by host. Emulator output is in `.artifacts/run-cyanbat/emulator.log`. |
