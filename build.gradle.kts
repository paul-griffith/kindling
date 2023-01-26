import org.gradle.internal.os.OperatingSystem
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE
import java.time.LocalDate

@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ktlint)
    application
    alias(libs.plugins.shadow)
    alias(libs.plugins.runtime)
    `maven-publish`
}

apply {
    plugin<DownloadJavadocsPlugin>()
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-releases/")
    }
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-thirdparty/")
    }
    maven {
        url = uri("https://jitpack.io")
        content {
            includeGroup("com.github.Dansoftowner")
        }
    }
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
    api(libs.excelkt)
    api(libs.jfreechart)
    api(libs.rsyntaxtextarea)
    implementation(libs.osthemedetector)
    runtimeOnly(libs.bundles.ia.transitive)

    testImplementation(libs.bundles.kotest)
}

group = "io.github.paulgriffith.kindling"

application {
    mainClass.set("io.github.paulgriffith.kindling.MainPanel")
}

tasks {
    test {
        useJUnitPlatform()
    }

    val cleanupJDeploy by registering(Delete::class) {
        delete("jdeploy", "jdeploy-bundle")
    }
    clean {
        finalizedBy(cleanupJDeploy)
    }

    shadowJar {
        manifest {
            attributes["Main-Class"] = "io.github.paulgriffith.kindling.MainPanel"
        }
        archiveBaseName.set("kindling-bundle")
        archiveClassifier.set("")
        archiveVersion.set("")
        mergeServiceFiles()
    }

    register("printVersion") {
        doLast { // add a task action
            println(project.version)
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.java.map(String::toInt).get())
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
            "java.xml",
            "jdk.zipfs",
        ),
    )

    jpackage {
        val currentOs = OperatingSystem.current()
        val imgType = if (currentOs.isWindows) "ico" else "png"
        appVersion = System.getenv("TAG_VERSION") ?: "1.0.0"
        imageOptions = listOf("--icon", "src/main/resources/icons/ignition.$imgType")
        @OptIn(ExperimentalStdlibApi::class)
        val options: Map<String, String?> = buildMap {
            put("resource-dir", "src/main/resources")
            put("vendor", "Paul Griffith")
            put("copyright", LocalDate.now().year.toString())
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

        imageName = "kindling"
        installerName = "kindling"
        mainJar = "kindling-bundle.jar"
    }
}

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
            version = rootProject.version.toString()
        }
    }
}
