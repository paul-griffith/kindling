enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "kindling"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.5.0")
}
