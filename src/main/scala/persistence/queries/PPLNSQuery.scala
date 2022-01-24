package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.ShareResponse

import java.sql.{Date, PreparedStatement, ResultSet}

// Query the last N shares created before a certain date. Used
class PPLNSQuery(dbConn: DatabaseConnection, poolId: String, blockHeight: Long, numShares: Int, offset: Int) extends DatabaseQuery[Array[ShareResponse]](dbConn) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override val queryString: String =
    """SELECT * FROM shares WHERE poolid = ? AND blockheight <= ? ORDER BY created DESC FETCH NEXT ? ROWS ONLY OFFSET ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[Array[ShareResponse]] = {
    asStatement.setString(1, poolId)
    asStatement.setLong(2, blockHeight)
    asStatement.setInt(3, numShares)
    asStatement.setInt(4, offset)
    this
  }

  private var _response: Array[ShareResponse] = Array[ShareResponse]()

  override def execute(): DatabaseQuery[Array[ShareResponse]] = {

    rt = asStatement.executeQuery()
    var newResponse = Array[ShareResponse]()
    while(rt.next()){
      val asShareResponse =
        ShareResponse(
          rt.getString(1), rt.getLong(2), rt.getBigDecimal(3),
          rt.getBigDecimal(4), rt.getString(5),
          rt.getString(9), rt.getDate(10)
        )

      newResponse = newResponse++Array(asShareResponse)
    }
    logger.info(s"PPLNS Response with ${newResponse.length} shares")

    _response = newResponse
    this
  }

  override def getResponse: Array[ShareResponse] = _response
}
