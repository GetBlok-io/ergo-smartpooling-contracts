package groups.chains

import boxes.MetadataInputBox
import configs.SmartPoolConfig
import contracts.holding.HoldingContract
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

class HoldingChain2(ctx: BlockchainContext, boxToValue: Map[MetadataInputBox, Long], prover: ErgoProver, address: Address,
                    feeValue: Long, inputBoxes:List[InputBox], holdingContract: HoldingContract){

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_HOLD_CHAIN)
  private[this] var _completed = Map.empty[MetadataInputBox, Long]

  private var boxToHolding = Map.empty[MetadataInputBox, InputBox]


  def executeChain: HoldingChain2 = {

    val sendHoldingToOutputs = ctx.newTxBuilder()
    sendHoldingToOutputs
      .boxesToSpend(inputBoxes.asJava)
      .fee(feeValue * inputBoxes.length)
      .sendChangeTo(address.getErgoAddress)
    var holdingOutputs = List[OutBox]()

    boxToValue.foreach{
      bV =>
        val boxBuilder = sendHoldingToOutputs.outBoxBuilder()
        val outBox = boxBuilder.value(bV._2).contract(holdingContract.asErgoContract).build()
        holdingOutputs = holdingOutputs ++ List(outBox)
        logger.info("Adding new holding value to outputs: " + bV._2)
    }

    sendHoldingToOutputs.outputs(holdingOutputs:_*)
    val unsignedTx = sendHoldingToOutputs.build()

    logger.info("Now signing holding distribution")
    val signedTx = prover.sign(unsignedTx)
    logger.info("Holding Tx signed")

    for(bV <- boxToValue) {
      logger.info("Searching for holding box for subpool " + bV._1.getSubpoolId + " with value " + bV._2)
      val newHoldingBox = signedTx.getOutputsToSpend.asScala.filter(i => i.getValue == bV._2 && !boxToHolding.values.toArray.exists(h => h.getId == i.getId)).head
      signedTx.getOutputsToSpend.asScala.foreach(c => logger.info("Holding Output: " + c.getId + " With Val " + c.getValue))
      logger.info(s"New holding box for subpool ${bV._1.getSubpoolId} with id " + newHoldingBox.getId + " and value " + newHoldingBox.getValue)
      boxToHolding = boxToHolding++Map((bV._1, newHoldingBox))
      _completed = _completed ++ Map(bV)
    }
    val txId = ctx.sendTransaction(signedTx)
    logger.info(s"Tx sent with txId $txId and cost ${signedTx.getCost}")
    this
  }

  def result: Map[MetadataInputBox, InputBox] = boxToHolding
  def completed: Map[MetadataInputBox, Long] = _completed




}