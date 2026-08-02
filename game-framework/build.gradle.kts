plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "at.grueneis.game.framework"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.compose.material3)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.ktx)
            }
        }
    }
}
