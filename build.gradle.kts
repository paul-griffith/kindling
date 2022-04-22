import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE

@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    application
    // see gradle/libs.version.toml
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.shadow)
}

group = "io.github.paulgriffith"

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-releases/")
    }
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-thirdparty/")
    }
}

dependencies {
    // see gradle/libs.version.toml
    implementation(libs.serialization.json)
    implementation(libs.xerial.jdbc)
    implementation(libs.hsql)
    implementation(libs.zip4j)
    implementation(libs.miglayout)
    implementation(libs.jide.common)
    implementation(libs.swingx)
    implementation(libs.logback)
    implementation(libs.svgSalamander)
    implementation(libs.osthemedetector)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.flatlaf)
    implementation(libs.bundles.ignition) {
        // Exclude transitive IA dependencies - we only need core Ignition classes for cache deserialization
        isTransitive = false
    }
    runtimeOnly(libs.bundles.ia.transitive)

    testImplementation(libs.bundles.kotest)
}

application {
    mainClass.set("io.github.paulgriffith.MainPanel")
}

tasks {
    test {
        useJUnitPlatform()
    }
    shadowJar {
        manifest {
            attributes["Main-Class"] = "io.github.paulgriffith.MainPanel"
        }
        archiveBaseName.set("kindling-bundle")
        archiveClassifier.set("")
        archiveVersion.set("")
        mergeServiceFiles()
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = libs.versions.java.get()
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }
    val cleanupJDeploy by registering(Delete::class) {
        delete("jdeploy", "jdeploy-bundle")
    }
    clean {
        finalizedBy(cleanupJDeploy)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

ktlint {
    reporters {
        reporter(CHECKSTYLE)
    }
}
