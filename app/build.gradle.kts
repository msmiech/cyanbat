import javax.inject.Inject
import com.android.build.api.dsl.ApplicationExtension

// Module-level build file with build configurations for the app module
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

extensions.configure<ApplicationExtension> {
    namespace = "at.smiech.cyanbat"

    // Assets live at the repo root so the desktop module can use the same copy.
    sourceSets["main"].assets.srcDir(rootProject.file("assets"))
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "at.smiech.cyanbat"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 16
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    testImplementation(libs.kotlin.test.junit)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3)

    implementation(project(":game"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.compose.tooling.preview)
    debugImplementation(libs.androidx.compose.tooling)
}

/*
 * Compose Multiplatform 1.12 does not wire its Android asset copy for modules using
 * com.android.kotlin.multiplatform.library: it registers
 * :game:copyAndroidMainComposeResourcesToAndroidAssets but never configures that task's
 * outputDirectory, so the shared UI's images and strings never reach the APK and the app dies
 * on the first painterResource with MissingResourceException.
 *
 * Build the layout the resource runtime expects - composeResources/<packageOfResClass>/... -
 * and register it as a generated asset directory.
 */
abstract class StageComposeResources : DefaultTask() {
    @get:InputDirectory abstract val sourceDir: DirectoryProperty

    @get:Input abstract val resourcePackage: Property<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun stage() {
        fs.sync {
            from(sourceDir)
            into(outputDir.dir("composeResources/${resourcePackage.get()}"))
        }
    }
}

val stageSharedComposeResources = tasks.register<StageComposeResources>("stageSharedComposeResources") {
    dependsOn(":game:prepareComposeResourcesTaskForCommonMain")
    sourceDir.set(
        project(":game").layout.buildDirectory
            .dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources")
    )
    resourcePackage.set("at.smiech.cyanbat.resources")
    outputDir.set(layout.buildDirectory.dir("generated/composeResourcesAssets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            stageSharedComposeResources,
            StageComposeResources::outputDir
        )
    }
}
