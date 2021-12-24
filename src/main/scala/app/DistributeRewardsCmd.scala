package app

import boxes.{CommandInputBox, MetadataInputBox}
import config.{ConfigHandler, SmartPoolConfig}
import contracts.{MetadataContract, holding}
import contracts.command.PKContract
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.restapi.client.ApiClient
import org.slf4j.{Logger, LoggerFactory}
import payments.PaymentHandler
import transactions.{CreateCommandTx, DistributionTx, GenesisTx}
import persistence.PersistenceHandler
import persistence.queries.{BlockByHeightQuery, PPLNSQuery}
import persistence.responses.ShareResponse
import registers.{MemberList, ShareConsensus}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}


// TODO: Change how wallet mneumonic is handled in order to be safer against hacks(maybe unlock file from node)
class DistributeRewardsCmd(config: SmartPoolConfig, blockHeight: Int) extends SmartPoolCmd(config) {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DISTRIBUTE_REWARDS_CMD)
  final val PPLNS_CONSTANT = 50000

  override val appCommand: app.AppCommand.Value = AppCommand.GenerateMetadataCmd
  private var smartPoolId: ErgoId = _
  private var metadataId: ErgoId = _
  private var holdingContract: HoldingContract = _
  private var blockReward: Long = 0L

  private var memberList: MemberList = _
  private var shareConsensus: ShareConsensus = _

  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    // Make sure smart pool ids are set
    assert(paramsConf.getSmartPoolId != "")
    assert(metaConf.getMetadataId != "")
    smartPoolId = ErgoId.create(paramsConf.getSmartPoolId)
    metadataId = ErgoId.create(metaConf.getMetadataId)

    logger.info("Creating connection to persistence database")
    val persistence = new PersistenceHandler(Some(config.getPersistence.getHost), Some(config.getPersistence.getPort), Some(config.getPersistence.getDatabase))
    persistence.setConnectionProperties(config.getPersistence.getUsername, config.getPersistence.getPassword, config.getPersistence.isSslConnection)

    val dbConn = persistence.connectToDatabase
    logger.info("Now performing BlockByHeight Query")
    val blockQuery = new BlockByHeightQuery(dbConn, paramsConf.getPoolId, blockHeight.toLong)
    val block =  blockQuery.setVariables().execute().getResponse
    logger.info("Query executed successfully")
    logger.info(s"Block From Query: ")

    if(block == null){
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
    }catch{
      case exception: Exception =>
        logger.error(exception.getMessage)
        exit(logger, ExitCodes.COMMAND_FAILED)
    }
    // Lets ensure that blocks are only set to confirmed once we pay them out.
    // TODO: Renable this for MC
    assert(block.status == "confirmed")
    // Block must have full num of confirmations
    assert(block.confirmationProgress == 1.0)
    // Assertions to make sure config is setup for command
    assert(holdConf.getHoldingAddress != "")
    // Assume holding type is default for now
    assert(holdConf.getHoldingType == "default")

    blockReward = (block.reward * Parameters.OneErg).toLong


    logger.info("Now performing PPLNS Query")
    val pplnsQuery = new PPLNSQuery(dbConn, paramsConf.getPoolId, blockHeight, PPLNS_CONSTANT)
    val shares = pplnsQuery.setVariables().execute().getResponse
    logger.info("Query executed successfully")
    val commandInputs = PaymentHandler.simplePPLNSToConsensus(shares)
    shareConsensus = commandInputs._1
    memberList = commandInputs._2

    logger.info(s"Share consensus and member list with ${shareConsensus.nValue.size} unique addresses have been built")
    logger.info(shareConsensus.nValue.toString())
    logger.info(memberList.nValue.toString())
  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")

    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {

      val secretStorage = SecretStorage.loadFrom(walletConf.getSecretStoragePath)
      secretStorage.unlock(nodeConf.getWallet.getWalletPass)

      val prover = ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
      val nodeAddress = prover.getEip3Addresses.get(0)
      holdingContract = new holding.SimpleHoldingContract(SimpleHoldingContract.generateHoldingContract(ctx, Address.create(metaConf.getMetadataAddress), ErgoId.create(paramsConf.getSmartPoolId)))
      logger.info("Holding Address: " + holdingContract.getAddress.toString)
      val isHoldingCovered = ctx.getCoveringBoxesFor(holdingContract.getAddress, blockReward, List[ErgoToken]().asJava).isCovered

      if(!isHoldingCovered){
        exit(logger, ExitCodes.HOLDING_NOT_COVERED)
      }else{
        logger.info("Holding address has enough ERG to cover transaction")
      }

      logger.info(s"Prover Address=${prover.getAddress}")
      logger.info(s"Node Address=${nodeAddress}")
      //assert(prover.getAddress == nodeAddress)

      logger.warn("Using hard-coded PK Command Contract, ensure this value is added to configuration file later for more command box options")

      val metadataBox = new MetadataInputBox(ctx.getBoxesById(metadataId.toString).head, ErgoId.create(paramsConf.getSmartPoolId))
      logger.info(metadataBox.toString)

      val commandTx = new CreateCommandTx(ctx.newTxBuilder())
      val commandContract = new PKContract(nodeAddress)
      val inputBoxes = ctx.getWallet.getUnspentBoxes(cmdConf.getCommandValue + commandTx.txFee).get().asScala.toList

      logger.warn("Using hard-coded command value and tx fee, ensure this value is added to configuration file later for more command box options")

      val unsignedCommandTx =
        commandTx
          .metadataToCopy(metadataBox)
          .withCommandContract(commandContract)
          .commandValue(cmdConf.getCommandValue)
          .inputBoxes(inputBoxes: _*)
          .withHolding(holdingContract, blockReward)
          .setConsensus(shareConsensus)
          .setMembers(memberList)
          .buildCommandTx()
      val signedCmdTx = prover.sign(unsignedCommandTx)
      logger.info("Command Tx successfully signed")
      val cmdTxId = ctx.sendTransaction(signedCmdTx)
      logger.info(s"Tx was successfully sent with id: $cmdTxId")

      val commandBox = new CommandInputBox(commandTx.commandOutBox.convertToInputWith(cmdTxId.filter(c => c != '\"'), 0), commandContract)
      logger.info("Now building DistributionTx using new command box...")
      logger.info(commandBox.toString)
      val distTx = new DistributionTx(ctx.newTxBuilder())
      val unsignedDistTx =
        distTx
          .metadataInput(metadataBox)
          .commandInput(commandBox)
          .holdingValue(blockReward)
          .holdingContract(holdingContract)
          .buildMetadataTx()
      val signedDistTx = prover.sign(unsignedDistTx)
      logger.info("Distribution Tx successfully signed.")
      val txId = ctx.sendTransaction(signedDistTx).filter(c => c != '\"')
      logger.info(s"Tx successfully sent with id: $txId")
      metadataId = signedDistTx.getOutputsToSpend.get(0).getId
      signedDistTx.toJson(true)
    })
    logger.info("Command has finished execution")
  }

  def recordToConfig: Unit = {
    logger.info("Recording tx info into config...")

    logger.info("The following information will be updated:")
    logger.info(s"Metadata Id: ${metaConf.getMetadataId}(old) -> $metadataId")

    val newConfig = config.copy()
    newConfig.getParameters.getMetaConf.setMetadataId(metadataId.toString)

    ConfigHandler.writeConfig(AppParameters.configFilePath, newConfig)
    logger.info("Config file has been successfully updated")
  }



}

