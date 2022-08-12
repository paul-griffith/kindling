import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE

@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    // see gradle/libs.version.toml
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
}

allprojects {
    group = "io.github.paulgriffith.kindling"
}

subprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-releases/")
        }
        maven {
            url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-thirdparty/")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
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
