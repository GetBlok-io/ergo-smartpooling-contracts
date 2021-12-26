package app

import boxes.{CommandInputBox, MetadataInputBox}
import config.{ConfigHandler, SmartPoolConfig}
import contracts.command.PKContract
import contracts.holding
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import payments.PaymentHandler
import persistence.queries.{BlockByHeightQuery, MinimumPayoutsQuery, PPLNSQuery}
import persistence.responses.ShareResponse
import persistence.{DatabaseConnection, PersistenceHandler}
import registers.{MemberList, ShareConsensus}
import transactions.{CreateCommandTx, DistributionTx}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try


// TODO: Change how wallet mneumonic is handled in order to be safer against hacks(maybe unlock file from node)
class DistributeMultipleCmd(config: SmartPoolConfig, blockHeights: Array[Int]) extends SmartPoolCmd(config) {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DISTRIBUTE_REWARDS_CMD)
  final val PPLNS_CONSTANT = 50000

  override val appCommand: app.AppCommand.Value = AppCommand.DistributeRewardsCmd
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
    logger.info(s"Performing BlockByHeight Query for ${blockHeights.length} blocks")

    // Assertions to make sure config is setup for command
    assert(holdConf.getHoldingAddress != "")
    // Assume holding type is default for now
    assert(holdConf.getHoldingType == "default")
    var shareResponseList: Array[Array[ShareResponse]] = Array(Array[ShareResponse]())
    for(blockHeight <- blockHeights) {
      val blockQuery = new BlockByHeightQuery(dbConn, paramsConf.getPoolId, blockHeight.toLong)
      val block = blockQuery.setVariables().execute().getResponse
      logger.info("Query executed successfully")
      logger.info(s"Block From Query: ")

      if (block == null) {
        logger.error("Block is null")
        exit(logger, ExitCodes.COMMAND_FAILED)
      }
      assert(block.status == "confirmed")
      // Block must have full num of confirmations
      //assert(block.confirmationProgress == 1.0)


      blockReward = (block.reward * Parameters.OneErg).toLong


      logger.info("Now performing PPLNS Query")
      val pplnsQuery = new PPLNSQuery(dbConn, paramsConf.getPoolId, blockHeight, PPLNS_CONSTANT)
      val shares: Array[ShareResponse] = pplnsQuery.setVariables().execute().getResponse
      logger.info("Query executed successfully")
      shareResponseList = shareResponseList ++ Array(shares)
    }
    val commandInputs = PaymentHandler.simpleMultiPPLNSToConsensus(shareResponseList)
    val tempConsensus = commandInputs._1
    memberList = commandInputs._2
    // Lets ensure that blocks are only set to confirmed once we pay them out.
    // TODO: Renable this for MC


    shareConsensus = applyMinimumPayouts(dbConn, memberList, tempConsensus)

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
      var metadataBoxById = Try { ctx.getBoxesById(metadataId.toString).head }
      if(metadataBoxById.isFailure){
        metadataBoxById = Try {ctx.getCoveringBoxesFor(Address.create(metaConf.getMetadataAddress), metaConf.getMetadataValue, List[ErgoToken](new ErgoToken(smartPoolId, 1)).asJava).getBoxes.asScala.head}
      }
      if(metadataBoxById.isFailure){
        exit(logger, ExitCodes.NO_SMARTPOOL_ID_IN_CONFIG)
      }


      val metadataBox = new MetadataInputBox(metadataBoxById.get, ErgoId.create(paramsConf.getSmartPoolId))
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

  private def applyMinimumPayouts(dbConn: DatabaseConnection, memberList: MemberList, shareConsensus: ShareConsensus): ShareConsensus ={
    var newShareConsensus = ShareConsensus.fromConversionValues(shareConsensus.cValue)
    logger.info(s"Now querying minimum payouts for ${newShareConsensus.cValue.length} different members in the smart pool.")
    for(member <- memberList.cValue){
      val minimumPayoutsQuery = new MinimumPayoutsQuery(dbConn, paramsConf.getPoolId, member._2)
      val settingsResponse = minimumPayoutsQuery.setVariables().execute().getResponse
      if(settingsResponse.paymentthreshold > 0.1){
        val propBytesIndex = newShareConsensus.cValue.map(c => c._1).indexOf(member._1, 0)
        newShareConsensus = ShareConsensus.fromConversionValues(
          shareConsensus.cValue.updated(propBytesIndex,
            (member._1, Array(newShareConsensus.cValue(propBytesIndex)._2(0), (settingsResponse.paymentthreshold * Parameters.OneErg).toLong, newShareConsensus.cValue(propBytesIndex)._2(2)))))
      }
    }
    logger.info("Minimum payouts updated for all smart pool members!")
    newShareConsensus
  }



}

