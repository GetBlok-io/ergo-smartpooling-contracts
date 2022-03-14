package app.commands

import app.{AppCommand, ExitCodes, exit}
import boxes.{BoxHelpers, MetadataInputBox}
import configs.SmartPoolConfig
import explorer.ExplorerHandler
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.explorer.client.ExplorerApiClient
import org.ergoplatform.restapi.client.JSON
import org.slf4j.LoggerFactory
import persistence.entries.{BalanceChangeEntry, BlockEntry, BoxIndexEntry, PaymentEntry}
import persistence.{DatabaseConnection, PersistenceHandler}
import persistence.queries.{BoxIndexQuery, ConsensusByTransactionQuery, PaymentsQuery, PaymentsQueryByTransaction, SmartPoolByEpochQuery, SmartPoolByHeightQuery}
import persistence.responses.SmartPoolResponse
import persistence.writes.{BalanceChangeInsertion, BlockUpdateByHeight, BoxIndexUpdate, ConsensusDeletionByNFT, PaymentInsertion, SmartPoolDeletionByNFT}
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Command that cleans smartpool and consensus database of duplicate values(Values during the same epoch)
 * Only cleans if there exists a transaction during a certain epoch who's confirmation > 1
 */

@deprecated
class CheckAndCleanDbCmd(config: SmartPoolConfig, blockHeight: Int) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_CLEAN_DB_CMD)

  private var dbConn: DatabaseConnection = _
  private var explorerHandler: ExplorerHandler = _
  override val appCommand: app.AppCommand.Value = AppCommand.CheckAndCleanDbCmd

  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    logger.info(s"CheckAndClean Db for blockHeight $blockHeight")
    logger.info("Creating connection to persistence database")
    val persistence = new PersistenceHandler(Some(config.getPersistence.getHost), Some(config.getPersistence.getPort), Some(config.getPersistence.getDatabase))
    persistence.setConnectionProperties(config.getPersistence.getUsername, config.getPersistence.getPassword, config.getPersistence.isSslConnection)
    val explorerApiClient = new ExplorerApiClient(explorerUrl)


    dbConn = persistence.connectToDatabase
    explorerHandler = new ExplorerHandler(explorerApiClient)
    logger.info("Explorer handler made!")

  }

  def executeCommand: Unit = {

    logger.info("Command has begun execution")
    logger.info(s"Now checking and cleaning db using boxes from boxIndex for blockHeight $blockHeight")
    logger.info("Now retrieving all boxes from boxIndex")
    val boxIndex = new BoxIndexQuery(dbConn).setVariables().execute().getResponse
    val confirmations = for(boxResp <- boxIndex if boxResp.epoch != 0) yield (boxResp, explorerHandler.getTxConfirmations(boxResp.txId))
    var currentEpoch = 0
    for(c <- confirmations) {
      val paymentsQueryByTransaction = new PaymentsQueryByTransaction(dbConn, paramsConf.getPoolId, c._1.txId).setVariables().execute().getResponse
      logger.info(s"Tx for subpool ${c._1.subpoolId} has ${c._2} confirmations with txid ${c._1.txId} and boxid ${c._1.boxId}")
      if(paymentsQueryByTransaction.length == 0) {
        logger.info("No payments found for this subpool, now making new payments!")
        if (c._2 > 0) {

          val consensusResponses = new ConsensusByTransactionQuery(dbConn, paramsConf.getPoolId, c._1.txId).setVariables().execute().getResponse
          logger.info("Consensus responses received!")
          var paymentsInserted = 0L
          var balanceChangesInserted = 0L
          logger.info("Now inserting into payments and balance_changes tables for confirmed tx")
          for (consensus <- consensusResponses) {
            val paymentsQuery = new PaymentsQuery(dbConn, paramsConf.getPoolId, consensus.miner, consensus.transactionHash).setVariables().execute().getResponse
            logger.info(s"Now doing payments query for address ${consensus.miner} and txId ${consensus.transactionHash}")
            logger.info(s"${paymentsQuery.length} responses found.")
            if (paymentsQuery.length == 0) {
              if (consensus.valuePaid > 0 && consensus.storedPayout == 0) {

                logger.info("No past payments found, now making new payment and balance change entry in db")
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
            } else {
              logger.info("Payments were found for this consensus, skipping db writes.")
            }

          }
          logger.info(s"$paymentsInserted payments inserted along with $balanceChangesInserted balance changes")
        }
      }
    }

    logger.info("Now deleting all entries without last smartpoolnft")
    val smartPoolNFTWipe = new SmartPoolDeletionByNFT(dbConn).setVariables((paramsConf.getPoolId, paramsConf.getSmartPoolId)).execute()
    val consensusNFTWipe = new ConsensusDeletionByNFT(dbConn).setVariables((paramsConf.getPoolId, paramsConf.getSmartPoolId)).execute()
    logger.info("DB changes complete!")

    val failedPools = confirmations.filter(b => b._2 == -1)
    if(failedPools.length > 0){
      logger.info("The following pools lacked confirmations: " ++ failedPools.flatMap(p => "\n" + p._1.subpoolId))

      exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
      if(failedPools.length != confirmations.length){
        logger.info("Some pools have already confirmed, now checking if this counts as a failure")
        val confirmedPools = confirmations.filter(b => b._2 != -1)
        if(confirmedPools.length > 20 && confirmedPools.forall(p => p._2 > 20)){
          logger.info("There are more than 20 confirmed pools, each with more than 20 confirmations. Now setting unconfirmed pools to failures.")
          ergoClient.execute{
            ctx =>
              for(poolFailure <- failedPools) {

                val metadataInputBox = BoxHelpers.searchMetadataFromCtx(ctx, ErgoId.create(paramsConf.getSmartPoolId),
                  Address.create(metaConf.getMetadataAddress), metaConf.getMetadataValue, poolFailure._1.subpoolId.toInt)
                val boxIndexEntry = BoxIndexEntry(paramsConf.getPoolId, metadataInputBox.getId.toString, "re-attempt",
                  metadataInputBox.getCurrentEpoch, "failure", paramsConf.getSmartPoolId, metadataInputBox.getSubpoolId.toString, poolFailure._1.blocks)
                val boxIndexUpdate = new BoxIndexUpdate(dbConn).setVariables(boxIndexEntry).execute()
                logger.info(s"Subpool ${metadataInputBox.getSubpoolId} updated with box id from blockchain along with new failure status")
              }
          }

        }else{
          logger.info("Not all pools fully")
        }
      }
      exit(logger, ExitCodes.COMMAND_FAILED)
    }

    if(boxIndex.forall(br => br.status == "success")) {
      logger.info("All boxes in box response have status success!")
      exit(logger, ExitCodes.SUCCESS)
    }else {
      logger.warn("There were errors found in the current box index!")

    }

    exit(logger, ExitCodes.SUCCESS)
  }

  def recordToDb: Unit = {
    logger.info("Nothing to record for config")
    exit(logger, ExitCodes.SUCCESS)
  }


}

