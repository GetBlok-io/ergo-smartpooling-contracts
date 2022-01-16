package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.{ConsensusResponse, SmartPoolResponse}

import java.sql.PreparedStatement

class SmartPoolDeletion(dbConn: DatabaseConnection) extends DatabaseWrite[SmartPoolResponse](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """DELETE FROM smartpool_data WHERE poolid = ? AND transactionhash = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(smartPoolResponse: SmartPoolResponse): DatabaseWrite[SmartPoolResponse] = {

    asStatement.setString(1, smartPoolResponse.poolId)
    asStatement.setString(2, smartPoolResponse.transactionHash)

    this
  }

  override def execute(): Long = {
    logger.info("Now deleting smartpool entry")
    val rowsDeleted = asStatement.executeUpdate()
    asStatement.close()
    logger.info(s"$rowsDeleted rows deleted")
    rowsDeleted
  }
}
