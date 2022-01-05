package app.commands

import app.AppCommand
import boxes.MetadataInputBox
import config.SmartPoolConfig
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.LoggerFactory

// TODO: Change how wallet mneumonic is handled in order to be safer against hacks(maybe unlock file from node)
class ViewMetadataCmd(config: SmartPoolConfig) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_GEN_METADATA_CMD)

  override val appCommand: app.AppCommand.Value = AppCommand.GenerateMetadataCmd
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    // Make sure smart pool id is not set
    assert(metaConf.getMetadataId != "")
    assert(paramsConf.getSmartPoolId != "")
  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")


    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
      val metadataBox = new MetadataInputBox(ctx.getBoxesById(metaConf.getMetadataId).head, ErgoId.create(paramsConf.getSmartPoolId))
      logger.info(metadataBox.toString)
      metadataBox.toString
    })
    logger.info("Command has finished execution")
  }

  def recordToConfig: Unit = {
    logger.info("Nothing to record for config")
  }


}

