package test

import boxes.builders.{CommandOutputBuilder, MetadataOutputBuilder}
import boxes.{CommandInputBox, MetadataInputBox, MetadataOutBox}
import contracts.command.{CommandContract, MinerPKContract, PKContract}
import contracts.holding.SimpleHoldingContract
import contracts.{MetadataContract, generateContractAddress}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoContract, ErgoId, ErgoProver, ErgoToken, InputBox, NetworkType, OutBox, Parameters, PreHeader, SecretString, SignedTransaction, UnsignedTransaction, UnsignedTransactionBuilder}
import registers.{MemberList, MetadataRegisters, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.SigmaDsl
import sigmastate.serialization.GroupElementSerializer
import test.TestParameters.{commandContract, commandValue, creationMetadataID, currentCommandID, currentMetadataID, holdingAddress, holdingContract, initValue, metadataAddress, metadataContract, networkType, poolMiner, poolMinerTwo, poolOpProver, poolOperator, rewardsAddress, rewardsProver, rewardsValue}
import transactions.{CreateCommandTx, DistributionTx}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}



object TestParameters {

  final val initValue = 1 * Parameters.OneErg
  final val commandValue = 3 * Parameters.MinFee
  final val rewardsValue = 3 * Parameters.OneErg
  final val networkType = NetworkType.TESTNET
  val poolMiner = Address.create("3WwtfPaghPbuPtYQs4Uj9QooYsPYkEZsESjmcR49MB4fs5kEshX7")
  val poolMinerTwo = Address.create("3WzKopFYhfRGPaUvC7v49DWgeY1efaCD3YpNQ6FZGr2t5mBhWjmw")
  var creationMetadataID = ErgoId.create("d9a06b8a5dcbb2f6a400eb581891ca5f1cbd30be0e15d61d7b9cb2d0d864c65a")


  val currentMetadataID = "10799205d8cc1117457baa7f155c1a98b5f45cd7160471470a4a48421130a58f"
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
        .withHolding(new SimpleHoldingContract(holdingContract), rewardsValue)
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
    val tx = new DistributionTx(ctx.newTxBuilder())
    val builtTx =
      tx
        .metadataInput(metadataBox)
        .commandInput(commandBox)
        .holdingValue(rewardsValue)
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

    val poolOperatorBoxes = ctx.getCoveringBoxesFor(poolOperator, commandValue + (Parameters.MinFee * 2), List[ErgoToken]().asJava).getBoxes.asScala.toList

    val tx =
      commandTx
      .metadataToCopy(metadataBox)
      .withCommandContract(commandContract)
      .commandValue(commandValue)
      .setConsensus(shareCons)
      .setMembers(memsList)
      .inputBoxes(poolOperatorBoxes: _*)
      .withHolding(new SimpleHoldingContract(holdingContract), rewardsValue)
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




}
