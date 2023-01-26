import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE

@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ktlint)
    `maven-publish`
}

repositories {
    mavenCentral()
}

allprojects {
    group = "io.github.paulgriffith.kindling"

    tasks {
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = libs.versions.java.get()
            }
        }
    }
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
    val cleanupJDeploy by registering(Delete::class) {
        delete("jdeploy", "jdeploy-bundle")
    }
    clean {
        finalizedBy(cleanupJDeploy)
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
