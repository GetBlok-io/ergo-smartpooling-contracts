package app.commands

import app.{AppCommand, AppParameters}
import configs.SmartPoolConfig
import configs.node.{SmartPoolNodeConfig, SmartPoolWalletConfig}
import configs.params.{CommandConfig, HoldingConfig, MetadataConfig, SmartPoolParameters, VotingConfig}
import org.ergoplatform.appkit.{NetworkType, RestApiErgoClient}
import org.ergoplatform.explorer.client.ExplorerApiClient

abstract class SmartPoolCmd(config: SmartPoolConfig) {
  val appCommand: AppCommand.Value


  protected val nodeConf: SmartPoolNodeConfig = config.getNode
  protected val walletConf: SmartPoolWalletConfig = nodeConf.getWallet
  protected val paramsConf: SmartPoolParameters = config.getParameters
  protected val metaConf: MetadataConfig = paramsConf.getMetaConf
  protected val cmdConf: CommandConfig = paramsConf.getCommandConf
  protected val holdConf: HoldingConfig = paramsConf.getHoldingConf
  protected val voteConf: VotingConfig = paramsConf.getVotingConf

  //val explorerUrl: String = "https://ergo.watch/tmp-for-getblok/"
  val explorerUrl: String = if(nodeConf.getNetworkType == NetworkType.MAINNET) "https://ergo.watch/tmp-for-getblok/" else RestApiErgoClient.defaultTestnetExplorerUrl
  // Create ErgoClient instance (represents connection to node)
  val ergoClient =  RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)


  AppParameters.networkType = nodeConf.getNetworkType



  def initiateCommand: Unit

  def executeCommand: Unit

  def recordToDb: Unit


}

