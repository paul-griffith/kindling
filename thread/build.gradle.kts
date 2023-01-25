@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    kotlin("jvm")
    alias(libs.plugins.serialization)
}

dependencies {
    implementation(projects.core)
    testImplementation(libs.bundles.kotest)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

kotlin {
    jvmToolchain(libs.versions.java.map(String::toInt).get())
}
