plugins {
    kotlin("jvm")
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
