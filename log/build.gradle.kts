plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core)
    testImplementation(libs.bundles.kotest)
}
