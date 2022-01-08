package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.{ConsensusResponse, SmartPoolResponse}

import java.sql.PreparedStatement


class ConsensusByTransactionQuery(dbConn: DatabaseConnection, poolId: String, transaction: String) extends DatabaseQuery[Array[ConsensusResponse]](dbConn) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override val queryString: String =
    """SELECT * FROM consensus WHERE poolid = ? AND transactionhash = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[Array[ConsensusResponse]] = {
    asStatement.setString(1, poolId)
    asStatement.setString(2, transaction)
    this
  }

  private var _response: Array[ConsensusResponse] = Array[ConsensusResponse]()

  override def execute(): DatabaseQuery[Array[ConsensusResponse]] = {
    logger.info("Executing Query")
    rt = asStatement.executeQuery()

    while(rt.next()) {
      logger.info("Response was found for consensus query!")
      _response = _response++Array(ConsensusResponse(
        rt.getString(1),
        rt.getString(2),
        rt.getString(5),
        rt.getString(6),
        rt.getLong(7),
        rt.getLong(8),
        rt.getLong(9),
        rt.getLong(11),
      ))
    }
    logger.info(s"ConsensusByTransaction query with ${_response.length} rows")
    this
  }

  override def getResponse: Array[ConsensusResponse] = _response
}
