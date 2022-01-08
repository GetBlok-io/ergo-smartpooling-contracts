package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.SmartPoolResponse

import java.sql.PreparedStatement

class SmartPoolDeletionByNFT(dbConn: DatabaseConnection) extends DatabaseWrite[SmartPoolResponse](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """DELETE FROM smartpool_data WHERE poolid = ? AND smartpoolnft != ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(smartPoolResponse: SmartPoolResponse): DatabaseWrite[SmartPoolResponse] = {

    asStatement.setString(1, smartPoolResponse.poolId)
    asStatement.setString(2, smartPoolResponse.smartpoolNFT)
    this
  }

  override def execute(): Long = {
    logger.info("Now executing deletionByNFT")
    val rowsDeleted = asStatement.executeUpdate()
    asStatement.close()
    logger.info(s"$rowsDeleted rows deleted from smartpool_data table")
    rowsDeleted
  }
}
