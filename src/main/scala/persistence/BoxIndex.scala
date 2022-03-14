package persistence

import boxes.MetadataInputBox
import logging.LoggingHandler
import org.ergoplatform.appkit.{BlockchainContext, ErgoId, InputBox}
import org.slf4j.{Logger, LoggerFactory}
import persistence.entries.BoxIndexEntry
import persistence.models.Models
import persistence.models.Models.{BoxEntry, BoxStatus}
import persistence.queries.{BoxIndexQuery, BoxIndexQuery2}
import persistence.writes.{BoxIndexInsertion, BoxIndexInsertion2, BoxIndexUpdate, BoxIndexUpdate2}

class BoxIndex(dbConn: DatabaseConnection, poolId: String, boxEntries: Array[BoxEntry]) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  var boxes: Map[Int, BoxEntry] = boxEntries.map(b => (b.subpoolId, b)).toMap
  private var metadataInputs: Array[MetadataInputBox] = _

  def getSuccessful: BoxIndex = new BoxIndex(dbConn, poolId, boxes.filter(b => b._2.boxStatus == BoxStatus.SUCCESS).values.toArray)
  def getFailed: BoxIndex = new BoxIndex(dbConn, poolId, boxes.filter(b => b._2.boxStatus == BoxStatus.FAILURE).values.toArray)
  def getConfirmed: BoxIndex = new BoxIndex(dbConn, poolId, boxes.filter(b => b._2.boxStatus == BoxStatus.CONFIRMED).values.toArray)
  def getInitiated: BoxIndex = new BoxIndex(dbConn, poolId, boxes.filter(b => b._2.boxStatus == BoxStatus.INITIATED).values.toArray)
  def getReserved: BoxIndex = new BoxIndex(dbConn, poolId, boxes.filter(b => b._2.boxStatus == BoxStatus.RESERVED).values.toArray)
  def getUsed: BoxIndex = new BoxIndex(dbConn, poolId, boxes.filter(b => b._2.epoch > 0).values.toArray)
  def getNew: BoxIndex = new BoxIndex(dbConn, poolId, boxes.filter(b => b._2.epoch == 0).values.toArray)
  def getByBlock(height: Long) = new BoxIndex(dbConn, poolId, boxes.filter(b => b._2.blocks.contains(height)).values.toArray)
  def getSpecial: BoxIndex = new BoxIndex(dbConn, poolId, boxes.filter(b => b._1 >= 90).values.toArray)
  def getNormal: BoxIndex = new BoxIndex(dbConn, poolId, boxes.filter(b => b._1 < 90).values.toArray)

  def apply(id: Int): BoxEntry = boxes(id)
  def size: Int = this.boxes.size
  def head: (Int, BoxEntry) = this.boxes.head

  def grabFromContext(ctx: BlockchainContext): Array[MetadataInputBox] = {
    if(metadataInputs == null) {
      metadataInputs = boxes.map(b => b._2.grabFromContext(ctx).get).toArray.sortBy(m => m.getSubpoolId)
    }
    metadataInputs
  }

  def getHoldingBoxes(ctx: BlockchainContext): Map[MetadataInputBox, InputBox] = {
    if(metadataInputs == null)
      boxes.filter(b => b._2.holdingVal > 0).map(b => (b._2.grabFromContext(ctx).get, b._2.holdingFromContext(ctx).get))
    else
      metadataInputs.filter(b => this(b.getSubpoolId.toInt).holdingVal > 0).map(b => (b, this(b.getSubpoolId.toInt).holdingFromContext(ctx).get)).toMap
  }

  def getStorageBoxes(ctx: BlockchainContext): Map[MetadataInputBox, InputBox] = {
    if(metadataInputs == null) {
      boxes.filter(b => b._2.storedVal > 0).map(b => (b._2.grabFromContext(ctx).get, b._2.storageFromContext(ctx).get))
    }else{
      metadataInputs.filter(b => this(b.getSubpoolId.toInt).storedVal > 0).map(b => (b, this(b.getSubpoolId.toInt).storageFromContext(ctx).get)).toMap
    }
  }

  def writeFailures(failed: Map[MetadataInputBox, String], blockHeights: Array[Long]): Long = {
    var numBoxes = 0L
    for(boxPair <- failed){
      val metadataInputBox = boxPair._1
      val entry = this(metadataInputBox.getSubpoolId.toInt)
      val boxIndexEntry = BoxEntry(poolId, entry.boxId, boxPair._2,
        metadataInputBox.getCurrentEpoch, BoxStatus.FAILURE.toString, entry.smartPoolNft,
        entry.subpoolId, blockHeights, entry.holdingId, entry.holdingVal, entry.storedId, entry.storedVal)
      val rowsUpdated = new BoxIndexUpdate2(dbConn).setVariables(boxIndexEntry).execute()
      if(rowsUpdated > 0){
        numBoxes = numBoxes + rowsUpdated
        boxes = boxes.updated(entry.subpoolId, entry)
      }
    }
    logger.warn(s"$numBoxes subpools had failures")
    failed.keys.foreach(
      m =>
        logger.info(s"Subpool ${m.getSubpoolId} has status ${BoxStatus.FAILURE.toString.toUpperCase}")
    )
    numBoxes
  }

  def writeSuccessful(completed: Map[MetadataInputBox, String], blockHeights: Array[Long]): Long = {
    var numBoxes = 0L
    for(boxPair <- completed){
      val metadataInputBox = boxPair._1
      val entry = this(metadataInputBox.getSubpoolId.toInt)

      val boxIndexEntry = BoxEntry(poolId, entry.boxId, boxPair._2,
        metadataInputBox.getCurrentEpoch, BoxStatus.SUCCESS.toString, metadataInputBox.getSmartPoolId.toString,
        entry.subpoolId, blockHeights, entry.holdingId, entry.holdingVal, entry.storedId, entry.storedVal)
      val rowsUpdated = new BoxIndexUpdate2(dbConn).setVariables(boxIndexEntry).execute()
      if(rowsUpdated > 0){
        numBoxes = numBoxes + rowsUpdated
        boxes = boxes.updated(entry.subpoolId, entry)
      }
    }
    logger.info(s"$numBoxes subpools completed successfully")
    completed.keys.foreach(
      m =>
        logger.info(s"Subpool ${m.getSubpoolId} has status ${BoxStatus.SUCCESS.toString.toUpperCase}")
    )
    numBoxes
  }

  def writeConfirmed(confirmed: Map[MetadataInputBox, InputBox]): Long = {
    var numBoxes = 0L
    for(boxPair <- confirmed){
      val metadataInputBox = boxPair._1
      val entry = this(metadataInputBox.getSubpoolId.toInt)

      val boxIndexEntry = BoxEntry(poolId, metadataInputBox.getId.toString, entry.txId,
        metadataInputBox.getCurrentEpoch, BoxStatus.CONFIRMED.toString, entry.smartPoolNft,
        entry.subpoolId, entry.blocks, BoxEntry.EMPTY, 0L, boxPair._2.getId.toString, boxPair._2.getValue)
      val rowsUpdated = new BoxIndexUpdate2(dbConn).setVariables(boxIndexEntry).execute()
      if(rowsUpdated > 0){
        numBoxes = numBoxes + rowsUpdated
        boxes = boxes.updated(entry.subpoolId, entry)
      }
    }
    logger.info(s"$numBoxes subpools completed successfully")
    confirmed.keys.foreach(
      m =>
        logger.info(s"Subpool ${m.getSubpoolId} has status ${BoxStatus.CONFIRMED.toString.toUpperCase}")
    )
    numBoxes
  }

  def writeEmptyConfirmed(confirmed: Array[MetadataInputBox]): Long = {
    var numBoxes = 0L
    for(box <- confirmed){
      val metadataInputBox = box
      val entry = this(metadataInputBox.getSubpoolId.toInt)

      val boxIndexEntry = BoxEntry(poolId, metadataInputBox.getId.toString, entry.txId,
        metadataInputBox.getCurrentEpoch, BoxStatus.CONFIRMED.toString, entry.smartPoolNft,
        entry.subpoolId, entry.blocks, BoxEntry.EMPTY, 0L, BoxEntry.EMPTY, 0L)
      val rowsUpdated = new BoxIndexUpdate2(dbConn).setVariables(boxIndexEntry).execute()
      if(rowsUpdated > 0){
        numBoxes = numBoxes + rowsUpdated
        boxes = boxes.updated(entry.subpoolId, entry)
      }
    }
    logger.info(s"$numBoxes subpools completed successfully")
    confirmed.foreach(
      m =>
        logger.info(s"Subpool ${m.getSubpoolId} has status ${BoxStatus.CONFIRMED.toString.toUpperCase} with no storage")
    )
    numBoxes
  }

  def writeInitiated(initiated: Map[MetadataInputBox, InputBox], blockHeights: Array[Long]): Long = {
    var numBoxes = 0L
    for(boxPair <- initiated){
      val metadataInputBox = boxPair._1
      val entry = this(metadataInputBox.getSubpoolId.toInt)

      val boxIndexEntry = BoxEntry(poolId, entry.boxId, entry.txId,
        metadataInputBox.getCurrentEpoch, BoxStatus.INITIATED.toString, entry.smartPoolNft,
        entry.subpoolId, blockHeights, boxPair._2.getId.toString, boxPair._2.getValue, entry.storedId, entry.storedVal)
      val rowsUpdated = new BoxIndexUpdate2(dbConn).setVariables(boxIndexEntry).execute()
      if(rowsUpdated > 0){
        numBoxes = numBoxes + rowsUpdated
        boxes = boxes.updated(entry.subpoolId, entry)
      }
    }
    logger.info(s"$numBoxes subpools completed successfully")
    initiated.keys.foreach(
      m =>
        logger.info(s"Subpool ${m.getSubpoolId} has status ${BoxStatus.INITIATED.toString.toUpperCase}")
    )
    numBoxes
  }

  def writeGenerated(inputs: Array[InputBox], txId: String, smartPoolId: ErgoId): Long = {
    var numBoxes = 0L
    val metadataBoxes: Array[MetadataInputBox] = inputs.map(b => new MetadataInputBox(b, smartPoolId))
    for(box <- metadataBoxes){

      val boxIndexEntry = BoxEntry(poolId, box.getId.toString, txId,
        box.getCurrentEpoch, BoxStatus.CONFIRMED.toString, smartPoolId.toString,
        box.getSubpoolId.toInt, Array(0), BoxEntry.EMPTY, BoxEntry.EMPTY_LONG, BoxEntry.EMPTY, BoxEntry.EMPTY_LONG)
      val rowsUpdated = new BoxIndexInsertion2(dbConn).setVariables(boxIndexEntry).execute()
      if(rowsUpdated > 0){
        numBoxes = numBoxes + rowsUpdated
        boxes = boxes ++ Map((box.getSubpoolId.toInt, boxIndexEntry))
      }
    }
    logger.info(s"$numBoxes subpools completed successfully")
    boxes.values.foreach(
      e =>
        logger.info(s"Subpool ${e.subpoolId} has status ${BoxStatus.CONFIRMED.toString.toUpperCase}")
    )
    numBoxes
  }

  /**
   * Sets and updates BoxStatus for all entries in this BoxIndex
   */
  def setIndexStatus(status: BoxStatus): Long = {
    var numBoxes = 0L

    for(entry <- boxes){

      val boxIndexEntry = BoxEntry(entry._2.poolId, entry._2.boxId, entry._2.txId,
        entry._2.epoch, status.toString, entry._2.smartPoolNft,
        entry._1, entry._2.blocks, entry._2.holdingId, entry._2.holdingVal, entry._2.storedId, entry._2.storedVal)
      val rowsUpdated = new BoxIndexInsertion2(dbConn).setVariables(boxIndexEntry).execute()
      if(rowsUpdated > 0){
        numBoxes = numBoxes + rowsUpdated
        boxes = boxes.updated(entry._1, entry._2)
      }
    }
    logger.info(s"$numBoxes subpools completed successfully")
    boxes.values.foreach(
      e =>
        logger.info(s"Subpool ${e.subpoolId} has status ${status.toString.toUpperCase}")
    )
    numBoxes
  }
}

object BoxIndex {
  private val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  def fromDatabase(dbConn: DatabaseConnection, poolId: String): BoxIndex = {
    logger.info("Generating Box Index from database")
    val dbBoxes: Array[BoxEntry] = new BoxIndexQuery2(dbConn).setVariables().execute().getResponse
    new BoxIndex(dbConn, poolId, dbBoxes)
  }
}
