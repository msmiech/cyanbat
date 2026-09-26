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
