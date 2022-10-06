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
    val baseUrl: String,
    val noframe: Boolean = false,
) {
    val url: URL
        get() = URI("${baseUrl}allclasses${if (noframe) "-noframe" else ""}.html").toURL()
}

class DownloadJavadocsPlugin : Plugin<Project> {
    private val toDownload = mapOf(
        "8.1" to listOf(
            JavadocUrl("https://files.inductiveautomation.com/sdk/javadoc/ignition81/8.1.21/"),
            JavadocUrl("https://docs.oracle.com/en/java/javase/11/docs/api/"),
        ),
        "8.0" to listOf(
            JavadocUrl("https://files.inductiveautomation.com/sdk/javadoc/ignition80/8.0.14/"),
            JavadocUrl("https://docs.oracle.com/en/java/javase/11/docs/api/"),
        ),
        "7.9" to listOf(
            JavadocUrl("https://files.inductiveautomation.com/sdk/javadoc/ignition79/7921/", noframe = true),
            JavadocUrl("https://docs.oracle.com/javase/8/docs/api/", noframe = true),
        ),
    )

    override fun apply(target: Project) {
        val downloadJavadocs = target.tasks.register("downloadJavadocs", Task::class.java) {
            val javadocsDir = temporaryDir.resolve("javadocs")
            for ((version, javadocs) in toDownload) {
                javadocsDir.resolve(version).apply {
                    mkdirs()
                    resolve("links.properties").printWriter().use { writer ->
                        for (javadocUrl in javadocs) {
                            val classNamesToUrls = javadocUrl.url.openStream().use { inputstream ->
                                Jsoup.parse(inputstream, Charsets.UTF_8.name(), "")
                                    .select("a[href]")
                                    .associate { a ->
                                        val className = a.text()
                                        val packageName = a.attr("title").substringAfterLast(' ')

                                        "$packageName.$className" to "${javadocUrl.baseUrl}${a.attr("href")}"
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
