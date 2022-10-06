plugins {
    kotlin("jvm")
}

apply {
    plugin<DownloadJavadocsPlugin>()
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
    runtimeOnly(libs.bundles.ia.transitive)
    implementation(libs.excelkt)
    implementation(libs.jsoup)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

// data class JavadocUrl(val base: String, val allClasses: String = "allclasses.html")
//
// val downloadJavadocs = tasks.register<Task>("downloadJavadocs") {
//    val toDownload = mapOf(
//        "8.1" to listOf(
//            JavadocUrl("https://files.inductiveautomation.com/sdk/javadoc/ignition81/8.1.21/"),
//            JavadocUrl("https://docs.oracle.com/en/java/javase/11/docs/api/"),
//        ),
//        "8.0" to listOf(
//            JavadocUrl("https://files.inductiveautomation.com/sdk/javadoc/ignition80/8.0.14/"),
//            JavadocUrl("https://docs.oracle.com/en/java/javase/11/docs/api/"),
//        ),
//        "7.9" to listOf(
//            JavadocUrl("https://files.inductiveautomation.com/sdk/javadoc/ignition79/7921/", "allclasses-noframe.html"),
//            JavadocUrl("https://docs.oracle.com/javase/8/docs/api/", "allclasses-noframe.html"),
//        ),
//    )
// //    val javadocs = mapOf(
// //        "8.1" to "https://files.inductiveautomation.com/sdk/javadoc/ignition81/8.1.21/allclasses.html",
// //        "8.0" to "https://files.inductiveautomation.com/sdk/javadoc/ignition80/8.0.14/allclasses.html",
// //        "7.9" to "https://files.inductiveautomation.com/sdk/javadoc/ignition79/7921/allclasses-noframe.html",
// //    )
//
//    for ((version, javadocs) in toDownload) {
//        for ((base, allClasses) in javadocs) {
//            uri("$base$allClasses").toURL().openStream().use { inputstream ->
//                val ret = XmlParser(false, false, true).parse(inputstream)["a[href]"]
//                logger.error("ret: $ret")
//            }
//        }
//    }
//
//    outputs.dir(temporaryDir)
// }
//
// sourceSets["main"].resources.srcDir(downloadJavadocs)
