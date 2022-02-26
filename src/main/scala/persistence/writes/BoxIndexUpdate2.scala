package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.BoxIndexEntry
import persistence.models.Models.BoxEntry

import java.sql.PreparedStatement

class BoxIndexUpdate2(dbConn: DatabaseConnection) extends DatabaseWrite[BoxEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  val holdingFields: String = "holding_id = ?, holding_value = ?, stored_id = ?, stored_value = ?"

  override val insertionString: String =
    s"""UPDATE box_index SET boxid = ?, txid = ?, epoch = ?, status = ?, blocks = ?, $holdingFields WHERE poolid = ? AND subpool_id = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(boxEntry: BoxEntry): DatabaseWrite[BoxEntry] = {

    val blocksArray = dbConn.asConnection.createArrayOf("bigint",boxEntry.blocks.map(_.asInstanceOf[AnyRef]))


    asStatement.setString(1, boxEntry.boxId)
    asStatement.setString(2, boxEntry.txId)
    asStatement.setLong(3, boxEntry.epoch)
    asStatement.setString(4, boxEntry.status)

    asStatement.setArray(5, blocksArray)
    asStatement.setString(6, boxEntry.holdingId)
    asStatement.setLong(7, boxEntry.holdingVal)

    asStatement.setString(8, boxEntry.storedId)
    asStatement.setLong(9, boxEntry.storedVal)

    asStatement.setString(10, boxEntry.poolId)
    asStatement.setString(11, boxEntry.idTag)

    this
  }

  override def execute(): Long = {

    val rowsInserted = asStatement.executeUpdate()
    asStatement.close()

    rowsInserted
  }
}
