package groups

import app.ExitCodes
import boxes.{BoxHelpers, CommandInputBox, CommandOutBox, MetadataInputBox}
import configs.SmartPoolConfig
import contracts.command.{CommandContract, PKContract}
import contracts.holding.HoldingContract
import groups.chains.{HoldingChain, HoldingChain2, HoldingChainException}
import groups.exceptions.{ExactTokenBoxNotFoundException, StoredPaymentNotFoundException}
import logging.LoggingHandler
import org.ergoplatform.appkit.impl.{ErgoTreeContract, InputBoxImpl}
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import registers.{MemberList, PoolFees, PoolOperators, ShareConsensus}
import transactions.{CreateCommandTx, DistributionTx}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try


class HoldingGroup(ctx: BlockchainContext, metadataInputs: Array[MetadataInputBox], prover: ErgoProver, address: Address,
                   blockReward: Long, holdingContract: HoldingContract, config: SmartPoolConfig,
                   shareConsensus: ShareConsensus, memberList: MemberList) extends TransactionGroup[Map[MetadataInputBox, String]]{

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_HOLD_GRP)
  private[this] var _completed = Map[MetadataInputBox, String]()
  private[this] var _failed = Map[MetadataInputBox, String]()
  private[this] var _boxes = Map[MetadataInputBox, InputBox]()


  private val cmdConf = config.getParameters.getCommandConf
  private val metaConf = config.getParameters.getMetaConf
  private val holdConf = config.getParameters.getHoldingConf

  private var boxToShare = Map.empty[MetadataInputBox, ShareConsensus]
  private var boxToMember = Map.empty[MetadataInputBox, MemberList]
  private var boxToValue = Map.empty[MetadataInputBox, Long]
  private var boxToHolding = Map.empty[MetadataInputBox, InputBox]
  private var boxToExact = Map.empty[MetadataInputBox, InputBox]
  private var boxToInputs = Map.empty[MetadataInputBox, InputBox]

  final val SHARE_CONSENSUS_LIMIT = 10
  final val STANDARD_FEE = Parameters.MinFee * 5

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
    }

    if(membersLeft.length > 0){
      logger.warn("There are still members left after adding to existing subpools. Please msg kirat, he knows how to fix this")

      logger.warn("Members to add: " + membersLeft.map(m => m._2).mkString("\n"))
      //TODO: Add new member addition to epoch 0 subpools. Should not be needed for a while until greater than 250 members join the pool.
      app.exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
    }

    logger.info(s"boxToShare: ${boxToShare.size} boxToMember: ${boxToMember.size}")


    val totalShareScore = shareConsensus.cValue.map(sc => sc._2(0)).sum
    val totalHeld = blockReward

    logger.info(s"Total Share Score: $totalShareScore Total Held: $totalHeld")

    var holdingBoxes = BoxHelpers.loadBoxes(ctx, holdingContract.getAddress)

    for(box <- boxToShare.keys) {
      val boxShareScore = boxToShare(box).cValue.map(c => c._2(0)).sum
      val boxValueFromShares = BoxHelpers.removeDust(((BigDecimal(boxShareScore) / BigDecimal(totalShareScore)) * totalHeld).toLong)
      logger.info("BoxValueFromShares: " + boxValueFromShares)
      boxToValue = boxToValue ++ Map((box, boxValueFromShares))
    }


    for(hv <- boxToValue) {
      if (hv._2 != 0) {
        logger.info("Searching for exact holding box with value " + hv._2)
//        val exactHoldingBox = BoxHelpers.findExactBox(hv._2, holdingBoxes)
//        if (exactHoldingBox.isDefined) {
//          logger.info(s"Exact holding box was found for subpool ${hv._1.getSubpoolId}. This box will not be present in the holdingChain!")
//          holdingBoxes = holdingBoxes.filter(i => i.getId != exactHoldingBox.get.getId)
//          boxToExact = boxToExact ++ Map((hv._1, exactHoldingBox.get))
//          boxToValue = boxToValue -- Array(hv._1)
//          logger.info("Current holdingBoxesList: " + holdingBoxes.length)
//        }

      } else {
        logger.info(s"Subpool ${hv._1.getSubpoolId} has 0 value locked or added! Now removing from maps.")
        removeFromMaps(hv._1)
      }
    }

    this
  }

  override def executeGroup: TransactionGroup[Map[MetadataInputBox, String]] = {
    if(boxToValue.isEmpty){
      logger.warn("Current box to value is empty, double check to make sure all holding inputs exist already.")
      _completed = boxToExact.map(b => (b._1, b._2.getId.toString))
      _boxes = boxToExact
      return this
    }
    boxToInputs = collectBoxes

    val holdingChain = Try {
      logger.info("Holding Group is now being executed")
      val holdingChain = new HoldingChain2(ctx, boxToValue, prover, address, STANDARD_FEE, boxToInputs.values.toList, holdingContract)
      Thread.sleep(500)
      val completedHoldingChain = holdingChain.executeChain
      if (completedHoldingChain.completed != boxToValue) {
        throw new HoldingChainException
      }
      boxToHolding = completedHoldingChain.result ++ boxToExact
    }

    if(holdingChain.isFailure) {
      logger.error(s"Exception caught during holding chain execution!")
      logger.warn(holdingChain.failed.get.getMessage)
      logger.warn("Now adding metadata boxes to failure list")
      val failedMap = boxToInputs.map(b => (b._1, "holdingFailure"))
      _failed = failedMap
    }else{
      _completed = boxToHolding.map(b => (b._1, b._2.getId.toString))
      _boxes = boxToHolding.map(b => (b._1, b._2))
    }
    this
  }

  override def completed: Map[MetadataInputBox, String] = _completed

  override def failed: Map[MetadataInputBox, String] = _failed

  def boxes: Map[MetadataInputBox, InputBox] = _boxes


  /**
   * Creates tx to collect initial boxes and use them in subsequent transactions
   */
  def collectBoxes: Map[MetadataInputBox, InputBox] = {
    val txB = ctx.newTxBuilder()
    var totalFees = 0L
    var boxToOutputs = Map.empty[MetadataInputBox, OutBox]
    var boxToCollectedInputs = Map.empty[MetadataInputBox, InputBox]

    for(boxVal <- boxToValue){
      val initBox = txB.outBoxBuilder().value(boxVal._2 + STANDARD_FEE).contract(new ErgoTreeContract(address.getErgoAddress.script, address.getNetworkType)).build()
      totalFees = totalFees + boxVal._2  + STANDARD_FEE
      boxToOutputs = boxToOutputs++Map((boxVal._1, initBox))
    }
    val initOutputs = boxToOutputs.values.toArray
    var collectedInputBoxes = ctx.getWallet.getUnspentBoxes(totalFees + STANDARD_FEE).get()

    logger.info("Total Fees: " + totalFees)
    logger.info("Total Fee Output Size: " + initOutputs.size)
    logger.info("Total Fee Tx Input Size: " + collectedInputBoxes.size)
    logger.info("Total Fee Tx Input Val: " + BoxHelpers.sumBoxes(collectedInputBoxes.asScala.toList))
    logger.info("UnsignedTx Now building")

    val unsignedTx = txB.boxesToSpend(collectedInputBoxes).fee(STANDARD_FEE).outputs(initOutputs:_*).sendChangeTo(address.getErgoAddress).build()
    val signedTx = prover.sign(unsignedTx)

    logger.info("Collection Tx signed")
    val txId = ctx.sendTransaction(signedTx)
    logger.info(s"Tx sent with fee: $txId and cost: ${signedTx.getCost}")

    var inputsAdded = Array[InputBox]()
    val collectedInputs = signedTx.getOutputsToSpend

    for(box <- boxToValue.keys){
      val outBox = boxToOutputs(box)
      val collectionVal = outBox.getValue
      val collectedBox = collectedInputs.asScala.filter(fb => fb.getValue == collectionVal && !(inputsAdded.exists(ib => ib.getId == fb.getId))).head
      inputsAdded = inputsAdded ++ Array(collectedBox)
      logger.info("collectedBoxId: " + collectedBox.getId)
      logger.info("Inputs Added: " + inputsAdded.length)
      boxToCollectedInputs = boxToCollectedInputs ++ Map((box, collectedBox))
    }
    logger.info("Collected Box Inputs: " + boxToCollectedInputs.size)
    Thread.sleep(500)
    boxToCollectedInputs
  }

  def removeFromMaps(metadataInputBox: MetadataInputBox): Unit = {
    boxToShare = boxToShare--List(metadataInputBox)
    boxToMember = boxToMember--List(metadataInputBox)
    boxToValue = boxToValue--List(metadataInputBox)
    boxToHolding = boxToHolding--List(metadataInputBox)
    boxToInputs = boxToInputs--List(metadataInputBox)
    boxToExact = boxToExact--List(metadataInputBox)

  }

}