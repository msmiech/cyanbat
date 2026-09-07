import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

sourceSets {
    // The same asset files the Android app packages; here they ride along as classpath resources.
    main { resources.srcDir(rootProject.file("assets")) }
}

dependencies {
    implementation(project(":game"))
    testImplementation(libs.kotlin.test.junit)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    // Provides Dispatchers.Main on the JVM, backed by the Swing event queue.
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "at.smiech.cyanbat.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CyanBat"
            // jpackage insists on major.minor.patch; see the root build.gradle.kts.
            packageVersion = rootProject.extra["cyanbatPackageVersion"] as String
        }
    }
}
