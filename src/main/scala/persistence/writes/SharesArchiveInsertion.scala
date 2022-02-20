package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.SmartPoolEntry
import persistence.responses.ShareResponse

import java.sql.PreparedStatement
import java.time.{LocalDateTime, ZoneId}

class SharesArchiveInsertion(dbConn: DatabaseConnection) extends DatabaseWrite[ShareResponse](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """INSERT INTO
      | shares_archive (poolid, blockheight, difficulty, networkdifficulty, miner, worker, useragent, ipaddress, source, created)
      | VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(shareResponse: ShareResponse): DatabaseWrite[ShareResponse] = {

    asStatement.setString(1, shareResponse.poolId)
    asStatement.setLong(2, shareResponse.height)
    asStatement.setDouble(3, shareResponse.diff.doubleValue())
    asStatement.setDouble(4, shareResponse.netDiff.doubleValue())
    asStatement.setString(5, shareResponse.minerAddress)
    asStatement.setString(6, shareResponse.worker)
    asStatement.setString(7, shareResponse.userAgent)
    asStatement.setString(8, shareResponse.ipAddress)
    asStatement.setString(9, shareResponse.source)
    asStatement.setObject(10, LocalDateTime.ofInstant(shareResponse.created.toInstant, ZoneId.systemDefault()))

    this
  }

  override def execute(): Long = {
    //logger.info("Executing update")
    val rowsInserted = asStatement.executeUpdate()
    asStatement.close()
    //logger.info(s"Update executed. ${rowsInserted} Rows inserted into db.")
    rowsInserted
  }
}
