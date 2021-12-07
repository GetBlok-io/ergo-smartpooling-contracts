package test


import boxes.{MetadataInputBox, MetadataOutBox}
import contracts._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import contracts.MetadataContract
import contracts.SmartPoolingContract._
import org.ergoplatform.appkit.config.{ApiConfig, ErgoNodeConfig, ErgoToolConfig}
import values.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}

import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`collection asJava`
import scala.collection.mutable.ArrayBuffer

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
  final val initValue = 1 * Parameters.OneErg
  final val commandValue = 3 * Parameters.MinFee
  val poolMiner = Address.create("3WwtfPaghPbuPtYQs4Uj9QooYsPYkEZsESjmcR49MB4fs5kEshX7")
  val creationMetadataID = ErgoId.create("6842f3388a27080a228c4bf70893e68e17f956a9044361f40cbbfd605a645b44")
  val currentMetadataID = "aaf319d5c626860b10bfbdd672f6275e3b4a0170f2cc6ab5a196f0529fae36ce"
  var currentCommandID = "1096c5dc58661a897ac3df01c58815b84cb1913e4399a73e9493a3f32ef29d55"

  /**
   * Creator address sends some ERG to a metadata box while setting it's initial
   * registers, thereby creating a Metadata Box for epoch 0
   * @param ctx Blockchain Context
   * @param creator Creator Address whose proposition bytes shall be stored as a pool operator.
   */
  def initialMetadataTx(ctx: BlockchainContext, creatorSecret: SecretString): String = {
    val poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, creatorSecret, SecretString.empty())
    val poolOpProver = ctx.newProverBuilder().withMnemonic(creatorSecret, SecretString.empty()).withEip3Secret(0).build();
    println(s"Pool Operator Address: ${poolOperator}")

    val metadataContract = MetadataContract.generateMetadataContract(ctx, poolOperator)

    println(s"Sending ${initValue} ERG to create new Metadata Box\n")
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    // Create output holding box
    val metadataBox: MetadataOutBox = MetadataContract.buildInitialMetadata(txB.outBoxBuilder(), metadataContract, poolOperator, initValue, ctx.getHeight)
    println("New Metadata Box created: ")
    println(metadataBox)
    val poolOperatorBoxes = ctx.getUnspentBoxesFor(poolOperator, 0, 1)
    println("Generating unspent boxes for pool creator " + poolOperator)
    println("Current input size: "+ poolOperatorBoxes.size())

    // Create unsigned transaction
    val tx: UnsignedTransaction = txB
      .boxesToSpend(poolOperatorBoxes)
      .outputs(metadataBox.asOutBox)
      .fee(Parameters.MinFee * 2)
      .sendChangeTo(poolOperator.getErgoAddress)
      .build()
    println("Initial Metadata Tx Built\n")
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Metadata Id: ${signed.getOutputsToSpend.get(0).getId}")
    signed.getOutputsToSpend.get(0).getId.toString
  }

  def createCommandBox(ctx: BlockchainContext, creatorSecret: SecretString, metadataBoxID: String) = {
    val metadataBox = new MetadataInputBox(ctx.getBoxesById(metadataBoxID).head)

    val poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, creatorSecret, SecretString.empty())
    val poolOpProver = ctx.newProverBuilder().withMnemonic(creatorSecret, SecretString.empty()).withEip3Secret(0).build();
    val commandContract = new ErgoTreeContract(poolOperator.getErgoAddress.script)

    println(s"Pool Operator Address: ${poolOperator}")
    println(s"Sending ${commandValue} ERG to create new Command Box under Pool Operator address\n")
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outB = txB.outBoxBuilder()
    var commandBox = MetadataContract.buildNextMetadataOutput(metadataBox, outB, commandContract, ctx.getHeight)
    // If epoch is 0, lets make sure to explicitly fill epoch 1 with valid values, since dummy values were used before.
    if(metadataBox.getCurrentEpoch == 0) {
      val newConsensus = ShareConsensus.fromConversionValues(Array((poolMiner.getErgoAddress.script.bytes, 100L)))
      val newMembers = MemberList.fromConversionValues(Array((poolMiner.getErgoAddress.script.bytes, poolMiner.toString.getBytes(StandardCharsets.UTF_8))))
      val newPoolFees = PoolFees.fromConversionValues(Array((poolOperator.getErgoAddress.script.bytes, 1)))
      val newPoolInfo = PoolInfo.fromConversionValues(Array(metadataBox.getCurrentEpoch + 1, ctx.getHeight, metadataBox.getCreationHeight, BigInt(creationMetadataID.getBytes).toLong))
      val newPoolOps = PoolOperators.fromConversionValues(Array((poolOperator.getErgoAddress.script.bytes, poolOperator.toString.getBytes(StandardCharsets.UTF_8))))
      commandBox = MetadataContract.buildMetadataBox(outB, commandContract, commandValue, newConsensus, newMembers, newPoolFees, newPoolInfo , newPoolOps)
    }

    println("New Command Box created: ")
    println(commandBox)

    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, Parameters.MinFee * 3, List[ErgoToken]().toList.asJava)

    // Create unsigned transaction
    val tx: UnsignedTransaction = txB
      .boxesToSpend(poolOperatorBoxes.getBoxes)
      .outputs(commandBox.asOutBox)
      .fee(Parameters.MinFee * 2)
      .sendChangeTo(poolOperator.getErgoAddress)
      .build()
    println("Command Box Creation Tx Built\n")
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Command Id: ${signed.getOutputsToSpend.get(0).getId}")

    signed.getOutputsToSpend.get(0).getId.toString
  }

  def consensusTxWithoutHolding(ctx: BlockchainContext, creatorSecret: SecretString, metadataBoxId: String, commandBoxId: String) = {
    val poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, creatorSecret, SecretString.empty())
    val poolOpProver = ctx.newProverBuilder().withMnemonic(creatorSecret, SecretString.empty()).withEip3Secret(0).build();

    val inputBoxes = ctx.getBoxesById(metadataBoxId, commandBoxId)
    val commandBox = new MetadataInputBox(inputBoxes(1))
    viewMetadataInfo(ctx, commandBox.getId.toString)

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val mtB = new MetadataOutBoxBuilder(txB.outBoxBuilder())
    val outputMetadataBox: OutBox = buildCommandBox(mtB, generateMetadataContract(ctx, poolOperator), initValue,
      commandBox.getLastConsensus, commandBox.getMemberList, commandBox.getPoolFees, commandBox.getPoolInfo, commandBox.getPoolOperators)

    val tx = txB
      .boxesToSpend(inputBoxes.toList.asJava)
      .outputs(outputMetadataBox)
      .fee(Parameters.MinFee * 3)
      .sendChangeTo(poolOperator.getErgoAddress)
      .build()
    println("New Metadata Box Built\n")
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"New Metadata Id: ${signed.getOutputsToSpend.get(0).getId}")
    signed.toJson(true)

  }
  def viewMetadataInfo(ctx: BlockchainContext, boxId: String) = {
    val inputBox = ctx.getBoxesById(boxId).head
    val metadataBox = new MetadataBox(inputBox)
    println(metadataBox)
    println(new ErgoId(BigInt(BigInt(ErgoId.create("6842f3388a27080a228c4bf70893e68e17f956a9044361f40cbbfd605a645b44").getBytes).toLong).toByteArray))
    metadataBox.getId.toString
  }


  def rewardToSmartPoolTx(ctx: BlockchainContext, miningPool: SecretString): Unit = {
  }
  // Send a test voting transaction
  def smartPoolToMinersTx(ctx: BlockchainContext, minerString: SecretString, minerAddress: Address, minerShareArray: Array[Long], minerVoteArray: Array[Int]): Unit = {

  }

  def main(args: Array[String]): Unit = {
    println("SmartPool Test 1: Creation and Normal Operation")
    val poolOpSecret = SecretString.create("A test seed for smart pool operator")
    val conf  = ErgoToolConfig.load("smartpoolconfig.json")

    val nodeConf: ErgoNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(NetworkType.TESTNET)

    // Create ErgoClient instance (represents connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

    // Execute transaction
    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
      viewMetadataInfo(ctx, currentMetadataID)
      //viewMetadataInfo(ctx, currentCommandID)
      //initialMetadataTx(ctx, poolOpSecret)
      //consensusTxWithoutHolding(ctx, poolOpSecret, currentMetadataID, currentCommandID)
      //createCommandBox(ctx, poolOpSecret, currentMetadataID)
    })

  }

}