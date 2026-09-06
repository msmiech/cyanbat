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
