plugins {
    kotlin("jvm")
    `maven-publish`
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

kotlin {
    jvmToolchain(libs.versions.java.map(String::toInt).get())
}

apply(plugin = "maven-publish")
configure<PublishingExtension> {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/paul-griffith/kindling")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}
