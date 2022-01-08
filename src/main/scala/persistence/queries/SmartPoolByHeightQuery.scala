package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.{SettingsResponse, SmartPoolResponse}

import java.sql.PreparedStatement

// Query for minimum payouts to input into command box
// TODO: Finish this query for usage in mainnet. Currently not necessary for testnet
class SmartPoolByHeightQuery(dbConn: DatabaseConnection, poolId: String, height: Long) extends DatabaseQuery[SmartPoolResponse](dbConn) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override val queryString: String =
    """SELECT * FROM smartpool_data WHERE poolid = ? AND height = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[SmartPoolResponse] = {
    asStatement.setString(1, poolId)
    asStatement.setLong(2, height)
    this
  }

  private var _response: SmartPoolResponse = _

  override def execute(): DatabaseQuery[SmartPoolResponse] = {
    logger.info("Executing Query")
    rt = asStatement.executeQuery()

    if(rt.next()) {
      logger.info("Response was found for smartpool query!")
      _response = SmartPoolResponse(
        rt.getString(1),
        rt.getString(2),
        rt.getLong(3),
        rt.getLong(4),
        rt.getString(9)
      )
    }

    this
  }

  override def getResponse: SmartPoolResponse = _response
}
