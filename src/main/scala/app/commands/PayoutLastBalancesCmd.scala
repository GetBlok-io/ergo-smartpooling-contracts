package app.commands

import app.{AppCommand, ExitCodes, exit}
import boxes.BoxHelpers
import configs.SmartPoolConfig
import explorer.ExplorerHandler
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.explorer.client.ExplorerApiClient
import org.slf4j.LoggerFactory
import persistence.entries.{BalanceChangeEntry, PaymentEntry}
import persistence.queries.{BalancesQuery, BoxIndexQuery, ConsensusByTransactionQuery, PaymentsQuery, SmartPoolByEpochQuery}
import persistence.writes.{BalanceChangeInsertion, ConsensusDeletionByNFT, PaymentInsertion, SmartPoolDeletionByNFT}
import persistence.{DatabaseConnection, PersistenceHandler}

/**
 * Command to payout balances from balances table when switching to smartpooling system.
 */


class PayoutLastBalancesCmd(config: SmartPoolConfig) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PAY_BALANCES_CMD)

  private var dbConn: DatabaseConnection = _
  private var explorerHandler: ExplorerHandler = _
  override val appCommand: app.AppCommand.Value = AppCommand.PayoutBalancesCmd
  private var secretStorage: SecretStorage = _
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    logger.info("Creating connection to persistence database")
    val persistence = new PersistenceHandler(Some(config.getPersistence.getHost), Some(config.getPersistence.getPort), Some(config.getPersistence.getDatabase))
    persistence.setConnectionProperties(config.getPersistence.getUsername, config.getPersistence.getPassword, config.getPersistence.isSslConnection)
    val explorerApiClient = new ExplorerApiClient(explorerUrl)

    secretStorage = SecretStorage.loadFrom(walletConf.getSecretStoragePath)
    secretStorage.unlock(nodeConf.getWallet.getWalletPass)
    dbConn = persistence.connectToDatabase


  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")



    logger.info("Now retrieving all balances from balances table.")
    val balancesArray = new BalancesQuery(dbConn).setVariables().execute().getResponse
    logger.info(s"Num responses from balances query: ${balancesArray.length}")
    ergoClient.execute{ctx: BlockchainContext =>


      val prover = ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
      var nodeAddress = prover.getAddress
      logger.info("Now printing EIP3 Addresses")
      for(i <- 0 to 10){
        try {
          logger.info(prover.getEip3Addresses.get(i).toString)
          nodeAddress = prover.getEip3Addresses.get(i)
        }
        catch {case exception: Exception => logger.warn(s"Could not find EIP address number $i for node wallet")}
      }


      logger.info("The following addresses must be exactly the same:")
      logger.info(s"Prover Address=${prover.getAddress}")
      logger.info(s"Node Address=${nodeAddress}")
      var outBoxes = Array[OutBox]()
      for(b <- balancesArray) {
        val amountToPay = (BigDecimal(b.amount) * Parameters.OneErg).toLong
        logger.info("Amount to pay: " + amountToPay)

        val dustRemoved = BoxHelpers.removeDust(amountToPay)
        logger.info("With dust removed: " + dustRemoved)
        if(dustRemoved > 0) {
          outBoxes = outBoxes ++ Array(ctx.newTxBuilder()
            .outBoxBuilder()
            .value(dustRemoved)
            .contract(new ErgoTreeContract(Address.create(b.address).getErgoAddress.script))
            .build())
        }
      }
      val totalSum = outBoxes.map(ob => ob.getValue).sum
      logger.info(s"Now paying out all outstanding balances from balances table for a total of ${(BigDecimal(totalSum) / Parameters.OneErg).toDouble} ERG")
      val inputBoxes = ctx.getWallet.getUnspentBoxes(totalSum + paramsConf.getInitialTxFee).get()

      val unsignedTx = ctx.newTxBuilder()
        .boxesToSpend(inputBoxes)
        .outputs(outBoxes:_*)
        .fee(paramsConf.getInitialTxFee)
        .sendChangeTo(nodeAddress.getErgoAddress)
        .build()
      logger.info("New tx now built!")
      val signedTx = prover.sign(unsignedTx)
      logger.info("Tx has now been signed!")
      logger.warn("THIS TX HAS NOT BEEN SENT, JUST EVALUATING COST RIGHT NOW")
      val txId = ctx.sendTransaction(signedTx)

      logger.info(s"Tx sent with id $txId and cost ${signedTx}")


    }

    exit(logger, ExitCodes.SUCCESS)
  }

  def recordToDb: Unit = {
    logger.info("Nothing to record for config")
    exit(logger, ExitCodes.SUCCESS)
  }


}

