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


class CleanDbCmd(config: SmartPoolConfig, blockHeight: Int) extends SmartPoolCmd(config) {
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
    val boxIndex = BoxIndex.fromDatabase(dbConn, paramsConf.getPoolId)

    val txPair = for(boxResp <- boxIndex.getSuccessful.boxes) yield (boxResp, explorerHandler.getTx(boxResp._2.txId))
    ergoClient.execute { ctx: BlockchainContext =>
      var boxToStorage = Map.empty[MetadataInputBox, InputBox]
      var emptyStorage = Array.empty[MetadataInputBox]
      val holdingContract = new SimpleHoldingContract(
        SimpleHoldingContract.generateHoldingContract(ctx, Address.create(metaConf.getMetadataAddress), ErgoId.create(paramsConf.getSmartPoolId))
      )

      for (c <- txPair) {
        if (c._2.isDefined) {
          val paymentsQueryByTransaction = new PaymentsQueryByTransaction(dbConn, paramsConf.getPoolId, c._1._2.txId).setVariables().execute().getResponse
          logger.info(s"Tx for subpool ${c._1._2.subpoolId} has ${c._2.get.getNumConfirmations} confirmations with txid ${c._2.get.getId} and boxid ${c._2.get.getOutputs.get(0)}")
          if (c._2.get.getNumConfirmations > 0) {
            if (paymentsQueryByTransaction.length == 0) {
              logger.info("No payments found for this subpool, now making new payments!")
              val consensusResponses = new ConsensusByTransactionQuery(dbConn, paramsConf.getPoolId, c._2.get.getId).setVariables().execute().getResponse
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
            }
            logger.info("Now grabbing data from ctx")
            val metadataInputBox = new MetadataInputBox(
              ctx.getBoxesById(c._2.get.getOutputs.asScala.filter(i => i.getAddress == metaConf.getMetadataAddress).head.getBoxId).head,
              ErgoId.create(paramsConf.getSmartPoolId)
            )
            logger.info("Now grabbing storage from ctx")
            val storedBox = c._2.get.getOutputs.asScala.filter(o => o.getAddress == holdingContract.getAddress.toString)
            if(storedBox.size == 1) {
              val storedAsInput = ctx.getBoxesById(storedBox.head.getBoxId).head
              boxToStorage = boxToStorage ++ Map((metadataInputBox, storedAsInput))
            }else{
              emptyStorage = emptyStorage ++ Array(metadataInputBox)
            }
          } else{
            logger.warn(s"No transactions found for subpool ${c._1._1}")
          }
      }else{
          logger.warn(s"Transaction retrieval failed for subpool ${c._1._1}")
        }
    }
      boxIndex.writeConfirmed(boxToStorage)
      boxIndex.writeEmptyConfirmed(emptyStorage)
    }
    logger.info("Now deleting all entries without last smartpoolnft")
    val smartPoolNFTWipe = new SmartPoolDeletionByNFT(dbConn).setVariables((paramsConf.getPoolId, paramsConf.getSmartPoolId)).execute()
    val consensusNFTWipe = new ConsensusDeletionByNFT(dbConn).setVariables((paramsConf.getPoolId, paramsConf.getSmartPoolId)).execute()
    logger.info("DB changes complete!")

    val newBoxIdx = BoxIndex.fromDatabase(dbConn, paramsConf.getPoolId)
    if(newBoxIdx.getSuccessful.boxes.isEmpty && newBoxIdx.getFailed.boxes.isEmpty && newBoxIdx.getInitiated.boxes.isEmpty) {
//      logger.info("All boxes in box response have status confirmed!")
//      logger.info("Now querying and deleting shares for this block height.")
//      val initBlocks = newBoxIdx.getConfirmed.getUsed.boxes.head._2.blocks
//      if(newBoxIdx.getConfirmed.getUsed.boxes.forall(b => b._2.blocks sameElements initBlocks)){
//        logger.info("All used and confirmed boxes have same blocks distributed")
//        val lastShare =
//          if(nodeConf.getNetworkType == NetworkType.MAINNET)
//            ShareCollector.queryToWindow(dbConn, paramsConf.getPoolId, initBlocks.head).flatten.minBy(s => s.created.getTime)
//          else
//            ShareCollector.querySharePage(dbConn, paramsConf.getPoolId, initBlocks.head, 0).minBy(s => s.created.getTime)
//
//        ShareCollector.removeBeforeLast(dbConn, paramsConf.getPoolId, lastShare)
//        logger.info(s"Shares before ${lastShare.created.toString} with height ${lastShare.height} for block ${initBlocks.head} were transferred to archive.")
//      }else{
//        exit(logger, ExitCodes.NO_CONFIRMED_TXS_FOUND)
//      }
      exit(logger, ExitCodes.SUCCESS)
    }else {
      logger.warn("There were errors found in the current box index!")
      if(boxIndex.getUsed.boxes.forall(b => b._2.blocks sameElements boxIndex.getUsed.head._2.blocks)){
        logger.info(s"All subpools at blockHeights ${boxIndex.getUsed.head._2.blocks.mkString("Array(", ", ", ")")}")
        logger.warn(s"Now initiating failure retrial for subpools ${(boxIndex.getFailed.boxes.keys++boxIndex.getSuccessful.boxes.keys).toString()}")
        val distributeRewardsCmd = new DistributeCmd(config, boxIndex.getUsed.head._2.blocks.head.toInt)
        distributeRewardsCmd.initiateCommand
        distributeRewardsCmd.executeCommand
        distributeRewardsCmd.recordToDb
      }

      exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
    }

  }

  def recordToDb: Unit = {
    logger.info("Nothing to record for config")
    exit(logger, ExitCodes.SUCCESS)
  }


}

