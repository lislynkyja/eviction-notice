pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // https://modmuss50.github.io/mod-publish-plugin/
        id("me.modmuss50.mod-publish-plugin") version "2.2.1"
    }
}

plugins {
    // https://stonecutter.kikugie.dev/
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Plain Loom on 26.x and the remapping Loom on the obfuscated 1.21.x versions.
    id("dev.kikugie.loom-back-compat") version "0.4.3"
}

stonecutter {
    create(rootProject) {
        // One target per jar. The name is the Minecraft version it builds against, the oldest
        // in its range. The range and the jar's label are in stonecutter.properties.toml.
        versions("26.1")
        vcsVersion = "26.1"
    }
}

rootProject.name = "eviction-notice"
