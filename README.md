# CyanBat

CyanBat is a high-performance, side-scrolling 2D action game for Android, inspired by retro classics like *Gradius* and modern arcade staples like *Flappy Bird*.

## 🚀 Modern Architecture

The project has been modernized (September 2026) to leverage contemporary Kotlin and game development patterns:

- **Entity Component System (ECS)**: A pure, high-performance ECS implementation located in the `:engine` module. This decouples game logic from data, allowing for highly flexible and scalable entity behavior.
- **Kotlin Multiplatform (KMP)**: The core engine and math utilities are platform-agnostic, prepared for expansion to non-Android targets.
- **Value Class Optimizations**: Movement vectors (`Vector2`) are implemented as bit-packed value classes to eliminate heap allocations in the game loop.
- **Sub-pixel Precision**: Physics and geometry use float-based precision to ensure smooth movement and eliminate jitter.

## 📁 Project Structure

- `:app`: The Android-specific application layer, containing assets, UI screens, and platform-specific services.
- `:engine`: The core game engine.
  - `at.smiech.game.framework.ecs`: The ECS framework (World, Systems, Components).
  - `at.smiech.game.framework.math`: Optimized math utilities (`Rect`, `Vector2`).
  - `at.smiech.game.framework.impl`: Platform implementations and shared utilities.

## 🛠 Tech Stack

- **Language**: Kotlin 2.x
- **UI**: Jetpack Compose (for menus and overlays)
- **Data Persistence**: Jetpack DataStore
- **CI/CD**: GitHub Actions

## 📜 History

This project originated as an academic project in 2012, based on the principles from *Beginning Android Games* by Mario Zechner and Robert Green. The original framework was provided by DI Robert Smiech. In 2026, it was fully refactored to transition from legacy OOP to a data-driven ECS architecture.

---
*Developed with ❤️ using Kotlin.*
