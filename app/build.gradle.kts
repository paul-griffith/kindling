plugins {
    application
    kotlin("jvm")
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
