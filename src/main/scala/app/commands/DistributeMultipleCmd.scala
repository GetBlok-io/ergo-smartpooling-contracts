package app.commands

import app.{AppCommand, AppParameters, ExitCodes, exit}
import boxes.{BoxHelpers, CommandInputBox}
import config.{ConfigHandler, SmartPoolConfig}
import contracts.command.PKContract
import contracts.holding
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import payments.SimplePPLNS
import persistence.entries.{ConsensusEntry, PaymentEntry, SmartPoolEntry}
import persistence.queries.{BlockByHeightQuery, MinimumPayoutsQuery, PPLNSQuery}
import persistence.responses.ShareResponse
import persistence.writes.{ConsensusInsertion, PaymentInsertion, SmartPoolDataInsertion}
import persistence.{DatabaseConnection, PersistenceHandler}
import registers.{MemberList, ShareConsensus}
import transactions.{CreateCommandTx, DistributionTx, RegroupTx}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try

/**
 * Deprecated now, originally used to distribute multiple blocks at once. Because this class focuses on using 1 metadata box
 * it does not work for mainnet(Where DistributionGroup class is used instead to handle multiple subpools at once)
 */
@deprecated
class DistributeMultipleCmd(config: SmartPoolConfig, blockHeights: Array[Int]) extends SmartPoolCmd(config) {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DISTRIBUTE_REWARDS_CMD)
  final val PPLNS_CONSTANT = 50000

  override val appCommand: app.AppCommand.Value = AppCommand.DistributeRewardsCmd
  private var smartPoolId: ErgoId = _
  private var metadataId: ErgoId = _
  private var holdingContract: HoldingContract = _
  private var blockReward: Long = 0L
  private var dbConn: DatabaseConnection = _
  private var memberList: MemberList = _
  private var shareConsensus: ShareConsensus = _

  private var txId: String = _
  private var nextCommandBox: CommandInputBox = _
  private var signedTx: SignedTransaction = _
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

    dbConn = persistence.connectToDatabase
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


      blockReward = blockReward + (block.reward * Parameters.OneErg).toLong


      logger.info("Now performing PPLNS Query")
      val pplnsQuery = new PPLNSQuery(dbConn, paramsConf.getPoolId, blockHeight, PPLNS_CONSTANT)
      val shares: Array[ShareResponse] = pplnsQuery.setVariables().execute().getResponse
      logger.info("Query executed successfully")
      shareResponseList = shareResponseList ++ Array(shares)
    }
    val commandInputs = SimplePPLNS.simpleMultiPPLNSToConsensus(shareResponseList)
    val tempConsensus = commandInputs._1
    memberList = commandInputs._2


    logger.info(s"Total rewards from all blocks: ${blockReward}")
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

      logger.info(s"Total Block Reward to Send: $blockReward")
      blockReward = blockReward - (blockReward % Parameters.MinFee)
      logger.info(s"Rounding block reward to minimum box amount: $blockReward")

      val prover = ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
      val nodeAddress = prover.getEip3Addresses.get(0)
      holdingContract = new holding.SimpleHoldingContract(SimpleHoldingContract.generateHoldingContract(ctx, Address.create(metaConf.getMetadataAddress), ErgoId.create(paramsConf.getSmartPoolId)))
      logger.info("Holding Address: " + holdingContract.getAddress.toString)

      logger.info(s"Prover Address=${prover.getAddress}")
      logger.info(s"Node Address=${nodeAddress}")
      //assert(prover.getAddress == nodeAddress)

      logger.warn("Using hard-coded PK Command Contract, ensure this value is added to configuration file later for more command box options")
      logger.info("Now attempting to retrieve metadata box from blockchain")
      val selectMetadataBox = Try {BoxHelpers.selectCurrentMetadata(ctx, smartPoolId, Address.create(metaConf.getMetadataAddress), metaConf.getMetadataValue)}

      if(selectMetadataBox.isFailure){
        logger.warn("Metadata box could not be retrieved!")
        exit(logger, ExitCodes.FAILED_TO_RETRIEVE_METADATA)
      }
      logger.info("Now creating metadata box...")

      val metadataBox = selectMetadataBox.get

      val storedPayouts = metadataBox.getShareConsensus.cValue.map(c => c._2(2)).sum
      val isHoldingCovered = Try(ctx.getCoveringBoxesFor(holdingContract.getAddress, blockReward + storedPayouts, List[ErgoToken]().asJava).isCovered)

      if(isHoldingCovered.isFailure){
        exit(logger, ExitCodes.HOLDING_NOT_COVERED)
      }else{
        if(isHoldingCovered.get)
          logger.info("Holding address has enough ERG to cover transaction")
        else
          exit(logger, ExitCodes.HOLDING_NOT_COVERED)
      }
      //BoxHelpers.findIdealHoldingBoxes(ctx, holdingContract.getAddress, blockReward, storedPayouts)
      val holdingCoveringBoxes = ctx.getCoveringBoxesFor(holdingContract.getAddress, blockReward + storedPayouts, List[ErgoToken]().asJava)

      var holdingBoxes = BoxHelpers.findIdealHoldingBoxes(ctx, holdingContract.getAddress, blockReward, storedPayouts)
      //var holdingBoxes = holdingCoveringBoxes.getBoxes.asScala.toList
      val holdingCovered = holdingCoveringBoxes.getCoveredAmount
      val holdingSummed = holdingBoxes.map(x => x.getValue.toLong).sum
      val holdingSummed2 = holdingBoxes.map(x => x.getValue.longValue()).sum
      val holdingFolded = holdingBoxes.foldLeft(0L){
        (accum: Long, box: InputBox) =>
          accum + box.getValue
      }
      logger.info(s"HoldingSummed1: $holdingSummed, HoldingSummed2: $holdingSummed2, HoldingFolded: $holdingFolded")
      logger.info("Covered amount of holding boxes: " + holdingCovered)


      logger.info("Now checking to see if holding boxes have value greater than block reward")
      logger.info("SumBoxes of holding boxes: " + BoxHelpers.sumBoxes(holdingBoxes))
      logger.info("BlockReward+StoredPayouts: " + (blockReward + storedPayouts))
      logger.info("BlockReward " + blockReward)
      logger.info("Stored Payouts " + storedPayouts)
      if(BoxHelpers.sumBoxes(holdingBoxes) > (blockReward + storedPayouts)){
        logger.info("Ideal holding boxes are greater than block rewards + stored payouts")
        logger.info("Initiating regroup tx to get exact holding box inputs.")
        val regroupTx = new RegroupTx(ctx.newTxBuilder())
        val unsignedRegroup = regroupTx
          .txFee(paramsConf.getInitialTxFee)
          .feeAddress(nodeAddress)
          .holdingContract(holdingContract)
          .holdingInputs(holdingBoxes)
          .newHoldingValue(blockReward + storedPayouts)
          .build()
        val signedRegroup = prover.sign(unsignedRegroup)
        val regroupTxId = ctx.sendTransaction(signedRegroup)
        logger.info(s"RegroupTx sent with id: $regroupTxId and cost: ${signedRegroup.getCost}")
        logger.warn("Now exiting distribution command as failure to ensure regroupTx is sent.")
        exit(logger, ExitCodes.REGROUP_TX_SENT)
        // Uncomment to allow regroup tx to chain into distribution
         val holdingInputs = signedRegroup.getOutputsToSpend.asScala.filter(i => i.getValue == (blockReward + storedPayouts)).toList

         holdingBoxes = holdingInputs
      }else{
        if(BoxHelpers.sumBoxes(holdingBoxes) < (blockReward + storedPayouts)){
          logger.info("Valid holding boxes could not be found!")
          exit(logger, ExitCodes.HOLDING_NOT_COVERED)
        }
      }

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
          .withHolding(holdingContract, holdingBoxes)
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
          .holdingInputs(holdingBoxes)
          .holdingContract(holdingContract)
          .buildMetadataTx()
      val signedDistTx = prover.sign(unsignedDistTx)
      logger.info("Distribution Tx successfully signed.")

      txId = ctx.sendTransaction(signedDistTx).filter(c => c != '\"')
      nextCommandBox = commandBox

      logger.info(s"Tx successfully sent with id: $txId and cost: ${signedDistTx.getCost}")
      metadataId = signedDistTx.getOutputsToSpend.get(0).getId
      signedTx = signedDistTx
      signedDistTx.toJson(true)
    })
    logger.info("Command has finished execution")
  }

  def recordToDb: Unit = {
    logger.info("Recording tx info into config...")

    logger.info("The following information will be updated:")
    logger.info(s"Metadata Id: ${metaConf.getMetadataId}(old) -> $metadataId")

    logger.info(s"Total Block Reward to Send: $blockReward")
    blockReward = blockReward - (blockReward % Parameters.MinFee)
    logger.info(s"Rounding block reward to minimum box amount: $blockReward")

    val newConfig = config.copy()
    newConfig.getParameters.getMetaConf.setMetadataId(metadataId.toString)

    ConfigHandler.writeConfig(AppParameters.configFilePath, newConfig)
    logger.info("Config file has been successfully updated")
    val membersSerialized = nextCommandBox.getMemberList.cValue.map(m => m._2)
    val feesSerialized = nextCommandBox.getPoolFees.cValue.map(f => f._2.toLong)
    val opsSerialized = nextCommandBox.getPoolOperators.cValue.map(o => o._2)


    logger.info("SmartPool Data now being built and inserted into database.")
    val txOutputs = signedTx.getOutputsToSpend.asScala

    val outputMap: Map[String, Long] = txOutputs.map{
      o => (Address.fromErgoTree(o.getErgoTree, nodeConf.getNetworkType).toString, o.getValue.toLong)
    }.toMap

    val smartPoolEntry = SmartPoolEntry(config.getParameters.getPoolId, txId, nextCommandBox.getCurrentEpoch,
      nextCommandBox.getCurrentEpochHeight, membersSerialized, feesSerialized, nextCommandBox.getPoolInfo.cValue,
      opsSerialized, smartPoolId.toString, blockHeights.map(h => h.toLong), nextCommandBox.getSubpoolId.toString)

    val consensusEntries = nextCommandBox.getMemberList.cValue.map{
      (memberVal: (Array[Byte], String)) =>
        val consensusValues = nextCommandBox.getShareConsensus.cValue.filter{
          c =>
            c._1 sameElements memberVal._1
        }.head
        ConsensusEntry(config.getParameters.getPoolId, txId, nextCommandBox.getCurrentEpoch, nextCommandBox.getCurrentEpochHeight,
          smartPoolId.toString, memberVal._2, consensusValues._2(0), consensusValues._2(1), consensusValues._2(2), outputMap.getOrElse(memberVal._2, 0L)
        , nextCommandBox.getSubpoolId.toString)
    }

    val smartPoolDataUpdate = new SmartPoolDataInsertion(dbConn)
    smartPoolDataUpdate.setVariables(smartPoolEntry).execute()
    var rowsInserted = 0L
    logger.info(s"Attempting to insert ${consensusEntries.length} entries into consensus table")
    consensusEntries.foreach{
      ce =>

        val consensusUpdate = new ConsensusInsertion(dbConn)
        rowsInserted = rowsInserted + consensusUpdate.setVariables(ce).execute()
    }
    logger.info(s"$rowsInserted rows were inserted!")

  }

  private def applyMinimumPayouts(dbConn: DatabaseConnection, memberList: MemberList, shareConsensus: ShareConsensus): ShareConsensus ={
    var newShareConsensus = ShareConsensus.fromConversionValues(shareConsensus.cValue)
    logger.info(s"Now querying minimum payouts for ${newShareConsensus.cValue.length} different members in the smart pool.")
    for(member <- memberList.cValue){
      val minimumPayoutsQuery = new MinimumPayoutsQuery(dbConn, paramsConf.getPoolId, member._2)
      val settingsResponse = minimumPayoutsQuery.setVariables().execute().getResponse
      logger.info(s"Minimum Payout For Address ${member._2}: ${settingsResponse.paymentthreshold}")
      if(settingsResponse.paymentthreshold > 0.1){
        val propBytesIndex = newShareConsensus.cValue.map(c => c._1).indexOf(member._1, 0)
        newShareConsensus = ShareConsensus.fromConversionValues(
          newShareConsensus.cValue.updated(propBytesIndex,
            (member._1, Array(newShareConsensus.cValue(propBytesIndex)._2(0), (BigDecimal(settingsResponse.paymentthreshold) * BigDecimal(Parameters.OneErg)).toLong, newShareConsensus.cValue(propBytesIndex)._2(2)))))
      }
    }
    logger.info("Minimum payouts updated for all smart pool members!")
    newShareConsensus
  }



}

