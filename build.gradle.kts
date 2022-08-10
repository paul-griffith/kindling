import org.gradle.internal.os.OperatingSystem
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
    alias(libs.plugins.runtime)
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
    implementation(libs.excelkt)
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

runtime {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))

    modules.set(
        listOf(
            "java.desktop",
            "java.sql",
            "java.logging",
            "java.naming",
            "java.xml"
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
        appVersion = System.getenv("TAG_VERSION") ?: "1.0.0"
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

        // add-exports is used to bypass Java modular restrictions
        jvmArgs = listOf("--add-exports", "java.desktop/com.sun.java.swing.plaf.windows=ALL-UNNAMED")

        installerOptions = options.flatMap { (key, value) ->
            listOfNotNull("--$key", value)
        }

        mainJar = "kindling-bundle.jar"
    }
}
