package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.Companion.XML_FACTORY
import io.github.inductiveautomation.kindling.statistics.StatisticCategory
import org.w3c.dom.NodeList
import java.util.Properties
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class MetaStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "Meta"

    val uuid by statistic { gwbk ->
        val query = gwbk.configIDB.prepareStatement("SELECT SYSTEMUID FROM SYSPROPS")
        query.executeQuery().getString(0)
    }

    val gatewayName by statistic { gwbk ->
        val query = gwbk.configIDB.prepareStatement("SELECT SYSEMNAME FROM SYSPROPS")
        query.executeQuery().getString(0)
    }

    val gwbkSize by statistic { it.size }

    val redundancyRole by statistic { gwbk ->
        gwbk.redundancyInfo.use {
            val document = XML_FACTORY.newDocumentBuilder().parse(it).apply {
                normalizeDocument()
            }
            val xpath = XPathFactory.newInstance().newXPath()
            val expr = xpath.compile("//entry[@key=\"redundancy.noderole\"]")
            val results = expr.evaluate(document, XPathConstants.NODESET) as NodeList
            results.item(0).textContent
        }
    }

    val version by statistic { gwbk ->
        gwbk.backupInfo.use {
            val document = XML_FACTORY.newDocumentBuilder().parse(it).apply {
                normalizeDocument()
            }
            document.getElementsByTagName("version").item(0).textContent
        }
    }

    val maxMemory by statistic { gwbk ->
        val ignitionConf = Properties().apply {
            gwbk.ignitionConf.use(this::load)
        }
        ignitionConf.getProperty("wrapper.java.maxmemory").toInt()
    }
}
