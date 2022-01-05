package app.commands

import app.{AppCommand, ExitCodes, exit}
import boxes.MetadataInputBox
import config.SmartPoolConfig
import explorer.ExplorerHandler
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.explorer.client.ExplorerApiClient
import org.ergoplatform.restapi.client.JSON
import org.slf4j.LoggerFactory
import persistence.{DatabaseConnection, PersistenceHandler}
import persistence.queries.{SmartPoolByEpochQuery, SmartPoolByHeightQuery}
import persistence.responses.SmartPoolResponse
import persistence.writes.{ConsensusDeletion, SmartPoolDeletion}
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Command that cleans smartpool and consensus database of duplicate values(Values during the same epoch)
 * Only cleans if there exists a transaction during a certain epoch who's confirmation > 1
 */

// TODO: Add confirmation number to config file
class CheckAndCleanDbCmd(config: SmartPoolConfig, blockHeight: Int) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_CLEAN_DB_CMD)

  private var dbConn: DatabaseConnection = _
  private var explorerHandler: ExplorerHandler = _
  override val appCommand: app.AppCommand.Value = AppCommand.CheckAndCleanDbCmd

  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    logger.info("Creating connection to persistence database")
    val persistence = new PersistenceHandler(Some(config.getPersistence.getHost), Some(config.getPersistence.getPort), Some(config.getPersistence.getDatabase))
    persistence.setConnectionProperties(config.getPersistence.getUsername, config.getPersistence.getPassword, config.getPersistence.isSslConnection)
    val explorerApiClient = new ExplorerApiClient(explorerUrl)


    dbConn = persistence.connectToDatabase
    explorerHandler = new ExplorerHandler(explorerApiClient)

  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")
    val smartPoolAtHeight = new SmartPoolByHeightQuery(dbConn, paramsConf.getPoolId, blockHeight)
    val lastSmartPool = smartPoolAtHeight.setVariables().execute().getResponse

    logger.info("Now checking if smartpool exists at given height")
    if(lastSmartPool == null){
      exit(logger, ExitCodes.INVALID_ARGUMENTS)
    }
    logger.info("SmartPool found at given height")
    val currEpoch = lastSmartPool.epoch
    val smartPoolResponses = new SmartPoolByEpochQuery(dbConn, paramsConf.getPoolId, currEpoch)
      .setVariables().execute().getResponse
    val confirmations = for(smartPool <- smartPoolResponses) yield (smartPool, explorerHandler.getTxConfirmations(smartPool.transactionHash))

    if(confirmations.exists(c => c._2 > 1)){
      logger.info("Found transaction with > 1 confirmations!")
      val duplicateTxs = confirmations.filter(c => c._2 <= 1)
      if(duplicateTxs.length > 0){
        var smartPoolDeleted = 0L
        for(response <- duplicateTxs){
          val consensusDeletion = new ConsensusDeletion(dbConn)
          val consensusDeleted = consensusDeletion.setVariables(response._1).execute()
          logger.info(s"Deleted $consensusDeleted rows from consensus table for ${response._1.transactionHash} and height ${response._1.height}")
          val smartPoolDeletion = new SmartPoolDeletion(dbConn)
          smartPoolDeleted = smartPoolDeleted + smartPoolDeletion.setVariables(response._1).execute()
        }
        logger.info(s"Deleted $smartPoolDeleted rows from smartpool_data table")
      }else{
        logger.info("No duplicate transactions found for given epoch")
      }
    }else{
      exit(logger, ExitCodes.NO_CONFIRMED_TXS_FOUND)
    }

    logger.info("Command has finished execution")
  }

  def recordToConfig: Unit = {
    logger.info("Nothing to record for config")
    exit(logger, ExitCodes.SUCCESS)
  }


}

