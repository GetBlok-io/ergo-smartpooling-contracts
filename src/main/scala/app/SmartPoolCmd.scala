package app

import java.sql._

import config.{SmartPoolConfig, SmartPoolNodeConfig, SmartPoolWalletConfig}
import org.ergoplatform.appkit.{ErgoClient, NetworkType, RestApiErgoClient}

abstract class SmartPoolCmd(config: SmartPoolConfig) {
  val appCommand: AppCommand.Value

  protected val nodeConf: SmartPoolNodeConfig = config.getNode
  protected val walletConf: SmartPoolWalletConfig = nodeConf.getWallet
  val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(nodeConf.getNetworkType)

  // Create ErgoClient instance (represents connection to node)
  val ergoClient =  new RestApiErgoClient(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

  AppParameters.networkType = nodeConf.getNetworkType


  private val appParameters = config.getParameters


  def initiateCommand: Unit

  def executeCommand: Unit

  def recordToConfig: Unit


}

