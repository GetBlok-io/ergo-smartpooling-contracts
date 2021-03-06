package app.commands

import app.{AppCommand, AppParameters}
import config.SmartPoolConfig
import config.node.{SmartPoolNodeConfig, SmartPoolWalletConfig}
import config.params.{CommandConfig, HoldingConfig, MetadataConfig, SmartPoolParameters}
import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.explorer.client.ExplorerApiClient

abstract class SmartPoolCmd(config: SmartPoolConfig) {
  val appCommand: AppCommand.Value


  protected val nodeConf: SmartPoolNodeConfig = config.getNode
  protected val walletConf: SmartPoolWalletConfig = nodeConf.getWallet
  protected val paramsConf: SmartPoolParameters = config.getParameters
  protected val metaConf: MetadataConfig = paramsConf.getMetaConf
  protected val cmdConf: CommandConfig = paramsConf.getCommandConf
  protected val holdConf: HoldingConfig = paramsConf.getHoldingConf

  val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(nodeConf.getNetworkType)

  // Create ErgoClient instance (represents connection to node)
  val ergoClient =  RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)


  AppParameters.networkType = nodeConf.getNetworkType



  def initiateCommand: Unit

  def executeCommand: Unit

  def recordToDb: Unit


}

