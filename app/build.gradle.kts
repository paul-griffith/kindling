import org.gradle.internal.os.OperatingSystem
import java.time.LocalDate

@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    application
    kotlin("jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.runtime)
}

repositories {
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.idb)
    implementation(projects.log)
    implementation(projects.thread)
    implementation(projects.cache)
    implementation(projects.backup)
    implementation(libs.osthemedetector)
}

application {
    mainClass.set("io.github.paulgriffith.kindling.MainPanel")
}

tasks {
    shadowJar {
        manifest {
            attributes["Main-Class"] = "io.github.paulgriffith.kindling.MainPanel"
        }
        archiveBaseName.set("kindling-bundle")
        archiveClassifier.set("")
        archiveVersion.set("")
        mergeServiceFiles()
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
