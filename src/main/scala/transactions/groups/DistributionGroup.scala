package transactions.groups

import boxes.{BoxHelpers, CommandInputBox, CommandOutBox, MetadataInputBox}
import config.SmartPoolConfig
import contracts.command.PKContract
import contracts.holding.HoldingContract
import logging.LoggingHandler
import org.ergoplatform.appkit.impl.{ErgoTreeContract, InputBoxImpl}
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoProver, InputBox, OutBox, Parameters, SignedTransaction}
import org.slf4j.{Logger, LoggerFactory}
import registers.{MemberList, PoolFees, ShareConsensus}
import transactions.{CreateCommandTx, DistributionTx}
import transactions.models.TransactionGroup

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try




class DistributionGroup(ctx: BlockchainContext, metadataInputs: Array[MetadataInputBox], prover: ErgoProver, address: Address,
                        blockReward: Long, holdingContract: HoldingContract, config: SmartPoolConfig,
                        shareConsensus: ShareConsensus, memberList: MemberList) extends TransactionGroup[Map[MetadataInputBox, String]]{

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

  final val SHARE_CONSENSUS_LIMIT = 1
  final val STANDARD_FEE = Parameters.MinFee * 5
  override def buildGroup: TransactionGroup[Map[MetadataInputBox, String]] = {
    logger.info("Now building DistributionGroup")

    logger.info(s"Using ${metadataInputs.length} metadata boxes, with ${shareConsensus.cValue.length} consensus vals")
    for(sc <- shareConsensus.cValue){
      val boxSearch = findMetadata(metadataInputs, sc)
      if(boxSearch.isDefined){
        val metadataBox = boxSearch.get
        val newShareConsensus = ShareConsensus.fromConversionValues(Array(sc))

        val memberVal = memberList.cValue.filter(m => m._1 sameElements sc._1).head
        val newMemberList = MemberList.fromConversionValues(Array(memberVal))
        if(boxToShare.contains(metadataBox)){
          val updatedShareConsensus = ShareConsensus.fromConversionValues(boxToShare(metadataBox).cValue++Array(sc))
          val updatedMemberList = MemberList.fromConversionValues(boxToMember(metadataBox).cValue++Array(memberVal))
          boxToShare = boxToShare.updated(metadataBox, updatedShareConsensus)
          boxToMember = boxToMember.updated(metadataBox, updatedMemberList)
        }else{
          boxToShare = boxToShare++Map((metadataBox, newShareConsensus))
          boxToMember = boxToMember++Map((metadataBox, newMemberList))
        }
      }else{
        throw new MetadataNotFoundException
      }
    }

    logger.info(s"boxToShare: ${boxToShare.size} boxToMember: ${boxToMember.size}")


    val totalShareScore = shareConsensus.cValue.map(sc => sc._2(0)).sum
    val totalHeld = blockReward
    //logger.info("Holding box length: " + holdingBoxes.length)
    var lastConsensus = ShareConsensus.fromConversionValues(Array())
    var lastFees = PoolFees.fromConversionValues(Array())
    logger.info(s"Total Share Score: $totalShareScore Total Held: $totalHeld")
    for(box <- metadataInputs){
      lastConsensus = ShareConsensus.fromConversionValues(lastConsensus.cValue++box.getShareConsensus.cValue)
      lastFees = box.getPoolFees
    }
    logger.info(s"LastConsensusSize: ${lastConsensus.cValue.length} LastPoolFees: ${lastFees.cValue.length}")
    var storedPaymentsUsed = List[InputBox]()
    for(box <- boxToShare.keys) {
      val shCons = box.getShareConsensus //TODO UNCOMMENT
      val boxValueHeld = shCons.cValue.map(c => c._2(2)).sum
      val boxShareScore = boxToShare(box).cValue.map(c => c._2(0)).sum
      val boxValueFromShares = BoxHelpers.removeDust(((BigDecimal(boxShareScore) / BigDecimal(totalShareScore)) * totalHeld).toLong)
      logger.info("BoxValueFromShares: " + boxValueFromShares)
      boxToValue = boxToValue ++ Map((box, (boxValueFromShares, boxValueHeld)))

      if (boxValueHeld != 0) {

        logger.info("Stored payment is not 0 for box, now searching for exact holding box!")
        val exactStoredBox = BoxHelpers.findExactBox(ctx, holdingContract.getAddress, boxValueHeld, storedPaymentsUsed)
        if (exactStoredBox.isDefined) {
          logger.info("Exact Holding Box Value: " + exactStoredBox.get.getValue)
          storedPaymentsUsed = storedPaymentsUsed ++ List(exactStoredBox.get)
          boxToStorage = boxToStorage ++ Map((box, exactStoredBox.get))
        } else {
          throw new StoredPaymentNotFoundException
        }
      }
    }
    boxToFees = collectFeeBoxes
    val regroupInputs = boxToFees.map(bF => (bF._1,bF._2(1)))
    logger.info("")
    val holdingChain = new HoldingChain(ctx, boxToValue, prover, address, STANDARD_FEE, regroupInputs.values.toList, holdingContract, config)
    val completedHoldingChain = holdingChain.executeChain
    if(completedHoldingChain.completed != boxToValue){
      throw new HoldingChainException
    }
    boxToHolding = completedHoldingChain.result
    logger.info("BoxToHolding Length: " + boxToHolding.size)
    boxToHolding.values.foreach(i => logger.info("BoxToHolding Value: " + i.getValue + " Id " + i.getId))


    this
  }

  override def executeGroup: TransactionGroup[Map[MetadataInputBox, String]] = {

    var boxToCmdOutput = Map.empty[MetadataInputBox, CommandOutBox]
    for (metadataBox <- boxToShare.keys) {
      val commandChain = Try {
        val commandTx = new CreateCommandTx(ctx.newTxBuilder())
        val commandContract = new PKContract(address)
        val inputBoxes = List(boxToFees(metadataBox)(1))
        val newPoolFees = PoolFees.fromConversionValues(Array((address.getErgoAddress.script.bytes, 1)))
        var holdingInputs = List(boxToHolding(metadataBox))
        if (boxToStorage.contains(metadataBox)) {
          holdingInputs = holdingInputs ++ List(boxToStorage(metadataBox))
        }

        logger.info(s"Adding new command box to map for box ${metadataBox.getId}")
        val unsignedCommandTx =
          commandTx
            .metadataToCopy(metadataBox)
            .withCommandContract(commandContract)
            .commandValue(cmdConf.getCommandValue)
            .inputBoxes(inputBoxes: _*)
            .withHolding(holdingContract, holdingInputs)
            .setConsensus(boxToShare(metadataBox))
            .setMembers(boxToMember(metadataBox))
            .setPoolFees(newPoolFees)
            .buildCommandTx()
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
    logger.info("Command Input Boxes: " + cmdOutputsToSpend)
    val commandContract = new PKContract(address)
    val commandBoxes = cmdOutputsToSpend.filter(ib => ib.getValue == cmdConf.getCommandValue).map(o => new CommandInputBox(o, commandContract)).toArray

    for(metadataBox <- boxToShare.keys){
      val commandBox = commandBoxes.filter(i => i.getSubpoolId == metadataBox.getSubpoolId).head
      boxToCommand = boxToCommand++Map((metadataBox, commandBox))
    }
    logger.info(s"Total of ${boxToCommand.size} command boxes in map")

    for(metadataBox <- boxToShare.keys){
      val distributionChain = Try {
        val commandBox = boxToCommand(metadataBox)
        logger.info("Now building DistributionTx using new command box...")
        logger.info("Command Box: " + commandBox.toString)
        logger.info("Metadata Box: " + metadataBox.toString)

        var holdingInputs = List(boxToHolding(metadataBox))
        if (boxToStorage.contains(metadataBox)) {
          holdingInputs = holdingInputs ++ List(boxToStorage(metadataBox))
        }

        logger.info("Initial holding inputs made!")

        logger.info("Total Holding Input Value: " + BoxHelpers.sumBoxes(holdingInputs))
        val distTx = new DistributionTx(ctx.newTxBuilder())
        val unsignedDistTx =
          distTx
            .metadataInput(metadataBox)
            .commandInput(commandBox)
            .holdingInputs(holdingInputs)
            .holdingContract(holdingContract)
            .buildMetadataTx()
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

  def findMetadata(metadataArray: Array[MetadataInputBox], consensusVal: (Array[Byte], Array[Long])): Option[MetadataInputBox] = {
    for(box <- metadataArray){
      val shCons = box.getShareConsensus
      if(shCons.cValue.exists(c => c._1 sameElements consensusVal._1) && box.getShareConsensus.cValue.length <= SHARE_CONSENSUS_LIMIT){
        return Some(box)
      }
//      if(!boxToShare.contains(box)){
//        return Some(box)
//      }
    }

    for(box <- metadataArray){
      if(box.getShareConsensus.cValue.length <= SHARE_CONSENSUS_LIMIT){
        return Some(box)
      }
    }
    None
  }

  /**
   * Creates tx to collect fee boxes from node wallet and use them in subsequent transactions
   */
  def collectFeeBoxes: Map[MetadataInputBox, Array[InputBox]] = {
    var feeOutputs = List[OutBox]()
    val txB = ctx.newTxBuilder()
    var totalFees = 0L
    var boxToOutputs = Map.empty[MetadataInputBox, Array[OutBox]]
    var boxToFeeInputs = Map.empty[MetadataInputBox, Array[InputBox]]
    for(boxSh <- boxToShare){
     // val txFee = boxSh._2.cValue.length * Parameters.MinFee
     // val holdingFee = txB.outBoxBuilder().value(txFee).contract(new ErgoTreeContract(address.getErgoAddress.script)).build()
      val commandFee = txB.outBoxBuilder().value(cmdConf.getCommandValue + STANDARD_FEE).contract(new ErgoTreeContract(address.getErgoAddress.script)).build()
      val regroupFee = txB.outBoxBuilder().value(boxToValue(boxSh._1)._1 + STANDARD_FEE).contract(new ErgoTreeContract(address.getErgoAddress.script)).build()
      totalFees = totalFees + cmdConf.getCommandValue + STANDARD_FEE + boxToValue(boxSh._1)._1 + STANDARD_FEE

      // Elem 0 is distribution out box
      boxToOutputs = boxToOutputs++Map((boxSh._1, Array(commandFee, regroupFee)))
    }


    val feeTxOutputs = boxToOutputs.values.flatten.toArray
    val feeInputBoxes = ctx.getWallet.getUnspentBoxes(totalFees+STANDARD_FEE).get()
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
      val regroupFeeVal = outBoxes(1).getValue
      // val holdingFeeBox = feeInputs.asScala.filter(fb => fb.getValue == holdingFeeVal && !inputsAdded.contains(fb)).head
      // logger.info("HoldingFeeBoxId: " + holdingFeeBox.getId)
      // inputsAdded = inputsAdded++Array(holdingFeeBox)
      val commandFeeBox = feeInputs.asScala.filter(fb => fb.getValue == commandFeeVal && !inputsAdded.contains(fb)).head
      inputsAdded = inputsAdded++Array(commandFeeBox)
      logger.info("CommandFeeBoxId: " + commandFeeBox.getId)
      val regroupFeeBox = feeInputs.asScala.filter(fb => fb.getValue == regroupFeeVal && !(inputsAdded.exists(ib => ib.getId == fb.getId))).head
      inputsAdded = inputsAdded++Array(regroupFeeBox)
      logger.info("RegroupFeeBoxId: " + regroupFeeBox.getId)

      logger.info("Inputs Added: " + inputsAdded.length)
      boxToFeeInputs = boxToFeeInputs++Map((box, Array(commandFeeBox, regroupFeeBox)))
    }
    logger.info("Fee Box Inputs: " + boxToFeeInputs.size)

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