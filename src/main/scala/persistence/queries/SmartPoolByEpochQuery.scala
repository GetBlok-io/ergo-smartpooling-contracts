package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.SmartPoolResponse

import java.sql.PreparedStatement


class SmartPoolByEpochQuery(dbConn: DatabaseConnection, poolId: String, epoch: Long) extends DatabaseQuery[Array[SmartPoolResponse]](dbConn) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override val queryString: String =
    """SELECT * FROM smartpool_data WHERE poolid = ? AND epoch = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[Array[SmartPoolResponse]] = {
    asStatement.setString(1, poolId)
    asStatement.setLong(2, epoch)
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
        rt.getLong(4)
      ))
    }
    logger.info(s"SmartPoolByEpoch query with ${_response.length} rows")
    this
  }

  override def getResponse: Array[SmartPoolResponse] = _response
}
