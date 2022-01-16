package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.SmartPoolResponse

import java.sql.PreparedStatement


class SmartPoolBySubpoolQuery(dbConn: DatabaseConnection, poolId: String, subpoolId: String) extends DatabaseQuery[Array[SmartPoolResponse]](dbConn) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override val queryString: String =
    """SELECT * FROM smartpool_data WHERE poolid = ? AND subpool_id = ? ORDER BY created DESC FETCH NEXT 1 ROW ONLY""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[Array[SmartPoolResponse]] = {
    asStatement.setString(1, poolId)
    asStatement.setString(2, subpoolId)
    this
  }

  private var _response: Array[SmartPoolResponse] = Array[SmartPoolResponse]()

  override def execute(): DatabaseQuery[Array[SmartPoolResponse]] = {
    logger.info("Executing Query")
    rt = asStatement.executeQuery()

    while(rt.next()) {
      logger.info("Response was found for smartpool query!")
      _response = _response++Array(SmartPoolResponse(
        rt.getString(1),
        rt.getString(2),
        rt.getLong(3),
        rt.getLong(4),
        rt.getString(9)
      ))
    }
    logger.info(s"SmartPoolByEpoch query with ${_response.length} rows")
    this
  }

  override def getResponse: Array[SmartPoolResponse] = _response
}
