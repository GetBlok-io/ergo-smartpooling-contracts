package test

import boxes.builders.{CommandOutputBuilder, MetadataOutputBuilder}
import boxes.{CommandInputBox, MetadataInputBox, MetadataOutBox}
import contracts.command.PKContract
import contracts.holding.SimpleHoldingContract
import contracts.{MetadataContract, generateContractAddress}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoContract, ErgoId, ErgoProver, ErgoToken, InputBox, NetworkType, OutBox, Parameters, SecretString, SignedTransaction, UnsignedTransaction, UnsignedTransactionBuilder}
import registers.{MemberList, MetadataRegisters, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
import test.TestParameters.{commandValue, creationMetadataID, currentCommandID, currentMetadataID, holdingAddress, holdingContract, initValue, metadataContract, networkType, poolMiner, poolMinerTwo, poolOpProver, poolOperator, rewardsAddress, rewardsProver, rewardsValue}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}



object TestParameters {

  final val initValue = 1 * Parameters.OneErg
  final val commandValue = 3 * Parameters.MinFee
  final val rewardsValue = 3 * Parameters.OneErg
  final val networkType = NetworkType.TESTNET
  val poolMiner = Address.create("3WwtfPaghPbuPtYQs4Uj9QooYsPYkEZsESjmcR49MB4fs5kEshX7")
  val poolMinerTwo = Address.create("3WzKopFYhfRGPaUvC7v49DWgeY1efaCD3YpNQ6FZGr2t5mBhWjmw")
  var creationMetadataID = ErgoId.create("1f378e1d42ee5dd2ff48f79614160993a5cebf124548f38196763584d14fbd0b")

  val currentMetadataID = "f4495ebfc9a84c2db6cc77d1bd8f92e9dd1b6ea8bea578191e63ed3acd0be86a"
  var currentCommandID = "9c65dedcb0d03fd158f1d05f1a89a232343df81ff9794f56dd18396ba8b4179c"

  val poolOpSecret = SecretString.create("A test seed for smart pool operator")
  val rewardsSecret = SecretString.create("A test seed for mining rewards to be sent from")


  var metadataContract: ErgoContract = _
  var holdingContract: ErgoContract = _
  var metadataAddress: Address = _
  var holdingAddress: Address = _
  var rewardsAddress: Address = _
  var rewardsProver: ErgoProver = _
  var poolOperator: Address = _
  var poolOpProver: ErgoProver = _

  def initializeParameters(ctx: BlockchainContext): Unit = {
    metadataContract = MetadataContract.generateMetadataContract(ctx)
    metadataAddress = generateContractAddress(metadataContract, networkType)

    holdingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataAddress, creationMetadataID)
    holdingAddress = generateContractAddress(holdingContract, networkType)

    poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, poolOpSecret, SecretString.empty())
    poolOpProver = ctx.newProverBuilder().withMnemonic(poolOpSecret, SecretString.empty()).withEip3Secret(0).build();

    rewardsAddress = Address.createEip3Address(0, NetworkType.TESTNET, rewardsSecret, SecretString.empty())
    rewardsProver = ctx.newProverBuilder().withMnemonic(rewardsSecret, SecretString.empty()).withEip3Secret(0).build();


  }
}

object TestCommands {

  def miningRewardsToHolding(ctx: BlockchainContext): String = {

    println(s"Sending ${rewardsValue} ERG to holding address ${holdingAddress.toString}\n")
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    // Create output holding box
    val outB = txB.outBoxBuilder()
    val holdingBox = outB
      .contract(holdingContract)
      .value(rewardsValue)
      .build()
    val rewardBoxes = ctx.getCoveringBoxesFor(rewardsAddress, rewardsValue + (Parameters.MinFee*2), List[ErgoToken]().toList.asJava).getBoxes

    // Create unsigned transaction
    val tx: UnsignedTransaction = txB
      .boxesToSpend(rewardBoxes)
      .outputs(holdingBox)
      .fee(Parameters.MinFee * 2)
      .sendChangeTo(rewardsAddress.getErgoAddress)
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
  def initialMetadataTx(ctx: BlockchainContext) = {

    println(s"Pool Operator Address: ${poolOperator}")

    println(s"Sending ${initValue} ERG to create new Metadata Box\n")
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val mOB: MetadataOutputBuilder = new MetadataOutputBuilder(txB.outBoxBuilder())
    // Create output holding box
    val genesisBox: OutBox = MetadataContract.buildGenesisBox(mOB, metadataContract, poolOperator, initValue, ctx.getHeight)

    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, initValue + (Parameters.MinFee*2), List[ErgoToken]().toList.asJava).getBoxes
    println("Generating unspent boxes for pool creator " + poolOperator)
    println("Current input size: "+ poolOperatorBoxes.size())

    // Create unsigned transaction
    val tx: UnsignedTransaction = txB
      .boxesToSpend(poolOperatorBoxes)
      .outputs(genesisBox)
      .fee(Parameters.MinFee * 2)
      .sendChangeTo(poolOperator.getErgoAddress)
      .build()
    println("Initial Metadata Tx Built\n")
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')
    val smartPoolId = signed.getOutputsToSpend.get(0).getId
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Metadata Id: ${signed.getOutputsToSpend.get(0).getId}")
    creationMetadataID = signed.getOutputsToSpend.get(0).getId

    new MetadataInputBox(genesisBox.convertToInputWith(txId, 0),smartPoolId)
  }


  /**
   * Pool operator sends commandValue ERG to himself to create a valid command box
   * @param ctx Blockchain context
   * @return Id of command box
   */
  def createDefaultCommandBox(ctx: BlockchainContext, metadataBox: MetadataInputBox) = {

    val commandContract = new PKContract(poolOperator)

    println(s"Pool Operator Address: ${poolOperator}")
    println(s"Sending ${commandValue} ERG to create new Command Box under Pool Operator address\n")

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val cOB = new CommandOutputBuilder(txB.outBoxBuilder())
    CommandContract.initializeOutputBuilder(cOB, metadataBox, ctx.getHeight, commandContract, commandValue)
    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, commandValue + (Parameters.MinFee*2), List[ErgoToken]().toList.asJava)
    val commandBox = cOB.build()
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
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Command Id: ${signed.getOutputsToSpend.get(0).getId}")

    new CommandInputBox(commandBox.convertToInputWith(txId, 0), commandContract)
  }

  def createModifiedCommandBox(ctx: BlockchainContext, metadataBox: MetadataInputBox) = {

    val commandContract = new PKContract(poolOperator)
    val holdingBoxes = ctx.getCoveringBoxesFor(holdingAddress, rewardsValue, List[ErgoToken]().toList.asJava).getBoxes

    println(s"Pool Operator Address: ${poolOperator}")
    println(s"Sending ${commandValue} ERG to create new Command Box under Pool Operator address\n")

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val cOB = new CommandOutputBuilder(txB.outBoxBuilder())
    val outB = txB.outBoxBuilder()

    CommandContract.initializeOutputBuilder(cOB, metadataBox, ctx.getHeight, commandContract, commandValue)

    val simpleHoldingContract = new SimpleHoldingContract(holdingContract)

    simpleHoldingContract.holdingBoxes = holdingBoxes.asScala.toList
    simpleHoldingContract.metadataBox = metadataBox
    simpleHoldingContract.applyToCommand(cOB)

    val commandBox = cOB.build()

    println("holding box size" + holdingBoxes.size())

    println("New Command Box created: ")
    println(commandBox)

    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, commandValue + (Parameters.MinFee * 2), List[ErgoToken]().toList.asJava)

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
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Command Id: ${signed.getOutputsToSpend.get(0).getId}")

    new CommandInputBox(commandBox.convertToInputWith(txId, 0), commandContract)
  }


  /**
   * Pool operator signs distribution tx using given command box and metadata box ids.
   * @param ctx Blockchain context
   * @param metadataBoxId Id of metadata box
   * @param commandBoxId Id of command box
   * @return Id of new metadata box
   */
  def settingsTx(ctx: BlockchainContext, metadataBox: MetadataInputBox, commandBox: CommandInputBox) = {
    val p2PKCommandContract = new PKContract(poolOperator)
    val inputBoxes = List(metadataBox.asInput, commandBox.asInput)

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outB = txB.outBoxBuilder()
    val mOB = new MetadataOutputBuilder(outB)

    val metadataOutBox = MetadataContract.buildFromCommandBox(mOB, commandBox, metadataContract, initValue, creationMetadataID)
    println("Next Metadata Output Box constructed: ")
    println(metadataOutBox)
    val tx = txB
      .boxesToSpend(inputBoxes.asJava)
      .outputs(metadataOutBox.asOutBox)
      .fee(Parameters.MinFee * 3)
      .sendChangeTo(poolOperator.getErgoAddress)
      .build()
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"New Metadata Id: ${signed.getOutputsToSpend.get(0).getId}")
    new MetadataInputBox(metadataOutBox.convertToInputWith(txId, 0), metadataOutBox.getSmartPoolId)

  }


  /**
   * Pool operator signs distribution tx using given command box and metadata box ids. Spends holding box and performs
   * normal distribution tx
   * @param ctx Blockchain context
   * @param creatorSecret creator SecretString
   * @param metadataBoxId Id of metadata box
   * @param commandBoxId Id of command box
   * @return Id of new metadata box
   */
  def distributionTx(ctx: BlockchainContext, metadataBox: MetadataInputBox, commandBox: CommandInputBox) = {
    val p2PKCommandContract = new PKContract(poolOperator)
    val initBoxes = List(metadataBox.asInput, commandBox.asInput)


    val holdingBoxes = ctx.getCoveringBoxesFor(holdingAddress, rewardsValue, List[ErgoToken]().toList.asJava).getBoxes
    println("Current holding address: " + holdingAddress + " with " + holdingBoxes.size() + " boxes")
    val inputBoxes = initBoxes++holdingBoxes.asScala
    println(holdingBoxes.size())
    holdingBoxes.asScala.toArray.foreach((x: InputBox) => println(x.toJson(true)))
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outB = txB.outBoxBuilder()
    val mOB = new MetadataOutputBuilder(outB)
    val metadataOutBox = MetadataContract.buildFromCommandBox(mOB, commandBox, metadataContract, initValue, creationMetadataID)
    println("Next Metadata Output Box constructed: ")
    println(metadataOutBox)

    val holdingOutputs = SimpleHoldingContract.generateOutputBoxes(ctx, inputBoxes.toArray, Array(poolOperator), holdingAddress, creationMetadataID, p2PKCommandContract)
    val txFee = commandValue + (commandBox.getShareConsensus.nValue.size * Parameters.MinFee)
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
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"New Metadata Id: ${signed.getOutputsToSpend.get(0).getId}")
    new MetadataInputBox(metadataOutBox.convertToInputWith(txId, 0), metadataOutBox.getSmartPoolId)

  }

  def viewMetadataInfo(ctx: BlockchainContext): String = {
    val inputBox = ctx.getBoxesById(currentMetadataID).head
    val metadataBox = new MetadataInputBox(inputBox, creationMetadataID)
    println(metadataBox)
    metadataBox.getId.toString
  }

  def viewCommandInfo(ctx: BlockchainContext): String = {
    val inputBox = ctx.getBoxesById(currentCommandID).head
    val commandBox = new CommandInputBox(inputBox, new PKContract(poolOperator))
    println(commandBox)
    commandBox.getId.toString
  }

  def getCurrentMetadata(ctx: BlockchainContext) = {
    val inputBox = ctx.getBoxesById(currentMetadataID).head
    val metadataBox = new MetadataInputBox(inputBox, creationMetadataID)
    metadataBox
  }

  def getCurrentCommand(ctx: BlockchainContext) = {
    val inputBox = ctx.getBoxesById(currentCommandID).head
    val commandBox = new CommandInputBox(inputBox, new PKContract(poolOperator))
    commandBox
  }

  def createCustomCommandBox(ctx: BlockchainContext, metadataBox: MetadataInputBox) = {

    val commandContract = new PKContract(poolOperator)
    val holdingBoxes = ctx.getCoveringBoxesFor(holdingAddress, rewardsValue, List[ErgoToken]().toList.asJava).getBoxes

    println(s"Pool Operator Address: ${poolOperator}")
    println(s"Sending ${commandValue} ERG to create new Command Box under Pool Operator address\n")

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val cOB = new CommandOutputBuilder(txB.outBoxBuilder())
    val outB = txB.outBoxBuilder()

    CommandContract.initializeOutputBuilder(cOB, metadataBox, ctx.getHeight, commandContract, commandValue)
    val shareCons = ShareConsensus.fromConversionValues(Array(
      (poolMiner.getErgoAddress.script.bytes, Array(240L, Parameters.OneErg*2, 0)),
      (rewardsAddress.getErgoAddress.script.bytes, Array(140L, Parameters.OneErg/8, 0)),
      (poolMinerTwo.getErgoAddress.script.bytes, Array(110L, Parameters.OneErg/2, 0)),
    ))
    val memsList = MemberList.fromConversionValues(Array(
      (poolMiner.getErgoAddress.script.bytes, poolMiner.toString),
      (rewardsAddress.getErgoAddress.script.bytes, rewardsAddress.toString),
      (poolMinerTwo.getErgoAddress.script.bytes, poolMinerTwo.toString)

    ))
    cOB.setMetadata(new MetadataRegisters(shareCons, memsList, cOB.metadataRegisters.poolFees, cOB.metadataRegisters.poolInfo, cOB.metadataRegisters.poolOps))
    val simpleHoldingContract = new SimpleHoldingContract(holdingContract)

    simpleHoldingContract.holdingBoxes = holdingBoxes.asScala.toList
    simpleHoldingContract.metadataBox = metadataBox
    simpleHoldingContract.applyToCommand(cOB)

    val commandBox = cOB.build()

    println("holding box size" + holdingBoxes.size())

    println("New Command Box created: ")
    println(commandBox)

    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, commandValue + (Parameters.MinFee * 2), List[ErgoToken]().toList.asJava)

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
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Command Id: ${signed.getOutputsToSpend.get(0).getId}")

    new CommandInputBox(commandBox.convertToInputWith(txId, 0), commandContract)
  }




}
