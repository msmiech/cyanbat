# CyanBat

CyanBat is a side-scrolling 2D action game, inspired by retro classics like *Gradius* and modern
arcade staples like *Flappy Bird*. It runs on **Android** and on the **desktop** (Windows, macOS
and Linux) from a single shared codebase.

## 🚀 Architecture

- **Kotlin Multiplatform**: the engine and the whole game — rules, screens and menu UI — are
  common code. The platform modules only supply platform pieces: a framebuffer, input, audio,
  haptics and persistence.
- **Compose Multiplatform**: one set of menu, settings and credits screens renders on both
  Android and desktop, from shared string and drawable resources.
- **Entity Component System**: `:engine`'s ECS decouples game logic from data, so behaviour is
  composed from components rather than an inheritance hierarchy.
- **Value class optimisation**: `Vector2` is a bit-packed value class, so movement maths
  allocates nothing in the game loop.
- **Sub-pixel precision**: geometry is float-based, and the frame loop clamps its delta so a
  resume cannot fast-forward the simulation.

## 📁 Project structure

| Module | What it is |
| --- | --- |
| `:engine` | Platform-agnostic engine: `Game`/`Screen`/`Graphics`/`Audio` interfaces, the ECS (`at.smiech.engine.ecs`), maths (`at.smiech.engine.math`), and the shared `GameLoop`. `androidMain` and `jvmMain` hold the platform implementations. |
| `:game` | CyanBat itself: game screens, entity factory, spawners, and the shared Compose UI. Android + JVM. |
| `:app` | Android application — activities, DataStore, and Android asset wiring. |
| `:desktop` | Compose Desktop application — window, JVM asset wiring, and preferences-backed storage. |

Game assets live once at the repo root in `assets/`, packaged as Android assets by `:app` and as
classpath resources by `:desktop`.

## ▶️ Running it

Desktop, no emulator needed:

```bash
./gradlew :desktop:run
```

Android:

```bash
./gradlew :app:installDebug
```

There is also a `/run-cyanbat` skill that drives the Android build end to end on an emulator —
build, install, launch, screenshot, and check persistence. See
[.claude/skills/run-cyanbat/SKILL.md](.claude/skills/run-cyanbat/SKILL.md).

## 🧪 Building and testing

```bash
./gradlew build
```

Assembles every module, runs lint, and runs the unit tests — ECS, maths, spawn pacing, and a
check that the MP3 service provider desktop audio depends on is actually present.

## 📦 Cutting a release

Every artifact takes its version from `cyanbat.version` in `gradle.properties`. The release
workflow overrides it with the tag being built, so tagging is what sets the version — the property
is only what an untagged build stamps.

Pushing a version tag builds and publishes everything:

```bash
git tag 2.0 && git push origin 2.0
```

That produces a signed Android APK plus Windows, macOS and Linux desktop installers, and attaches
them to a GitHub release. Tags work with or without a leading `v`; a suffixed tag such as `2.1-rc1`
publishes as a pre-release. To rehearse without spending a tag, run the **Release** workflow
manually from the Actions tab — it builds and uploads the same artifacts to the run, and publishes
nothing.

### Signing secrets

The APK is signed with a keystore supplied through repository secrets, so nothing sensitive lives
in the repository.

2.0 is signed with a **new** keystore. The one the 1.x releases used is gone, and Android identifies
an app by its signature, so 2.0 is a fresh install rather than an update — anything still running a
1.x build has to be uninstalled first. That break is a one-off; from 2.0 onwards the same keystore
has to keep being used, because losing it again would force the same break on whoever is running
2.x by then.

Create it once, and back it up somewhere you will still have in a few years:

```bash
keytool -genkeypair -v -keystore cyanbat-release.jks -storetype PKCS12 \
  -alias cyanbat -keyalg RSA -keysize 4096 -validity 10000
```

Then add four repository secrets, under **Settings → Secrets and variables → Actions**:

| Secret | What it is |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias of the signing key inside the keystore — `cyanbat` above |
| `ANDROID_KEY_PASSWORD` | Password for that key |

Encode the keystore with:

```bash
base64 -w0 cyanbat-release.jks
```

Without these the release job fails rather than publishing an APK nobody can install. Local builds
and pull requests need no keystore at all — `assembleRelease` simply produces an unsigned APK, as
it always has.

Desktop installers are unsigned, so Windows SmartScreen and macOS Gatekeeper will warn on first
run. Fixing that needs a paid code-signing certificate and an Apple developer account.

## 🛠 Tech stack

- **Language**: Kotlin 2.x, Kotlin Multiplatform
- **UI**: Compose Multiplatform
- **Persistence**: Jetpack DataStore (Android), `java.util.prefs` (desktop)
- **Desktop audio**: `javax.sound.sampled` plus the mp3spi/jlayer service providers
- **CI/CD**: GitHub Actions

## 📜 History

This project originated as an academic project in 2012, based on the principles from *Beginning
Android Games* by Mario Zechner and Robert Green. The original framework was provided by DI Robert
Grüneis. In 2026 it was refactored from legacy OOP to a data-driven ECS architecture, and then
from an Android-only app to Kotlin Multiplatform with a desktop target.

---
*Developed with ❤️ using Kotlin.*
