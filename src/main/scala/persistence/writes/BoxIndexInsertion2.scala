package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.BoxIndexEntry
import persistence.models.Models.BoxEntry

import java.sql.PreparedStatement
import java.time.LocalDateTime

class BoxIndexInsertion2(dbConn: DatabaseConnection) extends DatabaseWrite[BoxEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """INSERT INTO
      | box_index (poolid, boxid, txid, epoch, status, smartpoolnft, subpool_id, blocks, holding_id, holding_value, stored_id, stored_value)
      | VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(boxEntry: BoxEntry): DatabaseWrite[BoxEntry] = {

    val blocksArray = dbConn.asConnection.createArrayOf("bigint", boxEntry.blocks.map(_.asInstanceOf[AnyRef]))

    asStatement.setString(1, boxEntry.poolId)
    asStatement.setString(2, boxEntry.boxId)
    asStatement.setString(3, boxEntry.txId)
    asStatement.setLong(4, boxEntry.epoch)
    asStatement.setString(5, boxEntry.status)

    asStatement.setString(6, boxEntry.smartPoolNft)
    asStatement.setString(7, boxEntry.subpoolId.toString)

    asStatement.setArray(8, blocksArray)
    asStatement.setString(9, boxEntry.holdingId)
    asStatement.setLong(10, boxEntry.holdingVal)
    asStatement.setString(11, boxEntry.storedId)
    asStatement.setLong(12, boxEntry.storedVal)
    this
  }

  override def execute(): Long = {
    logger.info("Executing update")
    val rowsInserted = asStatement.executeUpdate()
    asStatement.close()
    logger.info(s"Update executed. ${rowsInserted} Rows inserted into db.")
    rowsInserted
  }
}
