package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.{BlockResponse, ShareResponse}

import java.sql.{Date, PreparedStatement}

class BlockByHeightQuery(dbConn: DatabaseConnection, poolId: String, blockheight: Long) extends DatabaseQuery[BlockResponse](dbConn) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override val queryString: String =
    """SELECT * FROM blocks WHERE poolid = ? AND blockheight = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[BlockResponse] = {
    asStatement.setString(1, poolId)
    asStatement.setLong(2, blockheight)
    this
  }

  private var _response: BlockResponse = _

  override def execute(): DatabaseQuery[BlockResponse] = {
    logger.info("Executing Query")
    rt = asStatement.executeQuery()

    if(rt.next()) {
      logger.info("Response was found for block query!")
      _response = BlockResponse(
        rt.getLong(1), rt.getString(2), rt.getLong(3),
        rt.getDouble(4), rt.getString(5), rt.getDouble(7),
        rt.getString(10), rt.getDouble(11), rt.getDate(14)
      )
    }

    this
  }

  override def getResponse: BlockResponse = _response
}
