package app.api

import app.AppParameters
import configs.SmartPoolConfig
import configs.node.{SmartPoolNodeConfig, SmartPoolWalletConfig}
import configs.params.{CommandConfig, HoldingConfig, MetadataConfig, SmartPoolParameters, VotingConfig}
import org.ergoplatform.appkit.{ErgoClient, RestApiErgoClient}

abstract class ApiCommand(config: SmartPoolConfig, args: Array[String]) {
  final val ARG_START_DELIMITER = "#"
  final val ARG_END_DELIMITER = "#"
  final val OUTPUT_SEPARATOR = "|^^^^^^|"

  protected val nodeConf: SmartPoolNodeConfig = config.getNode
  protected val walletConf: SmartPoolWalletConfig = nodeConf.getWallet
  protected val paramsConf: SmartPoolParameters = config.getParameters
  protected val metaConf: MetadataConfig = paramsConf.getMetaConf
  protected val cmdConf: CommandConfig = paramsConf.getCommandConf
  protected val holdConf: HoldingConfig = paramsConf.getHoldingConf
  protected val voteConf: VotingConfig = paramsConf.getVotingConf

  protected var outputStrings = Array.empty[String]

  val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(nodeConf.getNetworkType)

  // Create ErgoClient instance (represents connection to node)
  val ergoClient: ErgoClient =  RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

  def printOutputs(): Unit = {
    val outputsDelimited = outputStrings.map(o => OUTPUT_SEPARATOR ++ o)
    outputsDelimited.foreach(print)
  }

  def execute: ApiCommand
}
