plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core)
    implementation(projects.idb)
    implementation(libs.rsyntaxtextarea)
}

kotlin {
    jvmToolchain(libs.versions.java.map(String::toInt).get())
}
