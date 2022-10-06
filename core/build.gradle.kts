plugins {
    kotlin("jvm")
}

apply {
    plugin<DownloadJavadocsPlugin>()
}

dependencies {
    // see gradle/libs.version.toml
    api(libs.serialization.json)
    api(libs.xerial.jdbc)
    api(libs.hsql)
    api(libs.zip4j)
    api(libs.miglayout)
    api(libs.jide.common)
    api(libs.swingx)
    api(libs.logback)
    api(libs.svgSalamander)
    api(libs.bundles.coroutines)
    api(libs.bundles.flatlaf)
    api(libs.bundles.ignition) {
        // Exclude transitive IA dependencies - we only need core Ignition classes for cache deserialization
        isTransitive = false
    }
    runtimeOnly(libs.bundles.ia.transitive)
    implementation(libs.excelkt)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}
