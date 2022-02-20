package app.commands

import app.{AppCommand, ExitCodes, exit}
import boxes.MetadataInputBox
import configs.SmartPoolConfig
import contracts.holding.SimpleHoldingContract
import explorer.ExplorerHandler
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.explorer.client.ExplorerApiClient
import org.slf4j.LoggerFactory
import payments.ShareCollector
import persistence.entries.{BalanceChangeEntry, PaymentEntry}
import persistence.queries.{BoxIndexQuery, ConsensusByTransactionQuery, PaymentsQuery, PaymentsQueryByTransaction}
import persistence.writes.{BalanceChangeInsertion, ConsensusDeletionByNFT, PaymentInsertion, SmartPoolDeletionByNFT}
import persistence.{BoxIndex, DatabaseConnection, PersistenceHandler}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
 * Command that cleans smartpool and consensus database of duplicate values(Values during the same epoch)
 * Only cleans if there exists a transaction during a certain epoch who's confirmation > 1
 */

// TODO: Add confirmation number to config file
// TODO: Use node instead of explorer?
class CheckAndCleanDbCmd2(config: SmartPoolConfig, blockHeight: Int) extends SmartPoolCmd(config) {
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
//    val blockUpdateByHeight = new BlockUpdateByHeight(dbConn, 681894).setVariables(BlockEntry(paramsConf.getPoolId, "paid", "paid")).execute()
  }

  def executeCommand: Unit = {


    logger.info("Command has begun execution")
    logger.info(s"Now checking and cleaning db using boxes from boxIndex for blockHeight $blockHeight")
    logger.info("Now retrieving all boxes from boxIndex")
    val boxIndex = BoxIndex.fromDatabase(dbConn, paramsConf.getPoolId)

    val confirmations = for(boxResp <- boxIndex.getSuccessful.boxes) yield (boxResp, explorerHandler.getTx(boxResp._2.txId))
    ergoClient.execute { ctx: BlockchainContext =>
      var boxToStorage = Map.empty[MetadataInputBox, InputBox]
      val holdingContract = new SimpleHoldingContract(
        SimpleHoldingContract.generateHoldingContract(ctx, Address.create(metaConf.getMetadataAddress), ErgoId.create(paramsConf.getSmartPoolId))
      )

      for (c <- confirmations) {
        val paymentsQueryByTransaction = new PaymentsQueryByTransaction(dbConn, paramsConf.getPoolId, c._1._2.txId).setVariables().execute().getResponse
        logger.info(s"Tx for subpool ${c._1._2.subpoolId} has ${c._2} confirmations with txid ${c._1._2.txId} and boxid ${c._1._2.boxId}")
        if (paymentsQueryByTransaction.length == 0) {
          logger.info("No payments found for this subpool, now making new payments!")
          if (c._2.get.getNumConfirmations > 0) {

            val consensusResponses = new ConsensusByTransactionQuery(dbConn, paramsConf.getPoolId, c._1._2.txId).setVariables().execute().getResponse
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

                  val balanceChangeEntry = BalanceChangeEntry(paramsConf.getPoolId, consensus.miner, -1 * amount, s"Reset balance from payment at epoch ${c._1._2.epoch}")
                  balanceChangesInserted = balanceChangesInserted + (new BalanceChangeInsertion(dbConn).setVariables(balanceChangeEntry).execute())
                } else if (consensus.valuePaid == 0 && consensus.storedPayout > 0) {

                  val amount = (BigDecimal(consensus.storedPayout) / Parameters.OneErg).toDouble
                  val balanceChangeEntry = BalanceChangeEntry(paramsConf.getPoolId, consensus.miner, amount, s"Stored payment for ${consensus.shares} shares at epoch ${c._1._2.epoch}")
                  balanceChangesInserted = balanceChangesInserted + (new BalanceChangeInsertion(dbConn).setVariables(balanceChangeEntry).execute())
                }
              } else {
                logger.info("Payments were found for this consensus, skipping db writes.")
              }

            }
            logger.info(s"$paymentsInserted payments inserted along with $balanceChangesInserted balance changes")
            val metadataInputBox = c._1._2.grabFromContext(ctx).get
            val storedBox = c._2.get.getOutputs.asScala.filter(o => o.getAddress == holdingContract.getAddress.toString).head
            val storedAsInput = ctx.getBoxesById(storedBox.getBoxId).head
            boxToStorage = boxToStorage ++ Map((metadataInputBox, storedAsInput))
          }
        }
      }
      boxIndex.writeConfirmed(boxToStorage)
    }
    logger.info("Now deleting all entries without last smartpoolnft")
    val smartPoolNFTWipe = new SmartPoolDeletionByNFT(dbConn).setVariables((paramsConf.getPoolId, paramsConf.getSmartPoolId)).execute()
    val consensusNFTWipe = new ConsensusDeletionByNFT(dbConn).setVariables((paramsConf.getPoolId, paramsConf.getSmartPoolId)).execute()
    logger.info("DB changes complete!")
//    var distributeFailedCmd = new DistributeFailedCmd(config, 27)
//    distributeFailedCmd.initiateCommand
//    distributeFailedCmd.setFailedValues(0.251, 687001)
//    distributeFailedCmd.executeCommand
//    exit(logger, ExitCodes.SUCCESS)
//    val sendToHoldingCmd = new SendToHoldingCmd(config, 674662)
//    sendToHoldingCmd.initiateCommand
//    sendToHoldingCmd.setBlockReward((BigDecimal(4.944) * Parameters.OneErg).toLong)
//    sendToHoldingCmd.executeCommand
//    exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
//val distributeRewardsCmd = new DistributeRewardsCmd(config, 674662)
//    distributeRewardsCmd.initiateCommand
//    distributeRewardsCmd.executeCommand
//    distributeRewardsCmd.recordToDb
    val newBoxIdx = BoxIndex.fromDatabase(dbConn, paramsConf.getPoolId)
    if(newBoxIdx.getSuccessful.boxes.isEmpty && newBoxIdx.getFailed.boxes.isEmpty && newBoxIdx.getInitiated.boxes.isEmpty) {
      logger.info("All boxes in box response have status confirmed!")
      logger.info("Now querying and deleting shares for this block height.")
      val initBlocks = newBoxIdx.getConfirmed.getUsed.boxes.head._2.blocks
      if(newBoxIdx.getConfirmed.getUsed.boxes.forall(b => b._2.blocks sameElements initBlocks)){
        logger.info("All used and confirmed boxes have same blocks distributed")
        val lastShare =
          if(nodeConf.getNetworkType == NetworkType.MAINNET)
            ShareCollector.queryToWindow(dbConn, paramsConf.getPoolId, initBlocks.head).flatten.minBy(s => s.created.getTime)
          else
            ShareCollector.querySharePage(dbConn, paramsConf.getPoolId, initBlocks.head).minBy(s => s.created.getTime)

        ShareCollector.removeBeforeLast(dbConn, paramsConf.getPoolId, lastShare)
        logger.info(s"Shares before ${lastShare.created.toString} with height ${lastShare.height} for block ${initBlocks.head} were transferred to archive.")
      }else{
        exit(logger, ExitCodes.NO_CONFIRMED_TXS_FOUND)
      }
      exit(logger, ExitCodes.SUCCESS)
    }else {
      logger.warn("There were errors found in the current box index!")
      exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
    }
    explorerHandler.printVoters()
  }

  def recordToDb: Unit = {
    logger.info("Nothing to record for config")
    exit(logger, ExitCodes.SUCCESS)
  }


}

