package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.SmartPoolEntry
import persistence.responses.ShareResponse

import java.sql.PreparedStatement
import java.time.{LocalDateTime, ZoneId}

class SharesArchiveInsertion(dbConn: DatabaseConnection, querySize: Int, offset: Int) extends DatabaseWrite[ShareResponse](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """INSERT INTO
      | shares_archive SELECT * FROM shares WHERE poolid = ? AND created < ? FETCH NEXT ? ROWS ONLY OFFSET ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(shareResponse: ShareResponse): DatabaseWrite[ShareResponse] = {

    asStatement.setString(1, shareResponse.poolId)

    asStatement.setObject(2, LocalDateTime.ofInstant(shareResponse.created.toInstant, ZoneId.systemDefault()))
    asStatement.setInt(3, querySize)
    asStatement.setInt(4, offset)
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
