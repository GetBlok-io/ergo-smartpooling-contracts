package app.commands

import app.AppCommand
import config.SmartPoolConfig
import logging.LoggingHandler
import org.slf4j.LoggerFactory
import persistence.entries.BlockEntry
import persistence.writes.BlockUpdate
import persistence.{DatabaseConnection, PersistenceHandler}

// Command that resets confirmed blocks in db to pending.
class ResetToPendingCmd(config: SmartPoolConfig) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_RESET_STATUS_CMD)
  private var dbConn: DatabaseConnection = _
  override val appCommand: app.AppCommand.Value = AppCommand.ResetStatusCmd
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    logger.info("Creating connection to persistence database")
    val persistence = new PersistenceHandler(Some(config.getPersistence.getHost), Some(config.getPersistence.getPort), Some(config.getPersistence.getDatabase))
    persistence.setConnectionProperties(config.getPersistence.getUsername, config.getPersistence.getPassword, config.getPersistence.isSslConnection)

    dbConn = persistence.connectToDatabase


  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")

    logger.info("Now resetting confirmed blocks to have pending status...")

    val blockUpdate = new BlockUpdate(dbConn)
    val pending = "pending"
    val confirmed = "confirmed"

    val blockEntry = BlockEntry(paramsConf.getPoolId, confirmed, pending)

    blockUpdate.setVariables(blockEntry).execute()

    logger.info("Command has finished execution")
  }

  def recordToConfig: Unit = {
    logger.info("Nothing to record for config")
  }


}

