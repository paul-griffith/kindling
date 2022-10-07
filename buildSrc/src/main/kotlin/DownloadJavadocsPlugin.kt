import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.jsoup.Jsoup
import java.net.URI
import java.net.URL

data class JavadocUrl(
    val base: String,
    val noframe: Boolean = false,
) {
    val url: URL
        get() = URI("${base}allclasses${if (noframe) "-noframe" else ""}.html").toURL()
}

private fun javadoc(url: String) = JavadocUrl(url)
private fun legacyJavadoc(url: String) = JavadocUrl(url, noframe = true)

class DownloadJavadocsPlugin : Plugin<Project> {
    private val toDownload = mapOf(
        "8.1" to listOf(
            javadoc("https://files.inductiveautomation.com/sdk/javadoc/ignition81/8.1.21/"),
            javadoc("https://docs.oracle.com/en/java/javase/11/docs/api/"),
        ),
        "8.0" to listOf(
            javadoc("https://files.inductiveautomation.com/sdk/javadoc/ignition80/8.0.14/"),
            javadoc("https://docs.oracle.com/en/java/javase/11/docs/api/"),
        ),
        "7.9" to listOf(
            legacyJavadoc("https://files.inductiveautomation.com/sdk/javadoc/ignition79/7921/"),
            legacyJavadoc("https://docs.oracle.com/javase/8/docs/api/"),
        ),
    )

    override fun apply(target: Project) {
        val downloadJavadocs = target.tasks.register("downloadJavadocs", Task::class.java) {
            val javadocsDir = temporaryDir.resolve("javadocs")

            javadocsDir.resolve("versions.txt").printWriter().use { writer ->
                for (version in toDownload.keys) {
                    writer.println(version)
                }
            }

            for ((version, urls) in toDownload) {
                javadocsDir.resolve(version).apply {
                    mkdirs()
                    resolve("links.properties").printWriter().use { writer ->
                        for (javadocUrl in urls) {
                            val classNamesToUrls = javadocUrl.url.openStream().use { inputstream ->
                                Jsoup.parse(inputstream, Charsets.UTF_8.name(), "")
                                    .select("a[href]")
                                    .associate { a ->
                                        val className = a.text()
                                        val packageName = a.attr("title").substringAfterLast(' ')

                                        "$packageName.$className" to "${javadocUrl.base}${a.attr("href")}"
                                    }
                            }
                            for ((key, value) in classNamesToUrls) {
                                writer.append(key).append('=').append(value).appendLine()
                            }
                        }
                    }
                }
            }

            outputs.dir(temporaryDir)
        }

        target.extensions.getByName<SourceSetContainer>("sourceSets")["main"].resources.srcDir(downloadJavadocs)
    }
}
