plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core)
    implementation(projects.idb)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}
