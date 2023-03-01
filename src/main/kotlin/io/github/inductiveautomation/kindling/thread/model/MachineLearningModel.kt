package io.github.paulgriffith.kindling.thread.model

import io.github.paulgriffith.kindling.core.Kindling
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.io.path.bufferedWriter
import kotlin.io.path.name

object MachineLearningModel {
    private var needsUpdate = true
    var enabled = false
    private const val pmmlFileNamePrefix = "thread_machine_learning_"
    private const val supportAppsGatewayEndpoint = "https://iazendesk.inductiveautomation.com/system/webdev/ThreadCSVImportTool/LogisticRegressionThread.pmml"
    private const val versionEndpoint = "https://iazendesk.inductiveautomation.com/system/webdev/ThreadCSVImportTool/validate_pmml_version"
    private const val kindlingDownloadUrl = "https://iazendesk.inductiveautomation.com/data/perspective/client/zendesk_display"

    val pmmlFilePath: String
        get() {
            val folder = if (cacheFilePath.toFile().exists()) {
                cacheFilePath
            } else {
                Paths.get("src/main/resources")
            }

            return folder.toFile().listFiles().findLast { file ->
                file.isFile && file.name.contains(pmmlFileNamePrefix)
            }!!.absolutePath
        }

    private val cacheFilePath = run {
        val userHome = System.getProperty("user.home")
        val pmmlRelativePath = ".kindling/machine-learning-data/"
        Paths.get(userHome).resolve(pmmlRelativePath)
    }

    private val currentPMMLVersion by lazy {
        val client = HttpClient()
        runBlocking {
            val response: HttpResponse = client.request(versionEndpoint) {
                method = HttpMethod.Get
                url {
                    parameters.append("version", Kindling.VERSION)
                }
            }
            response.bodyAsText()
        }
    }

    private val oldPMMLVersion = run {
        var folder = cacheFilePath.toFile()
        if (!folder.exists()) { // If no cache file already, get bundled version
            folder = File("src/main/resources")
        }
        val lastFileName = folder.listFiles().findLast {file ->
            file.isFile && file.name.contains(pmmlFileNamePrefix)
        }?.toString()
        lastFileName?.substringBeforeLast(".pmml")?.substringAfterLast("_") ?: ""
    }

    fun verifyPMML() {
        if (!enabled) return
        if (oldPMMLVersion != currentPMMLVersion && needsUpdate) {
            when (currentPMMLVersion) { // Add future cases here if we want to send special messages for older versions (i.e. bug warnings post-release)
                "" -> {
                    val title = "New Version Available!"
                    val msg = "There is a new version of Kindling Beta available. Would you like to upgrade?"
                    if (pmmlPopup(title, msg, arrayOf("No", "Upgrade Now!")) == 1) {
                        val desk = Desktop.getDesktop()
                        desk.browse(URI(kindlingDownloadUrl))
                    }
                }
                else -> {
                    val title = "New Machine Learning Model Available!"
                    val msg = "There is a newer version of the Machine Learning Model available. Would you like to update?"
                    if (pmmlPopup(title, msg, arrayOf("No", "Update Now!")) == 1) {
                        updatePMML()
                    }
                }
            }
            needsUpdate = false
        }
    }

    private fun removeOldPMMLVersions() {
        val folder = cacheFilePath.toFile()
        val listOfFiles = folder.listFiles()
        if (listOfFiles != null && currentPMMLVersion != "") {
            val filteredList = listOfFiles.filter { file ->
                file.isFile && pmmlFileNamePrefix in file.name
            }
            filteredList.forEach { file ->
                if (file.name.substringBeforeLast(".pmml").substringAfterLast("_") != currentPMMLVersion) {
                    try {
                        Files.delete(file.toPath())
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun updatePMML() {
        if (cacheFilePath.name.isNotEmpty()){
            val client = HttpClient()
            runBlocking {
                val response: HttpResponse = client.request(supportAppsGatewayEndpoint) {
                    method = HttpMethod.Get
                }
                withContext(Dispatchers.IO) {
                    Files.createDirectories(cacheFilePath)
                }
                val filename = "$pmmlFileNamePrefix$currentPMMLVersion.pmml"
                cacheFilePath.resolve(filename).bufferedWriter().use { out ->
                    out.write(response.bodyAsText())
                }
                removeOldPMMLVersions()
            }
        }
    }

    private fun pmmlPopup(title: String, message: String, options: Array<String>): Int {
        return JOptionPane.showOptionDialog(
            JFrame(),
            message,
            title,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            ImageIcon(Kindling.frameIcon),
            options,
            options[1]
        )
    }
}