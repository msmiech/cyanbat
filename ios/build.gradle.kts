plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
}

/*
 * The iPhone and iPad app's Kotlin half: the menu and the runs, hosted in one UIViewController
 * that the Xcode project in iosApp/ puts on screen. Built into a static framework, CyanBatGame,
 * which Xcode links into the app through `embedAndSignAppleFrameworkForXcode`; the Compose plugin
 * copies the shared resources into the app beside it. Named apart from the app, CyanBat, since
 * Swift cannot import a module of its own name.
 *
 * Like the other iOS targets, it is left alone off a Mac (gradle.properties): the framework can
 * only be linked with Xcode.
 */
kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "CyanBatGame"
            isStatic = true
            // The framework is linked into the app, which carries the real bundle id; this one only
            // keeps the linker from guessing one and warning about it.
            binaryOption("bundleId", "at.smiech.cyanbat.CyanBatGame")
        }
    }

    sourceSets {
        iosMain {
            dependencies {
                implementation(project(":game"))
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
