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
import persistence.queries.{BoxIndexQuery, ConsensusByTransactionQuery, SmartPoolByEpochQuery, SmartPoolByHeightQuery}
import persistence.responses.SmartPoolResponse
import persistence.writes.{BalanceChangeInsertion, ConsensusDeletion, ConsensusDeletionByNFT, PaymentInsertion, SmartPoolDeletion, SmartPoolDeletionByNFT}
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Command that cleans smartpool and consensus database of duplicate values(Values during the same epoch)
 * Only cleans if there exists a transaction during a certain epoch who's confirmation > 1
 */

// TODO: Add confirmation number to config file
// TODO: Use node instead of explorer?
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

    logger.info("Now retrieving all boxes from boxIndex")
    val boxIndex = new BoxIndexQuery(dbConn).setVariables().execute().getResponse
    val confirmations = for(boxResp <- boxIndex) yield (boxResp, explorerHandler.getTxConfirmations(boxResp.txId))
    for(c <- confirmations) {
      if(c._2 > 0){
        logger.info(s"Tx for subpool ${c._1.subpoolId} has confirmations!")
          val consensusResponses = new ConsensusByTransactionQuery(dbConn, paramsConf.getPoolId, c._1.txId).setVariables().execute().getResponse
          var paymentsInserted = 0L
          var balanceChangesInserted = 0L
          logger.info("Now inserting into payments and balance_changes tables for confirmed tx")
          for (consensus <- consensusResponses) {
            if (consensus.valuePaid > 0 && consensus.storedPayout == 0) {

              val amount = (BigDecimal(consensus.valuePaid) / Parameters.OneErg).toDouble
              val paymentEntry = PaymentEntry(paramsConf.getPoolId, consensus.miner,
                amount, consensus.transactionHash)
              paymentsInserted = paymentsInserted + (new PaymentInsertion(dbConn).setVariables(paymentEntry).execute())

              val balanceChangeEntry = BalanceChangeEntry(paramsConf.getPoolId, consensus.miner, -1 * amount, s"Reset balance from payment at epoch ${c._1.epoch}")
              balanceChangesInserted = balanceChangesInserted + (new BalanceChangeInsertion(dbConn).setVariables(balanceChangeEntry).execute())
            } else if (consensus.valuePaid == 0 && consensus.storedPayout > 0) {

              val amount = (BigDecimal(consensus.storedPayout) / Parameters.OneErg).toDouble
              val balanceChangeEntry = BalanceChangeEntry(paramsConf.getPoolId, consensus.miner, amount, s"Stored payment for ${consensus.shares} shares at epoch ${c._1.epoch}")
              balanceChangesInserted = balanceChangesInserted + (new BalanceChangeInsertion(dbConn).setVariables(balanceChangeEntry).execute())
            }
          }
          logger.info(s"$paymentsInserted payments inserted along with $balanceChangesInserted balance changes")
      }
    }
    val smartPoolResponse = new SmartPoolByEpochQuery(dbConn, paramsConf.getPoolId, boxIndex(0).epoch).setVariables().execute().getResponse.head
    logger.info("Now deleting all entries without last smartpoolnft")
    val smartPoolNFTWipe = new SmartPoolDeletionByNFT(dbConn).setVariables((paramsConf.getPoolId, paramsConf.getSmartPoolId)).execute()
    val consensusNFTWipe = new ConsensusDeletionByNFT(dbConn).setVariables((paramsConf.getPoolId, paramsConf.getSmartPoolId)).execute()
    logger.info("DB changes complete!")
    if(boxIndex.forall(br => br.status == "success")) {
      logger.info("All boxes in box response have status succcess!")
      exit(logger, ExitCodes.SUCCESS)
    }else {
      logger.warn("There were errors found in the current box index!")
      exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
    }

    exit(logger, ExitCodes.SUCCESS)
  }

  def recordToDb: Unit = {
    logger.info("Nothing to record for config")
    exit(logger, ExitCodes.SUCCESS)
  }


}

