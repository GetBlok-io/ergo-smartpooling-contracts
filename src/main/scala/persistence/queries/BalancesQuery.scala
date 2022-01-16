package persistence.queries

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.responses.{BalanceResponse, PaymentResponse}

import java.sql.PreparedStatement


class BalancesQuery(dbConn: DatabaseConnection) extends DatabaseQuery[Array[BalanceResponse]](dbConn) {
  override val queryString: String =
    """SELECT * FROM balances""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)
  override def setVariables(): DatabaseQuery[Array[BalanceResponse]] = {


    this
  }

  private var _response: Array[BalanceResponse] = Array[BalanceResponse]()

  override def execute(): DatabaseQuery[Array[BalanceResponse]] = {

    rt = asStatement.executeQuery()

    while(rt.next()) {
      logger.info(s"Amount to pay for address ${rt.getString(2)}: ${rt.getDouble(3)}")
      _response = _response++Array(BalanceResponse(
       rt.getString(1), rt.getString(2), rt.getDouble(3)
      ))
    }

    this
  }

  override def getResponse: Array[BalanceResponse] = _response
}
