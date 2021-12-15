package test


import boxes.{MetadataInputBox, MetadataOutBox}
import contracts._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import contracts.MetadataContract
import org.ergoplatform.appkit.config.{ApiConfig, ErgoNodeConfig, ErgoToolConfig}
import registers.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
import test.TestCommands.{createCustomCommandBox, createDefaultCommandBox, createModifiedCommandBox, distributionTx, getCurrentCommand, getCurrentMetadata, initialMetadataTx, miningRewardsToHolding, settingsTx, viewCommandInfo, viewMetadataInfo}
import test.TestParameters.{creationMetadataID, currentCommandID, currentMetadataID}

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


  def main(args: Array[String]): Unit = {
    println("SmartPool Test 1: Creation and Normal Operation")

    val conf  = ErgoToolConfig.load("smartpoolconfig.json")

    val nodeConf: ErgoNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(NetworkType.TESTNET)

    // Create ErgoClient instance (represents connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

    // Execute transaction
    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
      TestParameters.initializeParameters(ctx)
//      viewMetadataInfo(ctx)
      //viewCommandInfo(ctx)
//
      var metadataBox = getCurrentMetadata(ctx)
//      var commandBox = getCurrentCommand(ctx)
      //var metadataBox = initialMetadataTx(ctx)
      //metadataBox.toString
      //var commandBox = createDefaultCommandBox(ctx, metadataBox)

      //metadataBox = settingsTx(ctx, metadataBox, commandBox)
      //miningRewardsToHolding(ctx)
      var commandBox = createCustomCommandBox(ctx, metadataBox)
     metadataBox = distributionTx(ctx, metadataBox, commandBox)
      println(
        s"""Current Metadata: ${metadataBox.getId}
           |Current Command: ${commandBox.getId}
           |""".stripMargin)
      metadataBox.getId.toString
    })

  }



}