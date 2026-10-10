import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
}

/*
 * One version for every artifact, read from `cyanbat.version` in gradle.properties and overridable
 * with `-Pcyanbat.version=`. The release workflow passes the git tag, so a tagged build stamps the
 * tag it was cut from rather than whatever the file last happened to say - which is how the app
 * drifted to 1.6 while the newest release on GitHub was still 1.2.
 *
 * The three forms below exist because the consumers disagree on what a version looks like: Android
 * wants a display name plus a monotonic integer, and jpackage accepts nothing but `major.minor.patch`.
 */
val declaredVersion = providers.gradleProperty("cyanbat.version").get().removePrefix("v")

// A pre-release suffix (`2.0-rc1`) is part of the display name but not of the numbers.
val versionParts = declaredVersion.substringBefore('-').split('.')
require(versionParts.size in 2..3 && versionParts.all { it.toIntOrNull() != null }) {
    "cyanbat.version must be major.minor or major.minor.patch, optionally with a -suffix, but was '$declaredVersion'"
}
val major = versionParts[0].toInt()
val minor = versionParts[1].toInt()
val patch = versionParts.getOrNull(2)?.toInt() ?: 0

extra["cyanbatVersionName"] = declaredVersion

/*
 * Only has to increase, never to be pretty. The old scheme was major * 10 + minor, which had no
 * room for a patch release at all - 1.2.1 and 1.2.2 would both have landed on 12 - so this widens
 * the digits. 2.0.0 becomes 20000, comfortably above the 16 that 1.6 last used.
 */
extra["cyanbatVersionCode"] = major * 10000 + minor * 100 + patch

// jpackage rejects a suffix, so `2.1-rc1` ships an installer versioned 2.1.0. The display name in
// the release title and on the download page still carries the suffix.
extra["cyanbatPackageVersion"] = "$major.$minor.$patch"

/*
 * Tests run in English whatever the machine's language: the menu's tests find buttons by their
 * English labels, and the run's read English banners. TranslationsTest and GameTextLayoutTest
 * switch the language themselves to check the others.
 *
 * Native access is granted up front because Skia loads its library through System.load, which the
 * JDK otherwise warns about and will eventually refuse.
 */
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("user.language", "en")
        systemProperty("user.country", "US")
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }

    // The shared tests do not run in the browser (see the engine's build file), so nothing is
    // built for them there either. A disabled test task still runs what it depends on, and among
    // that is Compose's check that the tests could load its runtime, which fails the build
    // because a library's tests cannot.
    tasks.named {
        it == "compileTestKotlinWasmJs" ||
            it == "compileTestDevelopmentExecutableKotlinWasmJs" ||
            it == "checkComposeUiTestConfigurationForWasmJs"
    }.configureEach { enabled = false }
}

/*
 * The web build's tools - Node.js, Yarn and Binaryen - come from the repositories that
 * settings.gradle.kts declares for them. Given a download URL, the Kotlin plugin would add a
 * repository of its own to the root project instead, which the build's repository mode refuses.
 */
allprojects {
    plugins.withType<WasmNodeJsRootPlugin> {
        the<WasmNodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<WasmNodeJsPlugin> {
        the<WasmNodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<WasmYarnPlugin> {
        the<WasmYarnRootEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<BinaryenPlugin> {
        the<BinaryenEnvSpec>().downloadBaseUrl.set(null as String?)
    }
}
