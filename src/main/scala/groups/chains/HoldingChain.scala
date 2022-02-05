package groups.chains

import boxes.MetadataInputBox
import config.SmartPoolConfig
import contracts.holding.HoldingContract
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

class HoldingChain(ctx: BlockchainContext, boxToValue: Map[MetadataInputBox, (Long, Long)], prover: ErgoProver, address: Address,
                   feeValue: Long, feeBoxes:List[InputBox], holdingContract: HoldingContract, config: SmartPoolConfig,
                   ){

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_HOLD_GRP)
  private[this] var _completed = Map.empty[MetadataInputBox, (Long, Long)]
  private[this] var _failed = Map.empty[MetadataInputBox, (Long, Long)]
  private[this] var _config: SmartPoolConfig = _

  private val cmdConf = config.getParameters.getCommandConf
  private val metaConf = config.getParameters.getMetaConf
  private val holdConf = config.getParameters.getHoldingConf

  private var boxToHolding = Map.empty[MetadataInputBox, InputBox]


  def executeChain: HoldingChain = {
    // TODO: Add Try / Catch
    val sendHoldingToOutputs = ctx.newTxBuilder()
    sendHoldingToOutputs
      .boxesToSpend(feeBoxes.asJava)
      .fee(feeValue * feeBoxes.length)
      .sendChangeTo(address.getErgoAddress)
    var holdingOutputs = List[OutBox]()
    boxToValue.foreach{
      bV =>
        val boxBuilder = sendHoldingToOutputs.outBoxBuilder()
        val outBox = boxBuilder.value(bV._2._1).contract(holdingContract.asErgoContract).build()
        holdingOutputs = holdingOutputs ++ List(outBox)
        logger.info("Adding new holding value to outputs: " + bV._2._1)
    }
    sendHoldingToOutputs.outputs(holdingOutputs:_*)


    val unsignedTx = sendHoldingToOutputs.build()

    logger.info("Now signing holding distribution")
    val signedTx = prover.sign(unsignedTx)
    logger.info("Holding Tx signed")

    for(bV <- boxToValue) {
      logger.info("Searching for holding box for metabox " + bV._1.getId + " and value " + bV._2._1)
      val newHoldingBox = signedTx.getOutputsToSpend.asScala.filter(i => i.getValue == bV._2._1 && !boxToHolding.values.toArray.contains(i)).head
      signedTx.getOutputsToSpend.asScala.foreach(c => logger.info("Regroup Output: " + c.getId + " With Val " + c.getValue))
      logger.info("New holding box for metabox " + newHoldingBox.getId + " and value " + newHoldingBox.getValue)
      boxToHolding = boxToHolding++Map((bV._1, newHoldingBox))
    }
    val txId = ctx.sendTransaction(signedTx)
    logger.info(s"Tx sent with txId $txId and cost ${signedTx.getCost}")
    _completed = boxToValue

    this
  }
  def result: Map[MetadataInputBox, InputBox] = boxToHolding
  def completed: Map[MetadataInputBox, (Long, Long)] = _completed

  def failed: Map[MetadataInputBox, (Long, Long)] = _failed



}