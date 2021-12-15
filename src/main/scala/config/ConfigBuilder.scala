package config

import com.google.gson.GsonBuilder
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.config.ErgoNodeConfig

import java.io.FileWriter

object ConfigBuilder {
  final val defaultURL = "http://213.239.193.208:9053/"
  final val defaultWalletSignerName = "ENTER WALLET/SIGNER NAME HERE"
  final val defaultConfigName = "subpool_config.json"


//  def newCustomConfig(walletName: String, params:SubPoolParameters): SmartPoolConfig = {
//    val api = new SubPoolApiConfig(defaultURL, "")
//    val wallet = new SubPoolWalletConfig(walletName)
//    val node = new SubPoolNodeConfig(api, wallet, NetworkType.MAINNET)
//    val config = new SmartPoolConfig(node, params)
//    config
//  }
//
//  def writeConfig(fileName: String, conf: SmartPoolConfig): SmartPoolConfig = {
//    val gson = new GsonBuilder().setPrettyPrinting().create()
//    val fileWriter = new FileWriter(fileName)
//    gson.toJson(conf, fileWriter)
//    fileWriter.close()
//    conf
//  }
}