package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.{BlockEntry, PaymentEntry}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class PaymentInsertion (dbConn: DatabaseConnection) extends DatabaseWrite[PaymentEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """
      |INSERT INTO
      | payments (poolid, coin, address, amount, transactionconfirmationdata, created)
      | VALUES(?, ?, ?, ?, ?, ?);""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(paymentEntry: PaymentEntry): DatabaseWrite[PaymentEntry] = {
    val localDateTime = LocalDateTime.now()
    asStatement.setString(1, paymentEntry.poolId)
    asStatement.setString(2, "ERG")
    asStatement.setString(3, paymentEntry.address)
    asStatement.setDouble(4, paymentEntry.amount)
    asStatement.setString(5, paymentEntry.txConfirmationData)
    asStatement.setObject(6, localDateTime)

    this
  }

  override def execute(): Long = {

    val rowsInserted = asStatement.executeUpdate()
    asStatement.close()

    rowsInserted
  }
}