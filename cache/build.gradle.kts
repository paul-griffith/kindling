plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}
