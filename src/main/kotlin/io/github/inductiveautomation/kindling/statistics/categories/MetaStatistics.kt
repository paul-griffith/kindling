package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.GatewayBackup.Companion.XML_FACTORY
import org.w3c.dom.NodeList
import java.util.Properties
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

@Suppress("unused")
class MetaStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "Meta"

    val uuid by statistic {
        val query = gwbk.configIDB.prepareStatement("SELECT SYSTEMUID FROM SYSPROPS")
        query.executeQuery().getString(1)
    }

    val gatewayName by statistic {
        val query = gwbk.configIDB.prepareStatement("SELECT SYSTEMNAME FROM SYSPROPS")
        query.executeQuery().getString(1)
    }

    val gwbkSize by statistic { gwbk.size }

    val redundancyRole by statistic {
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

    val version by statistic {
        gwbk.backupInfo.use {
            val document = XML_FACTORY.newDocumentBuilder().parse(it).apply {
                normalizeDocument()
            }
            document.getElementsByTagName("version").item(0).textContent
        }
    }

    val maxMemory by statistic {
        val ignitionConf = Properties().apply {
            gwbk.ignitionConf.use(this::load)
        }
        ignitionConf.getProperty("wrapper.java.maxmemory").toInt()
    }
}
