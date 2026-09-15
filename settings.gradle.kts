pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.5"
    id("dev.kikugie.loom-back-compat") version "0.3"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Phase 1: the whole 1.21 line on Yarn mappings.
        // One node per API generation; each jar covers the listed point releases.
        versions("1.21.1", "1.21.3", "1.21.5", "1.21.8", "1.21.11")
        vcsVersion = "1.21.1"
    }
}

rootProject.name = "burmalda"
