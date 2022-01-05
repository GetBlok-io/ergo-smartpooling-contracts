package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.{BlockEntry, ConsensusEntry}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class BlockUpdate(dbConn: DatabaseConnection) extends DatabaseWrite[BlockEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """UPDATE blocks SET status = ? WHERE poolid = ? AND status = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(blockEntry: BlockEntry): DatabaseWrite[BlockEntry] = {

    asStatement.setString(1, blockEntry.statusNew)
    asStatement.setString(2, blockEntry.poolId)
    asStatement.setString(3, blockEntry.statusOld)

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
