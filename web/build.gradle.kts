import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
}

/*
 * What the page serves beside itself: the game's asset files under assets/, the same files the
 * Android app packages and the desktop carries on its classpath, and the icons the desktop's window
 * uses, for the browser's tab and a phone's home screen.
 *
 * The assets come with an index of every file, assets/index.txt, which the page reads to know what
 * to fetch before a run; see WebAssets.
 */
val webAssets = layout.buildDirectory.dir("webAssets")
val stageWebAssets = tasks.register<Sync>("stageWebAssets") {
    val assets = rootProject.file("assets")
    from(assets) { into("assets") }
    from(rootProject.file("desktop/src/main/resources/icons")) { into("icons") }
    into(webAssets)
    val index = webAssets.map { it.file("assets/index.txt").asFile }
    doLast {
        index.get().writeText(
            assets.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(assets).invariantSeparatorsPath }
                .sorted()
                .joinToString("\n", postfix = "\n")
        )
    }
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "cyanbat"
        browser {
            commonWebpackConfig {
                outputFileName = "cyanbat.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain {
            // Calling into the page's JavaScript is experimental in Kotlin/Wasm.
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
            resources.srcDir(stageWebAssets)
            dependencies {
                implementation(project(":game"))
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
