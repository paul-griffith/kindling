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
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.io.path.bufferedWriter
import kotlin.io.path.name

object MachineLearningModel {

    private var needsUpdate = true
    fun verifyPMML() {
        println(System.getProperty("user.home"))
        if (oldPMMLVersion != currentPMMLVersion && needsUpdate) {
            when (currentPMMLVersion) {
                oldPMMLVersion -> { }
                "" -> if (pmmlPopup("New Version Available!", "There is a new version of Kindling Beta available. Would you like to upgrade?", arrayOf("No", "Upgrade Now!")) == 1) {
                    val desk = Desktop.getDesktop()
                    desk.browse(URI("https://iazendesk.inductiveautomation.com/data/perspective/client/zendesk_display"))
                }
                else -> if (pmmlPopup("New Machine Learning Model Available!", "There is a newer version of the Machine Learning Model available. Would you like to update?", arrayOf("No", "Update Now!")) == 1) {
                    updatePMML()
                }
            }
            needsUpdate = false
        }
    }

    private val cacheFilePath = run {
        val userHome = System.getProperty("user.home")
        val pmmlRelativePath = ".kindling/machine-learning-data/"
        Paths.get(userHome).resolve(pmmlRelativePath)
    }

    private val oldPMMLVersion = run {
        var folder = cacheFilePath.toFile()
        if (!folder.exists()) {
            folder = File("src/main/resources")
        }
        val listOfFiles = folder.listFiles()
        var version = ""
        if (listOfFiles != null) {
            for (i in listOfFiles.indices) {
                if (listOfFiles[i].isFile && listOfFiles[i].name.contains("thread_machine_learning")) {
                    version = listOfFiles[i].toString().substringBeforeLast(".pmml").substringAfterLast("_")
                }
            }
        }
        version
    }

    private fun removeOldPMMLVersions() {
        val folder = cacheFilePath.toFile()
        val listOfFiles = folder.listFiles()
        if (listOfFiles != null && currentPMMLVersion != "") {
            for (i in listOfFiles.indices) {
                if (listOfFiles[i].isFile && listOfFiles[i].name.contains("thread_machine_learning")) {
                    if (listOfFiles[i].toString().substringBeforeLast(".pmml").substringAfterLast("_") != currentPMMLVersion) {
                        val fileName = listOfFiles[i].toString()
                        try {
                            Files.delete(Paths.get(fileName))
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun updatePMML() {
        if (cacheFilePath.name.isNotEmpty()){
            val client = HttpClient()
            runBlocking {
                val response: HttpResponse = client.request("https://iazendesk.inductiveautomation.com/system/webdev/ThreadCSVImportTool/LogisticRegressionThread.pmml") {
                    method = HttpMethod.Get
                }
                withContext(Dispatchers.IO) {
                    Files.createDirectories(cacheFilePath)
                }
                val filename = "thread_machine_learning_$currentPMMLVersion.pmml"
                cacheFilePath.resolve(filename).bufferedWriter().use { out ->
                    out.write(response.bodyAsText())
                }
                removeOldPMMLVersions()
            }
        }
    }

    private val currentPMMLVersion by lazy {
        val client = HttpClient()
        runBlocking {
            val response: HttpResponse = client.request("https://iazendesk.inductiveautomation.com/system/webdev/ThreadCSVImportTool/validate_pmml_version") {
                method = HttpMethod.Get
                url {
                    parameters.append("version", Kindling.VERSION)
                }
            }
            response.bodyAsText()
        }
    }

    private fun pmmlPopup(title: String, message: String, options: Array<String>): Int {
        return JOptionPane.showOptionDialog(JFrame(),
                message,
                title,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1])
    }
}