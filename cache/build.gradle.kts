plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core)
}

kotlin {
    jvmToolchain(libs.versions.java.map(String::toInt).get())
}
