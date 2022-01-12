package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.{BoxIndexEntry, SmartPoolEntry}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class BoxIndexInsertion(dbConn: DatabaseConnection) extends DatabaseWrite[BoxIndexEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """INSERT INTO
      | box_index (poolid, boxid, txid, epoch, status, smartpoolnft, subpool_id, blocks)
      | VALUES(?, ?, ?, ?, ?, ?, ?, ?);""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(boxIndexEntry: BoxIndexEntry): DatabaseWrite[BoxIndexEntry] = {

    val blocksArray = dbConn.asConnection.createArrayOf("bigint",boxIndexEntry.blocks.map(_.asInstanceOf[AnyRef]))

    val localDateTime = LocalDateTime.now()

    asStatement.setString(1, boxIndexEntry.poolId)
    asStatement.setString(2, boxIndexEntry.boxId)
    asStatement.setString(3, boxIndexEntry.txId)
    asStatement.setLong(4, boxIndexEntry.epoch)
    asStatement.setString(5, boxIndexEntry.status)

    asStatement.setString(6, boxIndexEntry.smartPoolNft)
    asStatement.setString(7, boxIndexEntry.subpoolId)

    asStatement.setArray(8, blocksArray)
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
