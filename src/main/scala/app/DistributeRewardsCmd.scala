package app

import boxes.{CommandInputBox, MetadataInputBox}
import config.{ConfigHandler, SmartPoolConfig}
import contracts.MetadataContract
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
    // Lets ensure that blocks are only set to confirmed once we pay them out.
    assert(block.status == "pending")
    // Block must have full num of confirmations
    assert(block.confirmationProgess == 100.0)
    // Assertions to make sure config is setup for command
    assert(holdConf.getHoldingAddress != "")
    // Assume holding type is default for now
    assert(holdConf.getHoldingType == "default")

    val blockCreated = block.created
    blockReward = (block.reward * Parameters.OneErg).toLong
    holdingContract = new SimpleHoldingContract(new ErgoTreeContract(Address.create(holdConf.getHoldingAddress).getErgoAddress.script))

    logger.info("Now performing PPLNS Query")
    val pplnsQuery = new PPLNSQuery(dbConn, paramsConf.getPoolId, blockCreated, PPLNS_CONSTANT)
    val shares = pplnsQuery.setVariables().execute().getResponse
    logger.info("Query executed successfully")
    val commandInputs = PaymentHandler.pplnsToConsensus(shares)
    shareConsensus = commandInputs._1
    memberList = commandInputs._2

    logger.info(s"Share consensus and member list with ${shareConsensus.nValue.size} unique addresses have been built")
  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")

    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {

      val mnemonic = SecretString.create(nodeConf.getWallet.getWalletMneumonic)
      val password = SecretString.create(nodeConf.getWallet.getWalletPass)

      val prover = ctx.newProverBuilder().withMnemonic(mnemonic, password).build()
      val nodeAddress = Address.fromMnemonic(nodeConf.getNetworkType, mnemonic, password)

      logger.info("The following addresses must be exactly the same:")
      logger.info(s"Prover Address=${prover.getAddress}")
      logger.info(s"Node Address=${nodeAddress}")
      assert(prover.getAddress == nodeAddress)

      logger.warn("Using hard-coded PK Command Contract, ensure this value is added to configuration file later for more command box options")

      val metadataBox = new MetadataInputBox(ctx.getBoxesById(metadataId.toString).head, ErgoId.create(paramsConf.getSmartPoolId))
      logger.info(metadataBox.toString)

      val commandTx = new CreateCommandTx(ctx.newTxBuilder())
      val commandContract = new PKContract(nodeAddress)
      val inputBoxes = ctx.getCoveringBoxesFor(nodeAddress, AppParameters.defaultCommandValue + commandTx.txFee, List[ErgoToken]().asJava).getBoxes.asScala.toList

      logger.warn("Using hard-coded command value and tx fee, ensure this value is added to configuration file later for more command box options")

      val unsignedCommandTx =
        commandTx
          .metadataToCopy(metadataBox)
          .withCommandContract(commandContract)
          .commandValue(AppParameters.defaultCommandValue)
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

