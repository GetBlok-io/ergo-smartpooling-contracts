package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.{BoxIndexResponse, SmartPoolResponse}

import java.sql.PreparedStatement


class BoxIndexQuery(dbConn: DatabaseConnection) extends DatabaseQuery[Array[BoxIndexResponse]](dbConn) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override val queryString: String =
    """SELECT * FROM box_index""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[Array[BoxIndexResponse]] = {
    this
  }

  private var _response: Array[BoxIndexResponse] = Array[BoxIndexResponse]()

  override def execute(): DatabaseQuery[Array[BoxIndexResponse]] = {
    logger.info("Executing Query")
    rt = asStatement.executeQuery()

    while(rt.next()) {
      logger.info("Response was found for BoxIndex query!")
      _response = _response++Array(BoxIndexResponse(
        rt.getString(1),
        rt.getString(2),
        rt.getString(3),
        rt.getLong(4),
        rt.getString(5),
        rt.getString(6),
        rt.getString(7)
      ))
    }
    logger.info(s"BoxIndex query with ${_response.length} rows")
    this
  }

  override def getResponse: Array[BoxIndexResponse] = _response
}
