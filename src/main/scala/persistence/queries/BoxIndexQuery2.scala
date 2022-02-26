package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.models.Models.BoxEntry
import persistence.responses.BoxIndexResponse

import java.sql.PreparedStatement


class BoxIndexQuery2(dbConn: DatabaseConnection) extends DatabaseQuery[Array[BoxEntry]](dbConn) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override val queryString: String =
    """SELECT * FROM box_index""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[Array[BoxEntry]] = {
    this
  }

  private var _response: Array[BoxEntry] = Array[BoxEntry]()

  override def execute(): DatabaseQuery[Array[BoxEntry]] = {
    logger.info("Executing Query")
    rt = asStatement.executeQuery()

    while(rt.next()) {
      //logger.info("Response was found for BoxIndex query!")
      _response = _response++Array(BoxEntry(
        rt.getString(1),
        rt.getString(2),
        rt.getString(3),
        rt.getLong(4),
        rt.getString(5),
        rt.getString(6),
        rt.getString(7).toInt,
        rt.getArray(8).getArray.asInstanceOf[Array[AnyRef]].map(a => a.asInstanceOf[Long]),
        rt.getString(9),
        rt.getLong(10),
        rt.getString(11),
        rt.getLong(12)
      ))
    }
    logger.info(s"BoxIndex query with ${_response.length} rows")
    this
  }

  override def getResponse: Array[BoxEntry] = _response
}
