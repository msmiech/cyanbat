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
     * Kotlin/Native: every host compiles them, since Kotlin cross-compiles Apple libraries, so
     * `build` fails on a JVM-only API in commonMain. Linking, and running the tests on the
     * simulator, need a Mac - CI's ios job does that part.
     */
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                // Compose Multiplatform, not androidx: these resolve to androidx.compose on the
                // Android target and to the Skiko-backed artifacts on the JVM and iOS targets.
                implementation(compose.runtime)
                implementation(compose.foundation)
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
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.ktx)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}
