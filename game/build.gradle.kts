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
        namespace = "at.smiech.cyanbat.game"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    sourceSets {
        commonMain {
            dependencies {
                // api, not implementation: consumers build a CyanBatEnvironment out of engine
                // types (Pixmap, Haptics) and so need them on their own compile classpath.
                api(project(":engine"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// Generated resource accessors (Res.string.*, Res.drawable.*) for the shared UI.
compose.resources {
    publicResClass = true
    packageOfResClass = "at.smiech.cyanbat.resources"
}
