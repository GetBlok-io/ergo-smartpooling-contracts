package app.commands

import app.{AppCommand, ExitCodes, exit}
import config.SmartPoolConfig
import contracts.holding.SimpleHoldingContract
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import persistence.PersistenceHandler
import persistence.queries.BlockByHeightQuery

// TODO: Change how wallet mneumonic is handled in order to be safer against hacks(maybe unlock file from node)
class SendMultipleToHoldingCmd(config: SmartPoolConfig, blockHeights: Array[Int]) extends SmartPoolCmd(config) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_SEND_TO_HOLDING_CMD)
  private var blockReward = 0L
  val txFee: Long = Parameters.MinFee

  override val appCommand: app.AppCommand.Value = AppCommand.SendToHoldingCmd
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    // Basic assertions
    assert(holdConf.getHoldingAddress != "")
    assert(holdConf.getHoldingType == "default")
    assert(paramsConf.getSmartPoolId != "")
    assert(metaConf.getMetadataId != "")

    logger.info("Creating connection to persistence database")
    val persistence = new PersistenceHandler(Some(config.getPersistence.getHost), Some(config.getPersistence.getPort), Some(config.getPersistence.getDatabase))
    persistence.setConnectionProperties(config.getPersistence.getUsername, config.getPersistence.getPassword, config.getPersistence.isSslConnection)

    val dbConn = persistence.connectToDatabase

    logger.info(s"Now performing BlockByHeight Query for ${blockHeights.length} blocks")
    for(blockHeight <- blockHeights) {
      val blockQuery = new BlockByHeightQuery(dbConn, paramsConf.getPoolId, blockHeight.toLong)
      val block = blockQuery.setVariables().execute().getResponse
      logger.info("Query executed successfully")
      logger.info(s"Block From Query: ")

      if (block == null) {
        logger.error("Block is null")
        exit(logger, ExitCodes.COMMAND_FAILED)
      }
      try {
        logger.info(s"Block Height: ${block.blockheight}")
        logger.info(s"Block Id: ${block.id}")
        logger.info(s"Block Reward: ${block.reward}")
        logger.info(s"Block Progress: ${block.confirmationProgress}")
        logger.info(s"Block Status: ${block.status}")
        logger.info(s"Block Created: ${block.created}")
      } catch {
        case exception: Exception =>
          logger.error(exception.getMessage)
          exit(logger, ExitCodes.COMMAND_FAILED)
      }

      // Block must still be pending
      //assert(block.status == "pending")
      blockReward = blockReward + (BigDecimal(block.reward) * Parameters.OneErg).toLong
      // Block must have full num of confirmations
      //assert(block.confirmationProgress == 1.0)
      // Assertions to make sure config is setup for command
    }

  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")
    logger.info(s"Total Block Reward to Send: $blockReward")
    blockReward = blockReward - (blockReward % Parameters.MinFee)
    logger.info(s"Rounding block reward to minimum box amount: $blockReward")
    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
      val secretStorage = SecretStorage.loadFrom(walletConf.getSecretStoragePath)
      secretStorage.unlock(nodeConf.getWallet.getWalletPass)

      val prover = ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
      val nodeAddress = prover.getEip3Addresses.get(0)

      val holdingContract = SimpleHoldingContract.generateHoldingContract(ctx, Address.create(metaConf.getMetadataAddress), ErgoId.create(paramsConf.getSmartPoolId))
      val txB = ctx.newTxBuilder()
      val outB = txB.outBoxBuilder()
      val inputBoxes = ctx.getWallet.getUnspentBoxes(blockReward + txFee)

      val holdingBox =
        outB
        .value(blockReward)
        .contract(holdingContract)
        .build()

      val tx: UnsignedTransaction = txB
        .boxesToSpend(inputBoxes.get)
        .outputs(holdingBox)
        .fee(txFee)
        .sendChangeTo(nodeAddress.getErgoAddress)
        .build()
      logger.info("SendToHolding Tx built")
      val signed: SignedTransaction = prover.sign(tx)
      logger.info("SendToHolding Tx signed")
      // Submit transaction to node
      val txId: String = ctx.sendTransaction(signed)
      logger.info(s"Tx successfully sent with id: $txId and cost: ${signed.getCost} \n")
      signed.toJson(true)

    })
    logger.info("Command has finished execution")
  }

  def recordToConfig: Unit = {
    logger.info("Nothing to record for config")
  }


}

