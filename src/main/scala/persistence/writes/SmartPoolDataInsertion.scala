package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.SmartPoolEntry

import java.sql.PreparedStatement
import java.time.LocalDateTime

class SmartPoolDataInsertion(dbConn: DatabaseConnection) extends DatabaseWrite[SmartPoolEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """INSERT INTO
      | smartpool_data (poolid, transactionhash, epoch, height, members, fees, info, operators, smartpoolnft, created, blocks, subpool_id)
      | VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(smartPoolEntry: SmartPoolEntry): DatabaseWrite[SmartPoolEntry] = {

    val memberArray = dbConn.asConnection.createArrayOf("text",smartPoolEntry.members.map(_.asInstanceOf[AnyRef]))
    val feesArray = dbConn.asConnection.createArrayOf("bigint", smartPoolEntry.fees.map(_.asInstanceOf[AnyRef]))
    val infoArray = dbConn.asConnection.createArrayOf("bigint", smartPoolEntry.info.map(_.asInstanceOf[AnyRef]))
    val opsArray = dbConn.asConnection.createArrayOf("text",smartPoolEntry.operators.map(_.asInstanceOf[AnyRef]))
    val blocksArray = dbConn.asConnection.createArrayOf("bigint",smartPoolEntry.blocks.map(_.asInstanceOf[AnyRef]))

    val localDateTime = LocalDateTime.now()

    asStatement.setString(1, smartPoolEntry.poolId)
    asStatement.setString(2, smartPoolEntry.transactionHash)
    asStatement.setLong(3, smartPoolEntry.epoch)
    asStatement.setLong(4, smartPoolEntry.height)

    asStatement.setArray(5, memberArray)
    asStatement.setArray(6, feesArray)
    asStatement.setArray(7, infoArray)
    asStatement.setArray(8, opsArray)

    asStatement.setString(9, smartPoolEntry.smartPoolNft)
    asStatement.setObject(10, localDateTime)

    asStatement.setArray(11, blocksArray)
    asStatement.setString(12, smartPoolEntry.subpoolId)
    this
  }

  override def execute(): Long = {
    logger.info("Executing update")
    val rowsInserted = asStatement.executeUpdate()
    asStatement.close()
    logger.info(s"Update executed. ${rowsInserted} Rows inserted into db.")
    rowsInserted
  }
}
