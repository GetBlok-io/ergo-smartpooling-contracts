package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.ConsensusEntry
import persistence.responses.{ConsensusResponse, SmartPoolResponse}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class ConsensusDeletion(dbConn: DatabaseConnection) extends DatabaseWrite[SmartPoolResponse](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """DELETE FROM consensus WHERE poolid = ? AND epoch = ? AND height = ? AND smartpoolnft = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(smartpoolResponses: SmartPoolResponse): DatabaseWrite[SmartPoolResponse] = {

    asStatement.setString(1, smartpoolResponses.poolId)
    asStatement.setLong(2, smartpoolResponses.epoch)
    asStatement.setLong(3, smartpoolResponses.height)
    asStatement.setString(4, smartpoolResponses.smartpoolNFT)
    this
  }

  override def execute(): Long = {

    val rowsDeleted = asStatement.executeUpdate()
    asStatement.close()

    rowsDeleted
  }
}
