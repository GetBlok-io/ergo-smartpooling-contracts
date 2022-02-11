package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.BlockEntry

import java.sql.PreparedStatement

class BlockUpdateByHeight(dbConn: DatabaseConnection, height: Long) extends DatabaseWrite[BlockEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """UPDATE blocks SET status = ? WHERE poolid = ? AND blockheight = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(blockEntry: BlockEntry): DatabaseWrite[BlockEntry] = {

    asStatement.setString(1, blockEntry.statusNew)
    asStatement.setString(2, blockEntry.poolId)
    asStatement.setLong(3, height)

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
