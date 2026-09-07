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

/*
 * Release signing, supplied entirely through the environment so no keystore or password ever
 * reaches the repository. The release workflow writes the keystore out of a GitHub secret and
 * points ANDROID_KEYSTORE_FILE at it.
 *
 * When the variables are absent - every local build, and every pull request, where the secrets are
 * deliberately not exposed - the release build type stays unsigned and still assembles. That keeps
 * `./gradlew build` working for a contributor who has no keystore; the workflow, not this file, is
 * what refuses to publish an unsigned APK.
 */
// Blank counts as absent: GitHub Actions sets an env var to the empty string when the secret
// behind it does not exist, so an unset secret would otherwise read as a real, empty password.
fun signingEnv(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val keystoreFile = signingEnv("ANDROID_KEYSTORE_FILE")?.let(::file)?.takeIf { it.isFile }
val keystorePassword = signingEnv("ANDROID_KEYSTORE_PASSWORD")
val keystoreKeyAlias = signingEnv("ANDROID_KEY_ALIAS")

/*
 * PKCS12 - what keytool produces by default, and what the README tells you to create - has no
 * separate key password at all: keytool refuses to set one ("Different store and key passwords not
 * supported for PKCS12 KeyStores") and the store password is what unlocks the key. So an absent key
 * password means "the same as the store", not "none".
 *
 * It has to be resolved to something. AGP models keyPassword as a Gradle Property, and leaving it
 * unset does not mean empty - reading it later fails the build with MissingValueException, from a
 * task that never mentions passwords.
 */
val keystoreKeyPassword = signingEnv("ANDROID_KEY_PASSWORD") ?: keystorePassword

// Half a configuration is a mistake worth naming, rather than one to discover through whichever
// AGP task reads the property first.
require(keystoreFile == null || (keystorePassword != null && keystoreKeyAlias != null)) {
    "ANDROID_KEYSTORE_FILE is set, so ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_ALIAS must be set too"
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
        // Both derived from cyanbat.version; see the root build.gradle.kts.
        versionCode = rootProject.extra["cyanbatVersionCode"] as Int
        versionName = rootProject.extra["cyanbatVersionName"] as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Null when no keystore was supplied, which leaves the build type unsigned rather than
            // failing - the same behaviour this module had before signing was wired up at all.
            signingConfig = signingConfigs.findByName("release")
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
