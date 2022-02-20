package groups

import app.ExitCodes
import boxes.{BoxHelpers, CommandInputBox, CommandOutBox, MetadataInputBox}
import configs.SmartPoolConfig
import contracts.command.{CommandContract, PKContract}
import contracts.holding.HoldingContract
import groups.chains.{HoldingChain, HoldingChainException}
import groups.exceptions.{ExactTokenBoxNotFoundException, StoredPaymentNotFoundException}
import logging.LoggingHandler
import org.ergoplatform.appkit.impl.{ErgoTreeContract, InputBoxImpl}
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, ErgoProver, ErgoToken, InputBox, OutBox, Parameters, SignedTransaction}
import org.slf4j.{Logger, LoggerFactory}
import registers.{MemberList, PoolFees, PoolOperators, ShareConsensus}
import transactions.{CreateCommandTx, DistributionTx}
import transactions.models.TransactionGroup

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try




class DistributionGroup(ctx: BlockchainContext, metadataInputs: Array[MetadataInputBox], prover: ErgoProver, address: Address,
                        blockReward: Long, holdingContract: HoldingContract, commandContract: CommandContract, config: SmartPoolConfig,
                        shareConsensus: ShareConsensus, memberList: MemberList, poolFees: PoolFees, isFailureAttempt: Boolean, failureIds: Array[String]) extends TransactionGroup[Map[MetadataInputBox, String]]{

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DIST_GRP)
  private[this] var _completed = Map[MetadataInputBox, String]()
  private[this] var _failed = Map[MetadataInputBox, String]()
  private[this] var _txs = Map[MetadataInputBox, SignedTransaction]()


  private val cmdConf = config.getParameters.getCommandConf
  private val metaConf = config.getParameters.getMetaConf
  private val holdConf = config.getParameters.getHoldingConf

  private var boxToShare = Map.empty[MetadataInputBox, ShareConsensus]
  private var boxToMember = Map.empty[MetadataInputBox, MemberList]
  private var boxToValue = Map.empty[MetadataInputBox, (Long, Long)]
  private var boxToHolding = Map.empty[MetadataInputBox, InputBox]
  private var boxToStorage = Map.empty[MetadataInputBox, InputBox]
  private var boxToCommand = Map.empty[MetadataInputBox, CommandInputBox]
  private var boxToFees = Map.empty[MetadataInputBox, Array[InputBox]]

  final val SHARE_CONSENSUS_LIMIT = 10
  final val STANDARD_FEE = Parameters.MinFee * 5

  var customCommand = false
  var voteTokenStr = config.getParameters.getVotingConf.getVoteTokenId
  override def buildGroup: TransactionGroup[Map[MetadataInputBox, String]] = {
    logger.info("Now building DistributionGroup")

    logger.info(s"Using ${metadataInputs.length} metadata boxes, with ${shareConsensus.cValue.length} consensus vals")
    val subpoolSelector = new SubpoolSelector
    val membersLeft = subpoolSelector.selectDefaultSubpools(metadataInputs, shareConsensus, memberList)._2
    boxToShare = subpoolSelector.shareMap
    boxToMember = subpoolSelector.memberMap

    for(boxSh <- boxToShare){
      logger.info(s"Subpool ${boxSh._1.getSubpoolId} has ${boxSh._2.cValue.length} members")
      if(boxSh._2.cValue.length > SHARE_CONSENSUS_LIMIT){
        logger.error(s"There was an error assigning a share consensus to subpool ${boxSh._1.getSubpoolId}")
        logger.error(s"Current share consensus length: ${boxSh._2.cValue.length}")
        app.exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
      }
//      if(boxSh._2.cValue.length == 0 || boxSh._2.cValue.map(c => c._2(0)).sum == 0){
//        logger.info(s"Subpool ${boxSh._1.getSubpoolId} removed from maps due to having 0 new members or shares!")
//        removeFromMaps(boxSh._1)
//      }
      if (boxSh._1.getPoolOperators.cValue.exists(c => c._1 sameElements commandContract.getErgoTree.bytes)) {
        if (voteTokenStr != "")
          customCommand = true
      }

      logger.info(s"==== Subpool ${boxSh._1.getSubpoolId} ====\n")
      val memberStrings = boxToMember(boxSh._1).cValue.map(m => m._2)
      logger.info(memberStrings.mkString("\n"))
    }

    if(membersLeft.length > 0){
      logger.warn("There are still members left after adding to existing subpools. Please msg kirat, he knows how to fix this")

      logger.warn("Members to add: " + membersLeft.map(m => m._2).mkString("\n"))
      //TODO: Add new member addition to epoch 0 subpools. Should not be needed for a while until greater than 250 members join the pool.
      app.exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
    }

    if(isFailureAttempt){
      logger.info("Is failure attempt!")
      boxToMember.foreach(m => logger.info(m.toString()))
      boxToMember.foreach(m => logger.info(m._2.cValue.mkString("Array(", ", ", ")")))
    }

    logger.info(s"boxToShare: ${boxToShare.size} boxToMember: ${boxToMember.size}")


    val totalShareScore = shareConsensus.cValue.map(sc => sc._2(0)).sum
    val totalHeld = blockReward
    //logger.info("Holding box length: " + holdingBoxes.length)
    var lastConsensus = ShareConsensus.convert(Array())
    var lastFees = PoolFees.convert(Array())
    logger.info(s"Total Share Score: $totalShareScore Total Held: $totalHeld")
    for(box <- boxToShare.keys){
      lastConsensus = ShareConsensus.convert(lastConsensus.cValue++box.getShareConsensus.cValue)
      lastFees = box.getPoolFees
    }
    logger.info(s"LastConsensusSize: ${lastConsensus.cValue.length} LastPoolFees: ${lastFees.cValue.length}")

    var holdingBoxes = BoxHelpers.loadBoxes(ctx, holdingContract.getAddress)
    var holdingBoxesList = List[InputBox]()
    for(box <- boxToShare.keys) {
      val shCons = box.getShareConsensus //TODO UNCOMMENT
      val boxValueHeld = shCons.cValue.map(c => c._2(2)).sum
      val boxShareScore = boxToShare(box).cValue.map(c => c._2(0)).sum
      val boxValueFromShares = BoxHelpers.removeDust(((BigDecimal(boxShareScore) / BigDecimal(totalShareScore)) * totalHeld).toLong)
      logger.info("BoxValueFromShares: " + boxValueFromShares)
      boxToValue = boxToValue ++ Map((box, (boxValueFromShares, boxValueHeld)))

      if (boxValueHeld != 0) {

        logger.info(s"Stored payment is not 0 for box, now searching for exact holding box with value $boxValueHeld")
        val exactStoredBox = BoxHelpers.findExactBox(boxValueHeld, holdingBoxes)
        if (exactStoredBox.isDefined) {
          logger.info("Exact Holding Box Value: " + exactStoredBox.get.getValue)
          logger.info("Exact storage box id: " + exactStoredBox.get.getId)
          require(boxValueHeld == exactStoredBox.get.getValue)
          holdingBoxes = holdingBoxes.filter(i => i.getId != exactStoredBox.get.getId)
          boxToStorage = boxToStorage ++ Map((box, exactStoredBox.get))
        } else {
          throw new StoredPaymentNotFoundException
        }
      }
    }
    var withHoldingChain = false


    var exactHoldingMap = Map[MetadataInputBox, InputBox]()
    for(hv <- boxToValue) {
      if (!withHoldingChain) {
        logger.info("Searching for exact holding box with value " + hv._2._1)
        val exactHoldingBox = BoxHelpers.findExactBox(hv._2._1, holdingBoxes)
        if (exactHoldingBox.isEmpty) {
          logger.info("An exact holding box could not be found, a new holding chain is being created")
          withHoldingChain = true

        } else {
          logger.info("An exact holding box was found!")
          logger.info("Current withHoldingChain: " + withHoldingChain)
          require(hv._2._1 == exactHoldingBox.get.getValue)
          holdingBoxes = holdingBoxes.filter(i => i.getId != exactHoldingBox.get.getId)
          exactHoldingMap = exactHoldingMap ++ Map((hv._1, exactHoldingBox.get))
          logger.info("Current holdingBoxesList: " + holdingBoxes.length)
        }
      }
      if(hv._2._1 == 0 && hv._2._2 == 0){
        logger.info(s"Subpool ${hv._1.getSubpoolId} has 0 value locked or added! Now removing from maps.")
        removeFromMaps(hv._1)
      }

    }

    boxToFees = collectFeeBoxes(withHoldingChain)
    if(withHoldingChain) {

      val regroupInputs = boxToFees.map(bF => (bF._1, bF._2(1)))
      logger.info("A new holding chain is being created for this distribution!")

      val holdingChain = new HoldingChain(ctx, boxToValue, prover, address, STANDARD_FEE, regroupInputs.values.toList, holdingContract, config)
      Thread.sleep(500)
      val completedHoldingChain = holdingChain.executeChain
      if (completedHoldingChain.completed != boxToValue) {
        throw new HoldingChainException
      }
      boxToHolding = completedHoldingChain.result
    }else{
      logger.info("Exact holding inputs were found, setting boxToHolding map equal to exactHoldingMap")
      boxToHolding = exactHoldingMap
    }
    logger.info("BoxToHolding Length: " + boxToHolding.size)
    boxToHolding.values.foreach(i => logger.info("BoxToHolding Value: " + i.getValue + " Id " + i.getId))


    this
  }

  override def executeGroup: TransactionGroup[Map[MetadataInputBox, String]] = {
    var commandContractToUse: CommandContract = new PKContract(address)

    var boxToCmdOutput = Map.empty[MetadataInputBox, CommandOutBox]
    for (metadataBox <- boxToShare.keys) {
      val commandChain = Try {
        val commandTx = new CreateCommandTx(ctx.newTxBuilder())

        val inputBoxes = List(boxToFees(metadataBox)(0))
        // TODO: Take from config instead


        val newPoolOperators = PoolOperators.convert(Array(
          (commandContract.getErgoTree.bytes, "Vote Token Distributor"),
          (address.getErgoAddress.script.bytes, address.toString),
        ))

        var holdingInputs = List(boxToHolding(metadataBox))
        if (boxToStorage.contains(metadataBox)) {
          holdingInputs = holdingInputs ++ List(boxToStorage(metadataBox))
        }

        commandContractToUse = new PKContract(address)
        logger.info("Metadata box operators: " + metadataBox.getPoolOperators.cValue.toString)
        if(metadataBox.getPoolOperators.cValue.exists(op => op._1 sameElements commandContract.getErgoTree.bytes)){
          logger.info("MetadataBox has custom command contract in pool operators, now prioritizing custom command contract.")
          commandContractToUse = commandContract
        }
        logger.info(s"Adding new command box to map for box ${metadataBox.getId}")
        val unbuiltCommandTx =
          commandTx
            .metadataToCopy(metadataBox)
            .withCommandContract(commandContractToUse)
            .commandValue(cmdConf.getCommandValue)
            .inputBoxes(inputBoxes: _*)
            .withHolding(holdingContract, holdingInputs)
            .setConsensus(boxToShare(metadataBox))
            .setMembers(boxToMember(metadataBox))
            .setPoolFees(poolFees)
            .setPoolOps(newPoolOperators)
        if(voteTokenStr != "" && customCommand) {
          logger.info("Custom token id set, adding tokens to command output")
          unbuiltCommandTx.cOB.tokens(boxToFees(metadataBox)(0).getTokens.get(0))
        }
        val unsignedCommandTx = unbuiltCommandTx.buildCommandTx()
        logger.info(s"Command box built!")
        boxToCmdOutput = boxToCmdOutput ++ Map((metadataBox, commandTx.commandOutBox))
        logger.info("Command box added to outputs")
        //val signedCmdTx = prover.sign(unsignedCommandTx)
      }
      if(commandChain.isFailure) {
        logger.warn(s"Exception caught for metadata box ${metadataBox.getId.toString} during command chain execution!")
        logger.warn(commandChain.failed.get.getMessage)

        logger.warn("Now adding metadata box to failure list")
        _failed = _failed++Map((metadataBox, "cmdTx"))
        removeFromMaps(metadataBox)
      }
    }

    val commandInputs = boxToFees.values.map(f => f(0)).toList
    logger.info(s"New cmd tx with ${commandInputs.length} command inputs and ${boxToCmdOutput.size} command outputs")



    val unsignedCommandTx = ctx.newTxBuilder()
      .boxesToSpend(commandInputs.asJava)
      .fee(STANDARD_FEE * boxToCmdOutput.size)
      .outputs(boxToCmdOutput.values.map(c => c.asOutBox).toList:_*)
      .sendChangeTo(address.getErgoAddress)
      .build()
    val signedCmdTx = prover.sign(unsignedCommandTx)
    logger.info(s"Signed Tx Num Bytes Cost: ${signedCmdTx.toBytes.length}")
    logger.info(s"Signed Tx Cost: ${signedCmdTx.getCost}")
    val cmdOutputsToSpend = signedCmdTx.getOutputsToSpend.asScala
    for(ib <- cmdOutputsToSpend) {
      val asErgoBox = ib.asInstanceOf[InputBoxImpl].getErgoBox
      logger.info("Total ErgoBox Bytes: " + asErgoBox.bytes.length)
    }

    logger.info("Command Tx successfully signed")
    val cmdTxId = ctx.sendTransaction(signedCmdTx)
    logger.info(s"Tx was successfully sent with id: $cmdTxId and cost: ${signedCmdTx.getCost}")
    Thread.sleep(500)
    logger.info("Command Input Boxes: " + cmdOutputsToSpend)
    val pkContract = new PKContract(address)
    val commandBoxes = cmdOutputsToSpend
      .filter(ib => ib.getValue == cmdConf.getCommandValue)
      .filter(ib =>
      ((ib.getErgoTree.bytes sameElements commandContract.getErgoTree.bytes) || (ib.getErgoTree.bytes sameElements pkContract.getErgoTree.bytes))).map{
      o =>
        commandContractToUse = new PKContract(address)
        if(o.getErgoTree.bytes sameElements commandContract.getErgoTree.bytes) {
          logger.info("Using custom command contract for box")
          commandContractToUse = commandContract
        }
        new CommandInputBox(o, commandContractToUse)
    }
    logger.info(commandBoxes.mkString("\n"))
    for(metadataBox <- boxToShare.keys){
      val commandBox = commandBoxes.filter(i => i.getSubpoolId == metadataBox.getSubpoolId).head
      if(commandBox.contract.getErgoTree.bytes sameElements commandContract.getErgoTree.bytes) {
        logger.info(s"Subpool ${commandBox.getSubpoolId} has custom command contract")
        if (voteTokenStr != "" && customCommand) {
          logger.info("Tokens in current command box: ")
          logger.info(s"id: ${commandBox.getTokens.get(0).getId}")
          logger.info(s"amnt: ${commandBox.getTokens.get(0).getValue}")
        }
      }else{
        logger.info(s"Subpool ${commandBox.getSubpoolId} has default command contract.")
      }
      logger.info(s"Adding command box with id ${commandBox.getSubpoolId} to metadata box with id ${metadataBox.getSubpoolId}")
      boxToCommand = boxToCommand++Map((metadataBox, commandBox))
    }
    logger.info(s"Total of ${boxToCommand.size} command boxes in map")

    for(metadataBox <- boxToShare.keys){
      val distributionChain = Try {
        Thread.sleep(500)
        val commandBox = boxToCommand(metadataBox)
        logger.info("Now building DistributionTx using new command box...")
        logger.info("Command Box: " + commandBox.toString)
        logger.info("Metadata Box: " + metadataBox.toString)

        var holdingInputs = List(boxToHolding(metadataBox))
        if (boxToStorage.contains(metadataBox)) {
          logger.info("Exact storage box with value " + boxToStorage(metadataBox).getValue + " is being used")
          holdingInputs = holdingInputs ++ List(boxToStorage(metadataBox))
        }

        logger.info("Initial holding inputs made!")

        logger.info("Total Holding Input Value: " + BoxHelpers.sumBoxes(holdingInputs))
        val distTx = new DistributionTx(ctx.newTxBuilder())
        val unbuiltDistTx =
          distTx
            .metadataInput(metadataBox)
            .commandInput(commandBox)
            .holdingInputs(holdingInputs)
            .holdingContract(holdingContract)
            .operatorAddress(address)


        if(voteTokenStr != "" && customCommand){
          logger.info("VoteTokenId set, now adding tokens to distribute to distribution tx.")
          unbuiltDistTx.tokenToDistribute(commandBox.getTokens.get(0))
        }

        val unsignedDistTx = unbuiltDistTx.buildMetadataTx()
        val signedDistTx = prover.sign(unsignedDistTx)

        logger.info(s"Signed Tx Num Bytes Cost: ${
          signedDistTx.toBytes.length
        }")

        logger.info(s"Signed Tx Cost: ${signedDistTx.getCost}")
        val distAsErgoBox = signedDistTx.getOutputsToSpend.get(0).asInstanceOf[InputBoxImpl].getErgoBox

        logger.info("Total ErgoBox Bytes: " + distAsErgoBox.bytes.length)


        val signedTx = signedDistTx
        logger.info("Distribution Tx successfully signed.")
        val txId = ctx.sendTransaction(signedDistTx).filter(c => c != '\"')
        logger.info(s"Tx successfully sent with id: $txId and cost: ${signedDistTx.getCost}")
        val newMetadataBox = new MetadataInputBox(signedDistTx.getOutputsToSpend.get(0), metadataBox.getSmartPoolId)
        signedDistTx.toJson(true)
        _completed = _completed ++ Map((newMetadataBox, txId))
        _txs = _txs++Map((newMetadataBox, signedDistTx))
        logger.info("Now waiting for 0.5secs")
        Thread.sleep(500)
      }

      if(distributionChain.isFailure) {
        logger.warn(s"Exception caught for metadata box ${metadataBox.getId.toString}")
        logger.info(distributionChain.failed.get.getMessage)

        logger.warn("Now adding metadata box to failure list")
        _failed = _failed ++ Map((metadataBox, "distTx"))
        removeFromMaps(metadataBox)
      }

    }

    this
  }

  override def completed: Map[MetadataInputBox, String] = _completed

  override def failed: Map[MetadataInputBox, String] = _failed

  def successfulTxs: Map[MetadataInputBox, SignedTransaction] = _txs

  /**
   * Creates tx to collect fee boxes from node wallet and use them in subsequent transactions
   */
  def collectFeeBoxes(withHolding: Boolean): Map[MetadataInputBox, Array[InputBox]] = {
    var feeOutputs = List[OutBox]()
    val txB = ctx.newTxBuilder()
    var totalFees = 0L
    var boxToOutputs = Map.empty[MetadataInputBox, Array[OutBox]]
    var boxToFeeInputs = Map.empty[MetadataInputBox, Array[InputBox]]
    for(boxSh <- boxToShare){
     // val txFee = boxSh._2.cValue.length * Parameters.MinFee
     // val holdingFee = txB.outBoxBuilder().value(txFee).contract(new ErgoTreeContract(address.getErgoAddress.script)).build()
      val commandFeeOutput = txB.outBoxBuilder().value(cmdConf.getCommandValue + STANDARD_FEE).contract(new ErgoTreeContract(address.getErgoAddress.script))
      if(voteTokenStr != "" && customCommand){
        // If vote token id exists, lets send vote tokens equal to total amount in holding contracts
        val voteTokenId = ErgoId.create(voteTokenStr)
        val totalTokenAmnt = boxToValue(boxSh._1)._1 + boxToValue(boxSh._1)._2
        commandFeeOutput.tokens(new ErgoToken(voteTokenId, totalTokenAmnt))
        logger.info(s"Added $totalTokenAmnt tokens to commandFeeOutput box for subpool ${boxSh._1.getSubpoolId}")

      }
      val commandFee = commandFeeOutput.build()
      if(withHolding) {
        val regroupFee = txB.outBoxBuilder().value(boxToValue(boxSh._1)._1 + STANDARD_FEE).contract(new ErgoTreeContract(address.getErgoAddress.script)).build()
        totalFees = totalFees + cmdConf.getCommandValue + STANDARD_FEE + boxToValue(boxSh._1)._1 + STANDARD_FEE
        boxToOutputs = boxToOutputs++Map((boxSh._1, Array(commandFee, regroupFee)))
      }else {
        totalFees = totalFees + cmdConf.getCommandValue + STANDARD_FEE

        // Elem 0 is distribution out box
        boxToOutputs = boxToOutputs ++ Map((boxSh._1, Array(commandFee)))
      }
    }


    val feeTxOutputs = boxToOutputs.values.flatten.toArray
    var feeInputBoxes = ctx.getWallet.getUnspentBoxes(totalFees+STANDARD_FEE).get()

    if(voteTokenStr != "" && customCommand){
      logger.info("Now checking if enough vote tokens are in current boxes")
      val voteTokenId = ErgoId.create(voteTokenStr)
      val totalTokens = boxToValue.values.map(v => v._1 + v._2).sum
      val currentTokens = feeInputBoxes.asScala
        .filter(ib => ib.getTokens.size() > 0)
        .filter(ib => ib.getTokens.get(0).getId.toString == voteTokenId.toString)
        .map(ib => ib.getTokens.get(0).getValue)
        .sum
      logger.info("Total tokens needed: " + totalTokens)
      logger.info("Current tokens in boxes: " + currentTokens)
      if(currentTokens < totalTokens){
        logger.info("Not enough tokens found in current boxes, now searching for exact token box.")
        val exactTokenBox = BoxHelpers.findExactTokenBox(ctx, address, voteTokenId, totalTokens - currentTokens)
        if(exactTokenBox.isDefined){
          logger.info("Exact token box found, now adding to fee input boxes.")
          feeInputBoxes.add(exactTokenBox.get)
          logger.info(s"Token Box: \n " + exactTokenBox.get.toJson(true))
        }else{
          logger.error("No token box could be found!")
          throw new ExactTokenBoxNotFoundException
        }
      }
    }

    logger.info("Total Fees: " + totalFees)
    logger.info("Total Fee Output Size: " + feeTxOutputs.size)
    logger.info("Total Fee Tx Input Size: " + feeInputBoxes.size)
    logger.info("Total Fee Tx Input Val: " + BoxHelpers.sumBoxes(feeInputBoxes.asScala.toList))
    logger.info("UnsignedTx Now building")
    val unsignedTx = txB.boxesToSpend(feeInputBoxes).fee(STANDARD_FEE).outputs(feeTxOutputs:_*).sendChangeTo(address.getErgoAddress).build()
    val signedTx = prover.sign(unsignedTx)
    logger.info("Fee Tx signed")
    val txId = ctx.sendTransaction(signedTx)
    logger.info(s"Tx sent with fee: $txId and cost: ${signedTx.getCost}")
    var inputsAdded = Array[InputBox]()
    val feeInputs = signedTx.getOutputsToSpend
    for(box <- boxToShare.keys){
      val outBoxes = boxToOutputs(box)
      //val holdingFeeVal = outBoxes(0).getValue
      val commandFeeVal = outBoxes(0).getValue
      val commandFeeBox = feeInputs.asScala.filter(fb => fb.getValue == commandFeeVal && !inputsAdded.contains(fb)).head
      inputsAdded = inputsAdded++Array(commandFeeBox)
      logger.info("CommandFeeBoxId: " + commandFeeBox.getId)
      if(commandFeeBox.getTokens.size() > 0){
        logger.info(s"CommandFeeBox Tokens - id: ${commandFeeBox.getTokens.get(0).getId} amnt: ${commandFeeBox.getTokens.get(0).getValue}")
      }
      if(withHolding) {
        val regroupFeeVal = outBoxes(1).getValue
        // val holdingFeeBox = feeInputs.asScala.filter(fb => fb.getValue == holdingFeeVal && !inputsAdded.contains(fb)).head
        // logger.info("HoldingFeeBoxId: " + holdingFeeBox.getId)
        // inputsAdded = inputsAdded++Array(holdingFeeBox)
        val regroupFeeBox = feeInputs.asScala.filter(fb => fb.getValue == regroupFeeVal && !(inputsAdded.exists(ib => ib.getId == fb.getId))).head
        inputsAdded = inputsAdded ++ Array(regroupFeeBox)
        logger.info("RegroupFeeBoxId: " + regroupFeeBox.getId)

        logger.info("Inputs Added: " + inputsAdded.length)
        boxToFeeInputs = boxToFeeInputs ++ Map((box, Array(commandFeeBox, regroupFeeBox)))
      }else{
        logger.info("Inputs Added: " + inputsAdded.length)
        boxToFeeInputs = boxToFeeInputs ++ Map((box, Array(commandFeeBox)))
      }
    }
    logger.info("Fee Box Inputs: " + boxToFeeInputs.size)
    Thread.sleep(500)
    boxToFeeInputs
  }

  def removeFromMaps(metadataInputBox: MetadataInputBox): Unit = {
    boxToShare = boxToShare--List(metadataInputBox)
    boxToMember = boxToMember--List(metadataInputBox)
    boxToValue = boxToValue--List(metadataInputBox)
    boxToHolding = boxToHolding--List(metadataInputBox)
    boxToStorage = boxToStorage--List(metadataInputBox)
    boxToCommand = boxToCommand--List(metadataInputBox)
    boxToFees = boxToFees--List(metadataInputBox)
  }

}