plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core)
    implementation(projects.idb)
    implementation(libs.rsyntaxtextarea)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}
