plugins {
    kotlin("jvm")
    alias(libs.plugins.serialization)
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.bundles.kotest)
}
