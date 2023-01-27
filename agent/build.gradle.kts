plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-releases/")
    }
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-thirdparty/")
    }
}

dependencies {
    compileOnly(libs.bundles.ignition)
}

kotlin {
    jvmToolchain(11)
}

tasks {
    jar {
        manifest {
            attributes(
                mapOf(
                    "Agent-Class" to "io.github.paulgriffith.kindling.Agent",
                ),
            )
        }
    }
}
