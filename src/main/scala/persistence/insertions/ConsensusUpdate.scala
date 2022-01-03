package persistence.insertions

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.{ConsensusEntry, SmartPoolEntry}

import java.sql.PreparedStatement
import java.time.LocalDateTime

class ConsensusUpdate(dbConn: DatabaseConnection) extends DatabaseInsertion[ConsensusEntry](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """INSERT INTO
      | consensus (poolid, transactionhash, epoch, height, smartpoolnft, miner, shares, minpayout, storedpayout, created)
      | VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)

  override def setVariables(consensusEntry: ConsensusEntry): DatabaseInsertion[ConsensusEntry] = {

    val localDateTime = LocalDateTime.now()

    asStatement.setString(1, consensusEntry.poolId)
    asStatement.setString(2, consensusEntry.transactionHash)
    asStatement.setLong(3, consensusEntry.epoch)
    asStatement.setLong(4, consensusEntry.height)
    asStatement.setString(5, consensusEntry.smartPoolNft)
    asStatement.setString(6, consensusEntry.miner)
    asStatement.setLong(7, consensusEntry.shares)
    asStatement.setLong(8, consensusEntry.minPayout)
    asStatement.setLong(9, consensusEntry.storedPayout)
    asStatement.setObject(10, localDateTime)

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
