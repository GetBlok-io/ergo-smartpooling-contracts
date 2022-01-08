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
import persistence.entries.{BalanceChangeEntry, PaymentEntry}
import persistence.{DatabaseConnection, PersistenceHandler}
import persistence.queries.{ConsensusByTransactionQuery, SmartPoolByEpochQuery, SmartPoolByHeightQuery}
import persistence.responses.SmartPoolResponse
import persistence.writes.{BalanceChangeInsertion, ConsensusDeletion, ConsensusDeletionByNFT, PaymentInsertion, SmartPoolDeletion, SmartPoolDeletionByNFT}
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

    if(confirmations.exists(c => c._2 >= 1)){
      logger.info("Found transaction with >= 1 confirmations!")
      val duplicateTxs = confirmations.filter(c => c._2 < 1)
      val realTxs = confirmations.filter(c => c._2 >= 1)
      val realTx = realTxs.filter(c => c._1.smartpoolNFT == lastSmartPool.smartpoolNFT).head
      val consensusResponses = new ConsensusByTransactionQuery(dbConn, paramsConf.getPoolId, realTx._1.transactionHash).setVariables().execute().getResponse
      var paymentsInserted = 0L
      var balanceChangesInserted = 0L
      logger.info("Now inserting into payments and balance_changes tables for confirmed tx")
      for(consensus <- consensusResponses) {
        if(consensus.valuePaid > 0 && consensus.storedPayout == 0){

          val amount = (BigDecimal(consensus.valuePaid) / Parameters.OneErg).toDouble
          val paymentEntry = PaymentEntry(paramsConf.getPoolId, consensus.miner,
            amount, consensus.transactionHash)
          paymentsInserted = paymentsInserted + (new PaymentInsertion(dbConn).setVariables(paymentEntry).execute())

          val balanceChangeEntry = BalanceChangeEntry(paramsConf.getPoolId, consensus.miner, -1 * amount, s"Reset balance from payment at epoch $currEpoch")
          balanceChangesInserted = balanceChangesInserted + (new BalanceChangeInsertion(dbConn).setVariables(balanceChangeEntry).execute())
        }else if(consensus.valuePaid == 0 && consensus.storedPayout > 0){
          
          val amount = (BigDecimal(consensus.storedPayout) / Parameters.OneErg).toDouble
          val balanceChangeEntry = BalanceChangeEntry(paramsConf.getPoolId, consensus.miner, amount, s"Stored payment for ${consensus.shares} shares at epoch $currEpoch")
          balanceChangesInserted = balanceChangesInserted + (new BalanceChangeInsertion(dbConn).setVariables(balanceChangeEntry).execute())
        }
      }
      logger.info(s"$paymentsInserted payments inserted along with $balanceChangesInserted balance changes")

      if(duplicateTxs.length > 0){
        logger.info("Duplicate transactions found, now pruning consensus and smartpool tables for duplicate values.")
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

      logger.info("Now deleting all entries without last smartpoolnft")
      val smartPoolNFTWipe = new SmartPoolDeletionByNFT(dbConn).setVariables(lastSmartPool).execute()
      val consensusNFTWipe = new ConsensusDeletionByNFT(dbConn).setVariables(lastSmartPool).execute()
      logger.info("DB changes complete!")

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

