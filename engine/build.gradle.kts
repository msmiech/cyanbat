plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "at.smiech.engine"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // JVM-hosted unit tests for the pure logic in commonMain (ECS, math).
        // No device or emulator needed; wired into `check` via `build`.
        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = false
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.compose.material3)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.ktx)
            }
        }
    }
}
