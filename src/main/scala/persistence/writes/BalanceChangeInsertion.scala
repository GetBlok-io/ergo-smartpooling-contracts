package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.{BalanceChangeEntry, PaymentEntry}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class BalanceChangeInsertion(dbConn: DatabaseConnection) extends DatabaseWrite[BalanceChangeEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """
      |INSERT INTO
      | balance_changes (poolid, address, amount, usage, tags, created)
      | VALUES(?, ?, ?, ?, ?, ?);""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(balanceChangeEntry: BalanceChangeEntry): DatabaseWrite[BalanceChangeEntry] = {
    val localDateTime = LocalDateTime.now()
    val tags = dbConn.asConnection.createArrayOf("text", Array[String]().map(_.asInstanceOf[AnyRef]))
    asStatement.setString(1, balanceChangeEntry.poolId)
    asStatement.setString(2, balanceChangeEntry.address)
    asStatement.setDouble(3, balanceChangeEntry.amount)
    asStatement.setString(4, balanceChangeEntry.usage)
    asStatement.setArray(5, tags)
    asStatement.setObject(6, localDateTime)

    this
  }

  override def execute(): Long = {

    val rowsInserted = asStatement.executeUpdate()
    asStatement.close()

    rowsInserted
  }
}