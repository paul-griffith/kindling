plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core)
    implementation(projects.log)
    implementation(libs.jfreechart)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}
