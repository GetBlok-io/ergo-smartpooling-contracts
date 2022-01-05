package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.{ConsensusResponse, SmartPoolResponse}

import java.sql.PreparedStatement

class SmartPoolDeletion(dbConn: DatabaseConnection) extends DatabaseWrite[SmartPoolResponse](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """DELETE FROM smartpool_data WHERE poolid = ? AND epoch = ? AND height = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(smartPoolResponse: SmartPoolResponse): DatabaseWrite[SmartPoolResponse] = {

    asStatement.setString(1, smartPoolResponse.poolId)
    asStatement.setLong(2, smartPoolResponse.epoch)
    asStatement.setLong(3, smartPoolResponse.height)
    this
  }

  override def execute(): Long = {

    val rowsDeleted = asStatement.executeUpdate()
    asStatement.close()

    rowsDeleted
  }
}
