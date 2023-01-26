enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "kindling"

include(
    "core",
    "app",
)
