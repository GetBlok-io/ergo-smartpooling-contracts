package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.{ShareResponse, SmartPoolResponse}

import java.sql.PreparedStatement
import java.time.{LocalDateTime, ZoneId}

class SharesBeforeCreatedDeletion(dbConn: DatabaseConnection) extends DatabaseWrite[ShareResponse](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """DELETE FROM shares WHERE poolid = ? AND created < ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(shareResponse: ShareResponse): DatabaseWrite[ShareResponse] = {

    asStatement.setString(1, shareResponse.poolId)
    asStatement.setObject(2, LocalDateTime.ofInstant(shareResponse.created.toInstant, ZoneId.systemDefault()))

    this
  }

  override def execute(): Long = {
    logger.info("Now deleting share entries")
    val rowsDeleted = asStatement.executeUpdate()
    asStatement.close()
    logger.info(s"$rowsDeleted rows deleted")
    rowsDeleted
  }
}
