package test


import boxes.{MetadataInputBox, MetadataOutBox, MetadataOutputBuilder}
import contracts._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import contracts.MetadataContract
import org.ergoplatform.appkit.config.{ApiConfig, ErgoNodeConfig, ErgoToolConfig}
import registers.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
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
  final val initValue = 1 * Parameters.OneErg
  final val commandValue = 3 * Parameters.MinFee
  final val rewardsValue = 3 * Parameters.OneErg
  val poolMiner = Address.create("3WwtfPaghPbuPtYQs4Uj9QooYsPYkEZsESjmcR49MB4fs5kEshX7")
  val creationMetadataID = ErgoId.create("8a813c853c118857d7dd6f03fafb50a1549b7451bc7a9494fab61da293039f28")

  val currentMetadataID = "707d4739c8388cb45f0cf5323cf5566cda21172c26f09d6016b6691078c94998"
  var currentCommandID = "7db6868062e196afd13e63298c1665110225870854bce61ae4b3cb1da44e22e7"

  def main(args: Array[String]): Unit = {
    println("SmartPool Test 1: Creation and Normal Operation")
    val poolOpSecret = SecretString.create("A test seed for smart pool operator")
    val rewardsSecret = SecretString.create("A test seed for mining rewards to be sent from")
    val conf  = ErgoToolConfig.load("smartpoolconfig.json")

    val nodeConf: ErgoNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(NetworkType.TESTNET)

    // Create ErgoClient instance (represents connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

    // Execute transaction
    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
      viewMetadataInfo(ctx, currentMetadataID)
      viewMetadataInfo(ctx, currentCommandID)
      //initialMetadataTx(ctx, poolOpSecret)
      //distributionTxWithoutHolding(ctx, poolOpSecret, currentMetadataID, currentCommandID)
      createModifiedCommandBox(ctx, poolOpSecret, currentMetadataID)
      //createDefaultCommandBox(ctx, poolOpSecret, currentMetadataID)
      //miningRewardsToHolding(ctx, rewardsSecret, poolOpSecret, creationMetadataID)
      //distributionTxWithHolding(ctx, poolOpSecret, currentMetadataID, currentCommandID)

    })

  }
  def miningRewardsToHolding(ctx: BlockchainContext, rewardsSecret: SecretString, creatorSecret: SecretString, smartPoolId: ErgoId): String = {
    val rewardsSender = Address.createEip3Address(0, NetworkType.TESTNET, rewardsSecret, SecretString.empty())
    val rewardsProver = ctx.newProverBuilder().withMnemonic(rewardsSecret, SecretString.empty()).withEip3Secret(0).build()
    val poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, creatorSecret, SecretString.empty())
    val metadataContract = MetadataContract.generateMetadataContract(ctx)
    val metadataAddress = generateContractAddress(metadataContract, NetworkType.TESTNET)
    val holdingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataAddress, smartPoolId)
    val holdingAddress = generateContractAddress(holdingContract, NetworkType.TESTNET)
    println(s"Sending ${rewardsValue} ERG to holding address ${holdingAddress.toString}\n")
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    // Create output holding box
    val outB = txB.outBoxBuilder()
    val holdingBox = outB
      .contract(holdingContract)
      .value(rewardsValue)
      .build()
    val rewardBoxes = ctx.getUnspentBoxesFor(rewardsSender, 0, 20)

    // Create unsigned transaction
    val tx: UnsignedTransaction = txB
      .boxesToSpend(rewardBoxes)
      .outputs(holdingBox)
      .fee(Parameters.MinFee * 2)
      .sendChangeTo(rewardsSender.getErgoAddress)
      .build()
    println("Rewards Tx Built\n")
    val signed: SignedTransaction = rewardsProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Holding Id: ${signed.getOutputsToSpend.get(0).getId}")
    signed.getOutputsToSpend.get(0).getId.toString
  }
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

    val metadataContract = MetadataContract.generateMetadataContract(ctx)

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

  //def miningRewardsToHoldingTx()

  /**
   * Pool operator sends commandValue ERG to himself to create a valid command box
   * @param ctx Blockchain context
   * @param creatorSecret creator secretstring
   * @param metadataBoxID Id of metadata box to build command box from
   * @return Id of command box
   */
  def createDefaultCommandBox(ctx: BlockchainContext, creatorSecret: SecretString, metadataBoxID: String): String = {
    val metadataBox = new MetadataInputBox(ctx.getBoxesById(metadataBoxID).head, creationMetadataID)

    val poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, creatorSecret, SecretString.empty())
    val poolOpProver = ctx.newProverBuilder().withMnemonic(creatorSecret, SecretString.empty()).withEip3Secret(0).build();
    val commandContract = new ErgoTreeContract(poolOperator.getErgoAddress.script)

    println(s"Pool Operator Address: ${poolOperator}")
    println(s"Sending ${commandValue} ERG to create new Command Box under Pool Operator address\n")
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outB = txB.outBoxBuilder()

    // If epoch is 0, lets make sure to explicitly fill epoch 1 with valid registers, since dummy registers were used before.
    val commandBox =
    if(metadataBox.getCurrentEpoch == 0) {
      val newConsensus = ShareConsensus.fromConversionValues(Array((poolMiner.getErgoAddress.script.bytes, Array(100L, (1*Parameters.OneErg), 0L))))
      val newMembers = MemberList.fromConversionValues(Array((poolMiner.getErgoAddress.script.bytes, poolMiner.toString)))
      val newPoolFees = PoolFees.fromConversionValues(Array((poolOperator.getErgoAddress.script.bytes, 1)))
      val newPoolInfo = PoolInfo.fromConversionValues(Array(metadataBox.getCurrentEpoch + 1, ctx.getHeight, metadataBox.getCreationHeight, BigInt(creationMetadataID.getBytes).toLong))
      val newPoolOps = PoolOperators.fromConversionValues(Array((poolOperator.getErgoAddress.script.bytes, poolOperator.toString)))
      MetadataContract.buildMetadataBox(outB, commandContract, commandValue, newConsensus, newMembers, newPoolFees, newPoolInfo , newPoolOps, creationMetadataID)
    }else{
      MetadataContract.buildNextCommandOutput(outB, metadataBox, commandContract, commandValue, ctx.getHeight)
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

  def createModifiedCommandBox(ctx: BlockchainContext, creatorSecret: SecretString, metadataBoxID: String) = {
    val metadataBox = new MetadataInputBox(ctx.getBoxesById(metadataBoxID).head, creationMetadataID)

    val poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, creatorSecret, SecretString.empty())
    val poolOpProver = ctx.newProverBuilder().withMnemonic(creatorSecret, SecretString.empty()).withEip3Secret(0).build();
    val commandContract = new ErgoTreeContract(poolOperator.getErgoAddress.script)

    println(s"Pool Operator Address: ${poolOperator}")
    println(s"Sending ${commandValue} ERG to create new Command Box under Pool Operator address\n")
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outB = txB.outBoxBuilder()
    val smartPoolId = creationMetadataID

    val commandBox = MetadataContract.buildNextCommandOutput(outB, metadataBox, commandContract, commandValue, ctx.getHeight)
    val metadataContract = MetadataContract.generateMetadataContract(ctx)
    val metadataAddress = generateContractAddress(metadataContract, NetworkType.TESTNET)
    val holdingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataAddress, smartPoolId)
    val holdingAddress = generateContractAddress(holdingContract, NetworkType.TESTNET)
    val holdingBoxes = ctx.getUnspentBoxesFor(holdingAddress, 0, 100)
    println("holding box size" + holdingBoxes.size())
    val newCommandBox = SimpleHoldingContract.modifyBalances(txB.outBoxBuilder(), commandBox, commandContract, smartPoolId, metadataBox, holdingBoxes.asScala.toList)
    println("New Command Box created: ")
    println(newCommandBox)

    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, Parameters.MinFee * 3, List[ErgoToken]().toList.asJava)

    // Create unsigned transaction
    val tx: UnsignedTransaction = txB
      .boxesToSpend(poolOperatorBoxes.getBoxes)
      .outputs(newCommandBox.asOutBox)
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

  /**
   * Pool operator signs distribution tx using given command box and metadata box ids.
   * @param ctx Blockchain context
   * @param creatorSecret creator SecretString
   * @param metadataBoxId Id of metadata box
   * @param commandBoxId Id of command box
   * @return Id of new metadata box
   */
  def distributionTxWithoutHolding(ctx: BlockchainContext, creatorSecret: SecretString, metadataBoxId: String, commandBoxId: String): String = {
    val poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, creatorSecret, SecretString.empty())
    val poolOpProver = ctx.newProverBuilder().withMnemonic(creatorSecret, SecretString.empty()).withEip3Secret(0).build();

    val inputBoxes = ctx.getBoxesById(metadataBoxId, commandBoxId)
    val commandBox = new MetadataInputBox(inputBoxes(1), creationMetadataID)
    val metadataBox = new MetadataInputBox(inputBoxes(0), creationMetadataID)
    val smartPoolId =
      if(metadataBox.getCurrentEpoch == 0){
        metadataBox.getId
      }else{
        metadataBox.getTokens.get(0).getId
      }
    val metadataContract = MetadataContract.generateMetadataContract(ctx)

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outB = txB.outBoxBuilder()
    val metadataOutBox = MetadataContract.buildNextMetadataOutput(outB, commandBox,metadataContract, initValue, smartPoolId)
    println("Next Metadata Output Box constructed: ")
    println(metadataOutBox)
    val tx = txB
      .boxesToSpend(inputBoxes.toList.asJava)
      .outputs(metadataOutBox.asOutBox)
      .fee(Parameters.MinFee * 3)
      .sendChangeTo(poolOperator.getErgoAddress)
      .build()
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"New Metadata Id: ${signed.getOutputsToSpend.get(0).getId}")
    signed.getOutputsToSpend.get(0).getId.toString

  }

  /**
   * Pool operator signs distribution tx using given command box and metadata box ids. Spends holding box and performs
   * normal distribituon tx
   * @param ctx Blockchain context
   * @param creatorSecret creator SecretString
   * @param metadataBoxId Id of metadata box
   * @param commandBoxId Id of command box
   * @return Id of new metadata box
   */
  def distributionTxWithHolding(ctx: BlockchainContext, creatorSecret: SecretString, metadataBoxId: String, commandBoxId: String): String = {
    val poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, creatorSecret, SecretString.empty())
    val poolOpProver = ctx.newProverBuilder().withMnemonic(creatorSecret, SecretString.empty()).withEip3Secret(0).build();


    val initBoxes = ctx.getBoxesById(metadataBoxId, commandBoxId)
    val metadataBox = new MetadataInputBox(initBoxes(0), creationMetadataID)
    val commandBox = new MetadataInputBox(initBoxes(1), creationMetadataID)

    val smartPoolId =
      if(metadataBox.getCurrentEpoch == 0){
        metadataBox.getId
      }else{
        metadataBox.getTokens.get(0).getId
      }
    println(smartPoolId)
    val metadataContract = MetadataContract.generateMetadataContract(ctx)
    val metadataAddress = generateContractAddress(metadataContract, NetworkType.TESTNET)
    val holdingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataAddress, smartPoolId)
    val holdingAddress = generateContractAddress(holdingContract, NetworkType.TESTNET)

    val holdingBoxes = ctx.getUnspentBoxesFor(holdingAddress, 0, 20)
    println("Current holding address: " + holdingAddress + " with " + holdingBoxes.size() + " boxes")
    val inputBoxes = initBoxes++holdingBoxes.asScala
    println(holdingBoxes.size())
    holdingBoxes.asScala.toArray.foreach((x: InputBox) => println(x.toJson(true)))
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outB = txB.outBoxBuilder()
    val metadataOutBox = MetadataContract.buildNextMetadataOutput(outB, commandBox,metadataContract, initValue, smartPoolId)
    println("Next Metadata Output Box constructed: ")
    println(metadataOutBox)
    val holdingOutputs = SimpleHoldingContract.generateOutputBoxes(ctx, inputBoxes, Array(poolOperator), holdingAddress)
    val txFee = commandValue + (metadataBox.getShareConsensus.nValue.size * Parameters.MinFee)
    val outputBoxes = List(metadataOutBox.asOutBox)++holdingOutputs.toList
    outputBoxes.foreach(x => println(x.getValue))
    val tx = txB
      .boxesToSpend(inputBoxes.toList.asJava)
      .outputs(outputBoxes:_*)
      .fee(txFee)
      .sendChangeTo(poolOperator.getErgoAddress)
      .build()
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"New Metadata Id: ${signed.getOutputsToSpend.get(0).getId}")
    signed.getOutputsToSpend.get(0).getId.toString

  }

  def viewMetadataInfo(ctx: BlockchainContext, boxId: String): String = {
    val inputBox = ctx.getBoxesById(boxId).head
    val metadataBox = new MetadataInputBox(inputBox, creationMetadataID)
    println(metadataBox)
    metadataBox.getId.toString
  }


  def rewardToSmartPoolTx(ctx: BlockchainContext, miningPool: SecretString): Unit = {
  }
  // Send a test voting transaction
  def smartPoolToMinersTx(ctx: BlockchainContext, minerString: SecretString, minerAddress: Address, minerShareArray: Array[Long], minerVoteArray: Array[Int]): Unit = {

  }



}