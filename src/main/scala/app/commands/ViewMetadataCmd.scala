package app.commands

import app.AppCommand
import boxes.MetadataInputBox
import configs.SmartPoolConfig
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

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

      val inputBoxes = ctx.getUnspentBoxesFor(Address.create(metaConf.getMetadataAddress), 0, 500).asScala
      var metadataBoxes = List[MetadataInputBox]()
      for(mb <- inputBoxes){
       if(mb.getValue == metaConf.getMetadataValue){
         if(mb.getTokens.size() > 0){
           if(mb.getTokens.get(0).getId.toString == paramsConf.getSmartPoolId){
             logger.info("Found metadata box!")
             logger.info("Tokens " + mb.getTokens.asScala.toArray.mkString("Array(", ", ", ")"))
             metadataBoxes = metadataBoxes++List(new MetadataInputBox(mb, ErgoId.create(paramsConf.getSmartPoolId)))
           }
         }

       }
      }
      for(mb <- metadataBoxes) {
        logger.info("Metadata Box: " + mb)
      }
      metadataBoxes.head.toString
    })
    logger.info("Command has finished execution")
  }

  def recordToDb: Unit = {
    logger.info("Nothing to record for config")
  }


}

