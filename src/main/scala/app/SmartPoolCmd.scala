package app

import config.node.{SmartPoolNodeConfig, SmartPoolWalletConfig}

import java.sql._
import config.SmartPoolConfig
import config.params.{CommandConfig, HoldingConfig, MetadataConfig, SmartPoolParameters}
import contracts.holding.HoldingContract
import org.ergoplatform.appkit.{ErgoClient, NetworkType, RestApiErgoClient}

abstract class SmartPoolCmd(config: SmartPoolConfig) {
  val appCommand: AppCommand.Value

  // TODO: Add helper functions to get current metadata and command, and select type of command and holding boxes

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

  def recordToConfig: Unit


}

