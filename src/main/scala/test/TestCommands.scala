package test

import boxes.builders.{CommandOutputBuilder, MetadataOutputBuilder}
import boxes.{BoxHelpers, CommandInputBox, MetadataInputBox, MetadataOutBox, RecordingInputBox}
import contracts.command.{CommandContract, MinerPKContract, PKContract}
import contracts.holding.SimpleHoldingContract
import contracts.voting.{ProxyBallotContract, RecordingContract}
import contracts.{MetadataContract, generateContractAddress}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoContract, ErgoId, ErgoProver, ErgoToken, InputBox, NetworkType, OutBox, Parameters, PreHeader, SecretString, SignedTransaction, UnsignedTransaction, UnsignedTransactionBuilder}
import registers.{MemberList, MetadataRegisters, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.SigmaDsl
import sigmastate.serialization.GroupElementSerializer
import test.TestParameters.{commandContract, commandValue, creationMetadataID, currentCommandID, currentMetadataID, holdingAddress, holdingContract, holdingInputs, initValue, metadataAddress, metadataContract, networkType, poolMiner, poolMinerTwo, poolOpProver, poolOperator, rewardsAddress, rewardsProver, rewardsValue}
import transactions.{CreateCommandTx, DistributionTx}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}



object TestParameters {

  final val initValue = 1 * Parameters.OneErg
  final val commandValue = 3 * Parameters.MinFee
  final val rewardsValue = 3 * Parameters.OneErg
  final val networkType = NetworkType.TESTNET
  val poolMiner = Address.create("3WwtfPaghPbuPtYQs4Uj9QooYsPYkEZsESjmcR49MB4fs5kEshX7")
  val poolMinerTwo = Address.create("3WzKopFYhfRGPaUvC7v49DWgeY1efaCD3YpNQ6FZGr2t5mBhWjmw")
  var creationMetadataID = ErgoId.create("02eb540400d441a288b6fce71ed76d171afc06347aeab504312249f07a5406ca")
  var holdingInputs = List("7ad1bf36908fd07eabb00706bada3f6cdb7d15f6d44cc8d32b60ae076c42ee1d", "3e76e81425fe02766da9c10f40aa5db18eaa6110aa4df2202dd91633d4719cb0")

  val currentMetadataID = "81bb1eb163b69d76e586e8f4d3e02e2ba5958fea514abafc69ef9469be4ae0bf"
  var currentCommandID = "49ff9cbe600063534dfe1f8900b344543d44d68852065e34e3b1a164d81450ed"

  val poolOpSecret = SecretString.create("decade replace tired property draft patch innocent regular habit refuse double hard stick where phrase")
  val rewardsSecret = SecretString.create("A test seed for mining rewards to be sent from")


  var metadataContract: ErgoContract = _
  var holdingContract: ErgoContract = _
  var metadataAddress: Address = _
  var holdingAddress: Address = _
  var rewardsAddress: Address = _
  var rewardsProver: ErgoProver = _
  var poolOperator: Address = _
  var poolOpProver: ErgoProver = _
  var commandContract: CommandContract = _

  val nodePubKey = GroupElementSerializer.parse(BigInt("023fcd666a1289acbd365abfdfc893ad273ec1795c7a72b18598996bb20e1096be", 16).toByteArray)
  val nodeGE = SigmaDsl.GroupElement(nodePubKey)


  def initializeParameters(ctx: BlockchainContext): Unit = {
    metadataContract = MetadataContract.generateMetadataContract(ctx)
    metadataAddress = generateContractAddress(metadataContract, networkType)

    holdingContract = SimpleHoldingContract.generateHoldingContract(ctx, metadataAddress, creationMetadataID)
    holdingAddress = generateContractAddress(holdingContract, networkType)

    poolOperator = Address.createEip3Address(0, NetworkType.TESTNET, poolOpSecret, SecretString.empty())
    poolOpProver = ctx.newProverBuilder().withMnemonic(poolOpSecret, SecretString.empty()).withEip3Secret(0).build();

    rewardsAddress = Address.createEip3Address(0, NetworkType.TESTNET, rewardsSecret, SecretString.empty())
    rewardsProver = ctx.newProverBuilder().withMnemonic(rewardsSecret, SecretString.empty()).withEip3Secret(0).build();
    println(holdingAddress)
    commandContract = new PKContract(poolOperator)
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

  def regroupHolding(ctx: BlockchainContext) = {
    val holdingInput = ctx.getBoxesById(holdingInputs: _*)
    val rewardBoxes = ctx.getCoveringBoxesFor(rewardsAddress, Parameters.MinFee*2, List[ErgoToken]().toList.asJava).getBoxes
    val outB = ctx.newTxBuilder().outBoxBuilder()
    val holdingOutput1 = outB
      .contract(holdingContract)
      .value(rewardsValue/3)
      .build()
    val holdingOutput2 = outB
      .contract(holdingContract)
      .value(rewardsValue/3)
      .build()
    val rewardsOutput3 = ctx.newTxBuilder().outBoxBuilder()
      .contract(new ErgoTreeContract(rewardsAddress.getErgoAddress.script))
      .value(rewardsValue/3)
      .build()
    val holdingOutput3 = ctx.newTxBuilder().outBoxBuilder()
      .contract(holdingContract)
      .value(rewardsValue/3)
      .build()

    val txB = ctx.newTxBuilder()


    val unsigned = txB.boxesToSpend((holdingInput.toList++rewardBoxes.asScala).asJava)
      .fee(Parameters.MinFee*2)
      .outputs(holdingOutput1, holdingOutput2, holdingOutput3)
      .sendChangeTo(rewardsAddress.getErgoAddress)
      .build()

    val signed = rewardsProver.sign(unsigned)
    val txId = ctx.sendTransaction(signed)
    println(signed.toJson(true))
    println(txId)

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
    val boxesToSpend = ctx.getCoveringBoxesFor(poolOperator, initValue + (Parameters.MinFee * 11), List[ErgoToken]().asJava).getBoxes
    var txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outB = txB.outBoxBuilder()
    val smartPoolToken = new ErgoToken(boxesToSpend.get(0).getId, 1)
    val tokenBox = outB
      .value(initValue + (Parameters.MinFee * 4))
      .mintToken(smartPoolToken, "SmartPool Test Token", "Test Token For SmartPool", 0)
      .contract(new ErgoTreeContract(poolOperator.getErgoAddress.script))
      .build()

    val tokenTx = txB.boxesToSpend(boxesToSpend).fee(Parameters.MinFee * 2).outputs(tokenBox).sendChangeTo(poolOperator.getErgoAddress).build()
    val tokenTxSigned = poolOpProver.sign(tokenTx)
    val tokenTxId: String = ctx.sendTransaction(tokenTxSigned).filter(c => c !='\"')
    val smartPoolId = boxesToSpend.get(0).getId
    val tokenInputBox = tokenBox.convertToInputWith(tokenTxId, 0)

    txB = ctx.newTxBuilder()
    val mOB: MetadataOutputBuilder = new MetadataOutputBuilder(txB.outBoxBuilder())
    // Create output holding box
    val genesisBox: OutBox = MetadataContract.buildGenesisBox(mOB, metadataContract, poolOperator, initValue, ctx.getHeight, smartPoolToken, 0)


    println("Generating unspent boxes for pool creator " + poolOperator)


    // Create unsigned transaction
    val tx: UnsignedTransaction = txB
      .boxesToSpend(List(tokenInputBox).asJava)
      .outputs(genesisBox)
      .fee(Parameters.MinFee * 3)
      .sendChangeTo(poolOperator.getErgoAddress)
      .build()
    println("Initial Metadata Tx Built\n")
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')

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


    println(s"Pool Operator Address: ${poolOperator}")
    println(s"Sending ${commandValue} ERG to create new Command Box under Pool Operator address\n")

    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, commandValue + (Parameters.MinFee * 2), List[ErgoToken]().asJava).getBoxes.asScala.toList
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val commandTx = new CreateCommandTx(txB)

    val tx =
      commandTx
        .metadataToCopy(metadataBox)
        .withCommandContract(commandContract)
        .commandValue(commandValue)
        .inputBoxes(poolOperatorBoxes: _*)
        .buildCommandTx()
    println("Command Box Creation Tx Built\n")
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Command Id: ${signed.getOutputsToSpend.get(0).getId}")

    new CommandInputBox(commandTx.commandOutBox.convertToInputWith(txId, 0), commandContract)
  }

  def createModifiedCommandBox(ctx: BlockchainContext, metadataBox: MetadataInputBox) = {


    val holdingBoxes = ctx.getCoveringBoxesFor(holdingAddress, rewardsValue, List[ErgoToken]().toList.asJava).getBoxes

    println(s"Pool Operator Address: ${poolOperator}")
    println(s"Sending ${commandValue} ERG to create new Command Box under Pool Operator address\n")
    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, commandValue + (Parameters.MinFee * 2), List[ErgoToken]().asJava).getBoxes.asScala.toList
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val commandTx = new CreateCommandTx(txB)
    val tx =
      commandTx
        .metadataToCopy(metadataBox)
        .withCommandContract(commandContract)
        .commandValue(commandValue)
        .inputBoxes(poolOperatorBoxes: _*)
        .withHolding(new SimpleHoldingContract(holdingContract), holdingBoxes.asScala.toList)
        .buildCommandTx()
    println("Command Box Creation Tx Built\n")
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Command Id: ${signed.getOutputsToSpend.get(0).getId}")

    new CommandInputBox(commandTx.commandOutBox.convertToInputWith(txId, 0), commandContract)
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
    val holdingBoxes = BoxHelpers.findIdealHoldingBoxes(ctx, holdingAddress, rewardsValue, 0L)
    val tx = new DistributionTx(ctx.newTxBuilder())
    val builtTx =
      tx
        .metadataInput(metadataBox)
        .commandInput(commandBox)
        .holdingInputs(holdingBoxes)
        .holdingContract(new SimpleHoldingContract(holdingContract))
        .buildMetadataTx()
    val signed: SignedTransaction = poolOpProver.sign(builtTx)
    val metadataOutBox = tx.metadataOutBox
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


    val holdingBoxes = ctx.getCoveringBoxesFor(holdingAddress, rewardsValue, List[ErgoToken]().toList.asJava).getBoxes

    println(s"Pool Operator Address: ${poolOperator}")
    println(s"Sending ${commandValue} ERG to create new Command Box under Pool Operator address\n")

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val commandTx = new CreateCommandTx(txB)
    val shareCons = ShareConsensus.convert(Array(
      (poolMiner.getErgoAddress.script.bytes, Array(240L, Parameters.OneErg*2, 0)),
      (rewardsAddress.getErgoAddress.script.bytes, Array(140L, Parameters.OneErg/8, 0)),
      (poolMinerTwo.getErgoAddress.script.bytes, Array(110L, Parameters.OneErg/2, 0))
    ))
    val memsList = MemberList.convert(Array(
      (poolMiner.getErgoAddress.script.bytes, poolMiner.toString),
      (rewardsAddress.getErgoAddress.script.bytes, rewardsAddress.toString),
      (poolMinerTwo.getErgoAddress.script.bytes, poolMinerTwo.toString)

    ))

    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, commandValue + (Parameters.MinFee * 2), List[ErgoToken]().asJava).getBoxes.asScala.toList

    val tx =
      commandTx
      .metadataToCopy(metadataBox)
      .withCommandContract(commandContract)
      .commandValue(commandValue)
      .setConsensus(shareCons)
      .setMembers(memsList)
      .inputBoxes(poolOperatorBoxes: _*)
      .withHolding(new SimpleHoldingContract(holdingContract), holdingBoxes.asScala.toList)
      .buildCommandTx()


    println("Command Box Creation Tx Built\n")
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')
    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Command Id: ${signed.getOutputsToSpend.get(0).getId}")

    new CommandInputBox(commandTx.commandOutBox.convertToInputWith(txId, 0), commandContract)
  }

  def initialRecordingTx(ctx: BlockchainContext) = {

    println(s"Pool Operator Address: ${poolOperator}")

    println(s"Sending ${initValue} ERG to create new Metadata Box\n")
    val boxesToSpend = ctx.getCoveringBoxesFor(poolOperator, initValue + (Parameters.MinFee * 2), List[ErgoToken]().asJava).getBoxes
    var txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outB = txB.outBoxBuilder()
    val recordingToken = new ErgoToken(boxesToSpend.get(0).getId, 1)
    val tokenBox = outB
      .value(initValue + (Parameters.MinFee * 1))
      .mintToken(recordingToken, "Recording Box NFT", "Test Token Recording Box", 0)
      .contract(new ErgoTreeContract(poolOperator.getErgoAddress.script))
      .build()

    val tokenTx = txB.boxesToSpend(boxesToSpend).fee(Parameters.MinFee * 1).outputs(tokenBox).sendChangeTo(poolOperator.getErgoAddress).build()
    val tokenTxSigned = poolOpProver.sign(tokenTx)
    val tokenTxId: String = ctx.sendTransaction(tokenTxSigned).filter(c => c !='\"')
    val recordingId = boxesToSpend.get(0).getId
    val tokenInputBox = tokenBox.convertToInputWith(tokenTxId, 0)
    val voteYes = ProxyBallotContract.generateContract(ctx, recordingId, voteYes = true, recordingId).getAddress
    val voteNo = ProxyBallotContract.generateContract(ctx, recordingId, voteYes = false, recordingId).getAddress
    println("Vote Yes: " + voteYes.toString)
    println("Vote No: " + voteYes.toString)
    val recordingContract = RecordingContract.generateContract(ctx, recordingId, voteYes, voteNo, 1000)
    println("Recording: " + recordingContract.getAddress)
    txB = ctx.newTxBuilder()

    // Create output holding box
    val genesisBox: OutBox = RecordingContract.buildNewRecordingBox(ctx, recordingId, recordingContract, initValue)


    println("Generating unspent boxes for pool creator " + poolOperator)


    // Create unsigned transaction
    val tx: UnsignedTransaction = txB
      .boxesToSpend(List(tokenInputBox).asJava)
      .outputs(genesisBox)
      .fee(Parameters.MinFee * 1)
      .sendChangeTo(poolOperator.getErgoAddress)
      .build()
    println("Initial Recording Tx Built\n")
    val signed: SignedTransaction = poolOpProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed).filter(c => c !='\"')

    println(s"Tx successfully sent with id: ${txId} \n")
    println(signed.toJson(true))
    println(s"Metadata Id: ${signed.getOutputsToSpend.get(0).getId}")
    creationMetadataID = signed.getOutputsToSpend.get(0).getId

    new RecordingInputBox(genesisBox.convertToInputWith(txId, 0), recordingId)
  }


}
