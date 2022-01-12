package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.{BoxIndexEntry, SmartPoolEntry}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class BoxIndexUpdate(dbConn: DatabaseConnection) extends DatabaseWrite[BoxIndexEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """UPDATE box_index SET boxid = ?, txid = ?, epoch = ?, status = ?, blocks = ?  WHERE poolid = ? AND subpool_id = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(boxIndexEntry: BoxIndexEntry): DatabaseWrite[BoxIndexEntry] = {

    val blocksArray = dbConn.asConnection.createArrayOf("bigint",boxIndexEntry.blocks.map(_.asInstanceOf[AnyRef]))


    asStatement.setString(1, boxIndexEntry.boxId)
    asStatement.setString(2, boxIndexEntry.txId)
    asStatement.setLong(3, boxIndexEntry.epoch)
    asStatement.setString(4, boxIndexEntry.status)

    asStatement.setArray(5, blocksArray)
    asStatement.setString(6, boxIndexEntry.poolId)
    asStatement.setString(7, boxIndexEntry.subpoolId)


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
