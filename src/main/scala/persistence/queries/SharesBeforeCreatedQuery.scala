package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.ShareResponse

import java.sql.PreparedStatement
import java.time.{LocalDateTime, ZoneId}
import java.util.Date

// Query the last N shares created before a certain date. Used
class SharesBeforeCreatedQuery(dbConn: DatabaseConnection, poolId: String, created: Date) extends DatabaseQuery[Array[ShareResponse]](dbConn) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override val queryString: String =
    """SELECT * FROM shares WHERE poolid = ? AND created < ? FETCH NEXT ? """.stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[Array[ShareResponse]] = {
    asStatement.setString(1, poolId)
    asStatement.setObject(2, LocalDateTime.ofInstant(created.toInstant, ZoneId.systemDefault()))

    this
  }

  private var _response: Array[ShareResponse] = Array[ShareResponse]()

  override def execute(): DatabaseQuery[Array[ShareResponse]] = {

    rt = asStatement.executeQuery()
    var newResponse = Array[ShareResponse]()
    while(rt.next()){
      val asShareResponse = {
        ShareResponse(
          rt.getString(1), rt.getLong(2), rt.getBigDecimal(3),
          rt.getBigDecimal(4),rt.getString(5), rt.getString(6),rt.getString(7),
          rt.getString(8), rt.getString(9), rt.getObject(10, classOf[Date])
        )

      }

      newResponse = newResponse++Array(asShareResponse)
    }
    logger.info(s"Share Response with ${newResponse.length} shares")

    _response = newResponse
    this
  }

  override def getResponse: Array[ShareResponse] = _response
}
