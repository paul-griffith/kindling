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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}
