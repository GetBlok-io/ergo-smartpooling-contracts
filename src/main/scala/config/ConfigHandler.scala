package config

import com.google.gson.GsonBuilder
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.config.ErgoNodeConfig

import java.io.FileWriter

object ConfigHandler {
  final val defaultURL = "http://127.0.0.1:9052/"
  final val defaultConfigName = "sp_config.json"


  def newConfig: SmartPoolConfig = {
    val params = new SmartPoolParameters("", "", "", Array(""))
    params.setMetadataId("")
    params.setCommandId("")
    params.setHoldingIds(Array(""))

    val api = new SmartPoolAPIConfig(defaultURL, "")
    val wallet = new SmartPoolWalletConfig("wallet_mneumonic", "wallet_password")
    val node = new SmartPoolNodeConfig(api, wallet, NetworkType.TESTNET)
    val persistence = new PersistenceConfig("127.0.0.1", 9000, "db_name", false)
    val config = new SmartPoolConfig(node, params, persistence)

    config
  }

  def writeConfig(fileName: String, conf: SmartPoolConfig): SmartPoolConfig = {
    val gson = new GsonBuilder().setPrettyPrinting().create()
    val fileWriter = new FileWriter(fileName)
    gson.toJson(conf, fileWriter)
    fileWriter.close()
    conf
  }
}