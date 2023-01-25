import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE

@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ktlint)
    `maven-publish` apply false
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
}

tasks {
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
