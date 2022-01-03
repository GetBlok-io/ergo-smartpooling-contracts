package boxes

import logging.LoggingHandler
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoId, InputBox}
import org.slf4j.{Logger, LoggerFactory}
import sigmastate.lang.exceptions.InvalidArguments

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
 * Object to help retrieve and find correct boxes for using in transactions
 */
object BoxHelpers {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DISTRIBUTE_REWARDS_CMD)
  final val BOX_SELECTOR_LIMIT = 20

  /**
   * Linear search to select metadata box using given parameters
   */
  def selectCurrentMetadata(ctx: BlockchainContext, smartPoolId: ErgoId, metadataAddress: Address, metadataValue: Long): MetadataInputBox = {
    logger.info("Searching for metadata box...")
    var offset = 0
    var boxesToSearch = ctx.getUnspentBoxesFor(metadataAddress, offset, BOX_SELECTOR_LIMIT)
    if(boxesToSearch.size() > 0) {
      var metadataInputBox = boxesToSearch.get(0)
      while (boxesToSearch.asScala.nonEmpty) {
        for (box <- boxesToSearch.asScala) {
          if (box.getValue == metadataValue) {
            if (box.getTokens.asScala.nonEmpty){
              val boxTokens = box.getTokens
              if(boxTokens.get(0).getId.toString == smartPoolId.toString && boxTokens.get(0).getValue == 1){
                logger.info("Metadata candidate found!")
                return new MetadataInputBox(box, smartPoolId)
              }
            }
          }
        }
        offset = offset + boxesToSearch.size()
        boxesToSearch = ctx.getUnspentBoxesFor(metadataAddress, offset, BOX_SELECTOR_LIMIT)
      }
      new MetadataInputBox(metadataInputBox, smartPoolId)
    }else{
      throw new InvalidArguments("Could not find any box with given parameters!")
    }
  }

  def minBox(boxOne: InputBox, boxTwo: InputBox): InputBox ={
    if(boxOne.getValue <= boxTwo.getValue){
      boxOne
    }else{
      boxTwo
    }
  }
  def maxBox(boxOne: InputBox, boxTwo: InputBox): InputBox ={
    if(boxOne.getValue >= boxTwo.getValue){
      boxOne
    }else{
      boxTwo
    }
  }
  def sumBoxes(boxList: List[InputBox]): Long ={
    boxList.map{b => b.getValue.toLong}.sum
  }


  def findIdealHoldingBoxes(ctx: BlockchainContext, holdingAddress: Address, holdingValue: Long, storedPaymentValue: Long): List[InputBox] ={
    logger.info("Searching for ideal holding boxes...")
    var offset = 0
    var boxesToSearch = ctx.getUnspentBoxesFor(holdingAddress, offset, BOX_SELECTOR_LIMIT)
    var accumulatedValue = 0L
    var boxesToReturn = List[InputBox]()
    var storedPaymentBoxes = List[InputBox]()
    if(boxesToSearch.size() > 0){
      while(boxesToSearch.asScala.nonEmpty){
        for(box <- boxesToSearch.asScala){
          // Get exact box for stored payment, which we know must exist as long as stored payment != 0
          if(storedPaymentValue != 0 && box.getValue == storedPaymentValue && storedPaymentBoxes.isEmpty){
            logger.info("Stored Payment Box found!")
            accumulatedValue = accumulatedValue + box.getValue
            storedPaymentBoxes = List(box)
          }
          // Try to get exact box for holding value
          if(box.getValue == holdingValue){
            logger.info("Exact holding box found!")
            accumulatedValue = accumulatedValue + box.getValue
            boxesToReturn = List(box)
          }else{

           if(boxesToReturn.nonEmpty){
              // If we have exact values, lets return them
              if(sumBoxes(boxesToReturn) == holdingValue && (storedPaymentValue == 0 || sumBoxes(storedPaymentBoxes) == storedPaymentValue)){
                logger.info("Exact holding input boxes found!")
                return boxesToReturn++storedPaymentBoxes
              }else{
                // If we're still less than holding value, then append
                if(sumBoxes(boxesToReturn) < holdingValue){
                  boxesToReturn = boxesToReturn++List(box)
                  accumulatedValue = accumulatedValue + box.getValue
                }else if(sumBoxes(boxesToReturn) > holdingValue){
                  // If our sumBoxes is > holding value, lets find the ideal box list
                  // by finding the list with the minimum holding value that is still >= the actual holding value
                  val potentialBoxes = for(potentialBox <- boxesToReturn) yield boxesToReturn.updated(boxesToReturn.indexOf(potentialBox, 0), box)
                  var boxListToUse = boxesToReturn
                  potentialBoxes.foreach{
                    pb =>
                      if(sumBoxes(pb) < sumBoxes(boxListToUse) && sumBoxes(pb) >= holdingValue){
                        boxListToUse = pb
                      }
                  }
                  boxesToReturn = boxListToUse
                  accumulatedValue = sumBoxes(boxesToReturn)+sumBoxes(storedPaymentBoxes)
                }
              }
             logger.info(s"Current accumulated value for ideal holding boxes: $accumulatedValue")
           }else{
             // If our box list is currently empty, lets set it to be just the first box we find
             boxesToReturn = List(box)
             accumulatedValue = accumulatedValue + box.getValue
           }
          }
        }
        offset = boxesToSearch.size
        boxesToSearch = ctx.getUnspentBoxesFor(holdingAddress, offset, BOX_SELECTOR_LIMIT)
      }
      boxesToReturn++storedPaymentBoxes
    }else{
      boxesToReturn++storedPaymentBoxes
    }


  }

}
