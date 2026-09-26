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

    // The recorder behind the README's gameplay GIFs; see recordGameplay below. Its own source
    // set, so none of it ships in the app, but built on the app's classes and classpath, so it
    // hosts the game exactly as the window does.
    val recorder = create("recorder") {
        compileClasspath += main.get().output
        runtimeClasspath += main.get().output
    }

    // Its tests sit with the app's, which is also what has every build compile it.
    test {
        compileClasspath += recorder.output
        runtimeClasspath += recorder.output
    }
}

configurations["recorderImplementation"].extendsFrom(configurations.implementation.get())
configurations["recorderRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    implementation(project(":game"))
    testImplementation(libs.kotlin.test.junit)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    // Provides Dispatchers.Main on the JVM, backed by the Swing event queue.
    implementation(libs.kotlinx.coroutines.swing)
}

// Flies both stages on an autopilot, headless and faster than real time, and writes a GIF of
// each to docs/gameplay. Arguments pass through: --args="--stages=2 --frames=build/frames".
tasks.register<JavaExec>("recordGameplay") {
    group = "documentation"
    description = "Records the README's gameplay GIFs by flying each stage on an autopilot."
    classpath = sourceSets["recorder"].runtimeClasspath
    mainClass = "at.smiech.cyanbat.desktop.recorder.RecordGameplayKt"
    workingDir = rootDir
    jvmArgs("-Djava.awt.headless=true")
    maxHeapSize = "2g"
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
