import org.gradle.internal.os.OperatingSystem
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE
import java.time.LocalDate

@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.conveyor)
    alias(libs.plugins.dokka)
    application
    alias(libs.plugins.shadow)
    alias(libs.plugins.runtime)
    `maven-publish`
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
    api(libs.serialization.csv)
    api(libs.xerial.jdbc)
    api(libs.hsql)
    api(libs.zip4j)
    api(libs.miglayout)
    api(libs.jide.common)
    api(libs.swingx)
    api(libs.logback)
    api(libs.jsvg)
    api(libs.bundles.coroutines)
    api(libs.bundles.flatlaf)
    api(libs.bundles.ignition) {
        // Exclude transitive IA dependencies - we only need core Ignition classes for cache deserialization
        isTransitive = true
    }
    api(libs.bundles.ktor)
    api(libs.excelkt)
    api(libs.jfreechart)
    api(libs.rsyntaxtextarea)
    api(libs.jpmml)
    runtimeOnly(libs.bundles.ia.transitive)

    testImplementation(libs.bundles.kotest)
}

group = "io.github.inductiveautomation"

application {
    mainClass.set("io.github.inductiveautomation.kindling.MainPanel")
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
            attributes["Main-Class"] = "io.github.inductiveautomation.kindling.MainPanel"
        }
        archiveBaseName.set("kindling-bundle")
        archiveClassifier.set("")
        archiveVersion.set("")
        isZip64 = true
        mergeServiceFiles()
    }

    register("printVersion") {
        doLast { // add a task action
            println(project.version)
        }
    }
}
val downloadJavadocs = tasks.register<DownloadJavadocs>("downloadJavadocs") {
    urlsByVersion.set(
        mapOf(
            "8.1" to listOf(
                "https://files.inductiveautomation.com/sdk/javadoc/ignition81/8.1.32/allclasses.html",
                "https://docs.oracle.com/en/java/javase/17/docs/api/allclasses-index.html",
                "https://www.javadoc.io/static/org.python/jython-standalone/2.7.3/allclasses-noframe.html",
            ),
            "8.0" to listOf(
                "https://files.inductiveautomation.com/sdk/javadoc/ignition80/8.0.14/allclasses.html",
                "https://docs.oracle.com/en/java/javase/11/docs/api/allclasses.html",
                "https://www.javadoc.io/static/org.python/jython-standalone/2.7.1/allclasses-noframe.html",
            ),
            "7.9" to listOf(
                "https://files.inductiveautomation.com/sdk/javadoc/ignition79/7921/allclasses-noframe.html",
                "https://docs.oracle.com/javase/8/docs/api/allclasses-noframe.html",
                "https://www.javadoc.io/static/org.python/jython-standalone/2.5.3/allclasses-noframe.html",
            ),
        ),
    )
    outputDir.set(project.layout.buildDirectory.dir("javadocs"))
}

kotlin {
    jvmToolchain {
        languageVersion.set(libs.versions.java.map(JavaLanguageVersion::of))
        vendor.set(JvmVendorSpec.AMAZON)
    }
    sourceSets {
        main {
            resources.srcDir(downloadJavadocs)
        }
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
            "java.xml",
            "jdk.zipfs",
            "jdk.crypto.ec",
        ),
    )

    jpackage {
        val currentOs = OperatingSystem.current()
        val imgType = if (currentOs.isWindows) "ico" else "png"
        appVersion = project.version.toString()
        imageOptions = listOf("--icon", "src/main/resources/icons/ignition.$imgType")
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

        imageName = "kindling beta"
        installerName = "kindling beta"
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
            from(components["kotlin"])
            pom {
                description.set("Kindling core API and first-party tools, packaged for ease of extension by third parties.")
            }
        }
    }
}
