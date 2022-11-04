plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core)
    implementation(projects.log)
    implementation("org.jfree:jfreechart:1.5.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}
