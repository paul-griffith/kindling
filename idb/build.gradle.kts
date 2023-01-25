plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core)
    implementation(projects.log)
    implementation(libs.jfreechart)
}

kotlin {
    jvmToolchain(libs.versions.java.map(String::toInt).get())
}
