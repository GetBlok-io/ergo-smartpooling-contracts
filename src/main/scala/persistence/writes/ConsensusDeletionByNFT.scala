package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.SmartPoolResponse

import java.sql.PreparedStatement

class ConsensusDeletionByNFT(dbConn: DatabaseConnection) extends DatabaseWrite[(String, String)](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """DELETE FROM consensus WHERE poolid = ? AND smartpoolnft != ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(smartpoolResponses: (String, String)): DatabaseWrite[(String, String)] = {

    asStatement.setString(1, smartpoolResponses._1)
    asStatement.setString(2, smartpoolResponses._2)
    this
  }

  override def execute(): Long = {

    val rowsDeleted = asStatement.executeUpdate()
    asStatement.close()

    rowsDeleted
  }
}
