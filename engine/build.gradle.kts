import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm()

    android {
        namespace = "at.smiech.engine"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    /*
     * There is no iOS app yet. The targets are here so that shared code has to keep working on
     * Kotlin/Native. With them, commonMain is compiled as common code on every host, so a
     * JVM-only API there fails `build` anywhere. Compiling for iOS itself, and running the tests
     * on the simulator, is left to a Mac - CI's ios job; gradle.properties says why.
     */
    iosArm64()
    iosSimulatorArm64()

    /*
     * The browser, through Kotlin/Wasm, for the web app in :web. The shared tests are not run here:
     * in a browser they would need one installed, Chrome by default, and Compose's Skia runtime
     * bundled into them, and on Node.js that runtime does not load at all. The JVM and iOS already
     * run them, and compiling for the browser already holds commonMain to what Kotlin/Wasm
     * supports. The root build file turns off what they would be built with.
     */
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask { enabled = false }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // Compose Multiplatform, not androidx: these resolve to androidx.compose on the
                // Android target and to the Skiko-backed artifacts on the JVM and iOS targets.
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        jvmMain {
            dependencies {
                // Service providers that teach javax.sound.sampled to decode MP3. Pure Java, no
                // natives, so the same jars work on all three desktop platforms.
                implementation(libs.mp3spi)
                implementation(libs.jlayer)
                implementation(libs.tritonus.share)
            }
        }
        jvmTest {
            dependencies {
                // Skia's native library for the machine the tests run on, which the renderer's
                // tests draw with. The engine itself needs only Compose's API; the desktop app
                // brings the natives it runs with.
                implementation(compose.desktop.currentOs)
            }
        }
        wasmJsMain {
            // Declaring the Web Audio API's types, and calling into JavaScript, is experimental in
            // Kotlin/Wasm.
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
            dependencies {
                // The page's fetch and Web Audio, and coroutines to wait on the promises they return.
                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.ktx)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.tracing)
            }
        }
    }
}
