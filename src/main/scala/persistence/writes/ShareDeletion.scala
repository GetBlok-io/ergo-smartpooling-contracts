package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.SmartPoolResponse

import java.sql.PreparedStatement

class ShareDeletion(dbConn: DatabaseConnection) extends DatabaseWrite[SmartPoolResponse](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """DELETE FROM shares WHERE poolid = ? AND created < ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(smartpoolResponses: SmartPoolResponse): DatabaseWrite[SmartPoolResponse] = {

    asStatement.setString(1, smartpoolResponses.poolId)
    asStatement.setString(2, smartpoolResponses.transactionHash)

    this
  }

  override def execute(): Long = {
    logger.info("Now deleting failed consensus entries")
    val rowsDeleted = asStatement.executeUpdate()
    asStatement.close()
    logger.info(s"$rowsDeleted rows deleted")
    rowsDeleted
  }
}
