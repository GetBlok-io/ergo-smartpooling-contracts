package test


import boxes.{MetadataInputBox, MetadataOutBox}
import org.slf4j.LoggerFactory
import contracts._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.{BlockchainContextBuilderImpl, BlockchainContextImpl, ErgoTreeContract, PreHeaderBuilderImpl, PreHeaderImpl}
import contracts.MetadataContract
import logging.LoggingHandler
import org.bouncycastle.math.ec.custom.sec.SecP256K1Point
import org.ergoplatform.{ErgoAddressEncoder, P2PKAddress}
import org.ergoplatform.appkit.config.{ApiConfig, ErgoNodeConfig, ErgoToolConfig}
import org.slf4j.Logger
import registers.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.SigmaDsl
import sigmastate.serialization.{GroupElementSerializer, ProveDlogSerializer}
import special.sigma.GroupElement
import test.TestCommands.{createCustomCommandBox, createDefaultCommandBox, createModifiedCommandBox, distributionTx, getCurrentCommand, getCurrentMetadata, initialMetadataTx, miningRewardsToHolding, regroupHolding, settingsTx, viewCommandInfo, viewMetadataInfo}
import test.TestParameters.{creationMetadataID, currentCommandID, currentMetadataID, nodeGE, poolOperator}

import scala.collection.JavaConverters._

object SmartPool_Test {
  /*
  * This file represents a test case for the smart pooling contract.
  * A rewards transaction is simulated that sends mining rewards to the smart pool.
  * First, the smart pool operator creates a metadata box.
  * The smart pool operator then creates a command box to use in the consensus Tx.
  * When the first reward is sent, the command and metadata box will be consumed
  * To create new command and metadata boxes.
   */

  // Initial metadata value
  implicit val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_TEST)
  //LoggingHandler.initializeLogger(logger)

  def main(args: Array[String]): Unit = {
    logger.info("Starting Smart Pool Test...")

    val conf  = ErgoToolConfig.load("test_config.json")

    val nodeConf: ErgoNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(NetworkType.TESTNET)

    // Create ErgoClient instance (represents connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

    // Execute transaction
    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
      TestParameters.initializeParameters(ctx)
      viewMetadataInfo(ctx)
//      ctx.newTxBuilder()
//      ctx.createPreHeader().
      //viewCommandInfo(ctx)

      // TODO Try preheader tests out for proof of vote
      //

      //var metadataBox = getCurrentMetadata(ctx)
      //var commandBox = getCurrentCommand(ctx)
      //var metadataBox = initialMetadataTx(ctx)
      //metadataBox.toString
      //var commandBox = createModifiedCommandBox(ctx, metadataBox)

      //metadataBox = settingsTx(ctx, metadataBox, commandBox, preHeader)
      //miningRewardsToHolding(ctx)
      //regroupHolding(ctx)
      //var commandBox = createCustomCommandBox(ctx, metadataBox)
      //metadataBox = distributionTx(ctx, metadataBox, commandBox)
//      println(
//        s"""Current Metadata: ${metadataBox.getId}
//           |Current Command: ${commandBox.getId}
//           |""".stripMargin)
      //metadataBox.getId.toString
      "hello"
    })
    logger.info("Test completed successfully")
  }



}