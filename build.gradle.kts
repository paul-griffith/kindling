import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeFirstWord
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE

@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    // see gradle/libs.version.toml
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.runtime)
    alias(libs.plugins.sonarqube)
}

group = "io.github.paulgriffith"
version = "0.0.6"

repositories {
    mavenCentral()
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-releases/")
    }
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-thirdparty/")
    }
    maven { url = uri("https://jitpack.io") }
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
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.flatlaf)
    implementation(libs.bundles.ignition) {
        // Exclude transitive IA dependencies - we only need core Ignition classes for cache deserialization
        isTransitive = false
    }
    implementation(libs.osthemedetector)
    runtimeOnly(libs.bundles.ia.transitive)

    testImplementation(libs.bundles.kotest)
}

// add-exports is used to bypass Java modular restrictions
val defaultJvmArgs = listOf("--add-exports", "java.desktop/com.sun.java.swing.plaf.windows=ALL-UNNAMED")
val className = "io.github.paulgriffith.MainPanel"

tasks {
    test {
        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = libs.versions.java.get()
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.RequiresOptIn"
            )
        }
    }
    jar {
        manifest {
            attributes["Main-Class"] = className
        }
    }
}

application {
    mainClass.set(className)
    applicationName = rootProject.name.capitalizeFirstWord()
    applicationDefaultJvmArgs = defaultJvmArgs
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
        vendor.set(JvmVendorSpec.AZUL)
    }
}

sonarqube {
    properties {
        property("sonar.projectKey", "paul-griffith_kindling")
        property("sonar.organization", "paul-griffith")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

runtime {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))

    modules.set(
        listOf(
            "java.desktop",
            "java.sql",
            "java.logging",
            "java.naming",
            "java.xml",
        )
    )

    jpackage {
        val currentOs = OperatingSystem.current()
        val imgType = when {
            currentOs.isWindows -> "ico"
            currentOs.isMacOsX -> "icns"
            else -> "png"
        }
        // Reverse the package version because MacOS doesn't like leading zeroes
        appVersion = if (currentOs.isMacOsX) version.toString().reversed() else version.toString()
        imageOptions = listOf("--icon", "src/main/resources/icons/ignition.$imgType")
        @OptIn(ExperimentalStdlibApi::class)
        val options: Map<String, String?> = buildMap {
            put("resource-dir", "src/main/resources")
            put("vendor", "Paul Griffith")
            put("copyright", "2022")
            put("description", "A collection of useful tools for troubleshooting Ignition")

            when {
                currentOs.isWindows -> {
                    put("win-per-user-install", null)
                    put("win-dir-chooser", null)
                    put("win-menu", null)
                    put("win-shortcut", null)
                    // random (consistent) UUID makes upgrades smoother
                    put("win-upgrade-uuid", "8e7428c8-bbc6-460a-9995-db6d8b04a690")
                }
                currentOs.isLinux -> {
                    put("linux-shortcut", null)
                }
            }
        }

        jvmArgs = defaultJvmArgs

        installerOptions = options.flatMap { (key, value) ->
            listOfNotNull("--$key", value)
        }
    }
}

ktlint {
    reporters {
        reporter(CHECKSTYLE)
    }
}
