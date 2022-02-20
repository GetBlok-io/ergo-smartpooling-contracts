package app.commands

import app.{AppCommand, AppParameters, ExitCodes, exit}
import boxes.{BoxHelpers, CommandInputBox, MetadataInputBox}
import configs.{ConfigHandler, SmartPoolConfig}
import contracts.command.{PKContract, VoteTokensContract}
import contracts.holding
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import payments.SimplePPLNS
import persistence.entries.{BoxIndexEntry, ConsensusEntry, SmartPoolEntry}
import persistence.queries.{BlockByHeightQuery, ConsensusByTransactionQuery, MinimumPayoutsQuery, PPLNSQuery, SmartPoolBySubpoolQuery}
import persistence.responses.{ShareResponse, SmartPoolResponse}
import persistence.writes.{BoxIndexUpdate, ConsensusDeletion, ConsensusInsertion, SmartPoolDataInsertion, SmartPoolDeletion}
import persistence.{DatabaseConnection, PersistenceHandler}
import registers.{MemberList, ShareConsensus}
import transactions.{CreateCommandTx, DistributionTx, RegroupTx}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try

class DistributeFailedCmd(config: SmartPoolConfig, subpoolid: Int) extends SmartPoolCmd(config) {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DISTRIBUTE_REWARDS_CMD)
  final val PPLNS_CONSTANT = 50000

  override val appCommand: app.AppCommand.Value = AppCommand.DistributeRewardsCmd
  private var smartPoolId: ErgoId = _
  private var metadataId: ErgoId = _
  private var holdingContract: HoldingContract = _
  private var blockReward: Long = 0L
  private var dbConn: DatabaseConnection = _
  private var memberList: MemberList = MemberList.convert(Array())
  private var shareConsensus: ShareConsensus = ShareConsensus.convert(Array())
  private var metadataBox: MetadataInputBox = _

  private var txId: String = _
  private var nextCommandBox: CommandInputBox = _
  private var signedTx: SignedTransaction = _
  private var blockHeight: Long = 0L
  private var subpoolResponse: SmartPoolResponse = _
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    logger.info("Attempting retrial of failed subpool")
    // Make sure smart pool ids are set
    require(paramsConf.getSmartPoolId != "")
    require(metaConf.getMetadataId != "")

    smartPoolId = ErgoId.create(paramsConf.getSmartPoolId)
    metadataId = ErgoId.create(metaConf.getMetadataId)
    blockReward = (BigDecimal(config.getFailure.getFailedValue) * Parameters.OneErg).toLong
    blockHeight = config.getFailure.getFailedBlock
//    blockHeight = 670816
//    blockReward = (BigDecimal(3.736) * Parameters.OneErg).toLong
    logger.info("Creating connection to persistence database")
    val persistence = new PersistenceHandler(Some(config.getPersistence.getHost), Some(config.getPersistence.getPort), Some(config.getPersistence.getDatabase))
    persistence.setConnectionProperties(config.getPersistence.getUsername, config.getPersistence.getPassword, config.getPersistence.isSslConnection)

    dbConn = persistence.connectToDatabase
    //logger.info(s"Performing BlockByHeight Query for ${blockHeights.length} blocks")

    // Assertions to make sure config is setup for command
    assert(holdConf.getHoldingAddress != "")
    // Assume holding type is default for now
    assert(holdConf.getHoldingType == "default")
    val subpoolList = new SmartPoolBySubpoolQuery(dbConn, paramsConf.getPoolId, subpoolid.toString).setVariables().execute().getResponse
    if(subpoolList.length == 0){
      logger.warn("That subpool could not be found!")
      exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
    }
    val subpool = subpoolList.head
    subpoolResponse = subpool
    logger.info(s"Subpool tx that failed: ${subpool.transactionHash}")
    val consensusList = new ConsensusByTransactionQuery(dbConn, paramsConf.getPoolId, subpool.transactionHash).setVariables().execute().getResponse

    for(c <- consensusList){
      val minerAddress = Address.create(c.miner)

      shareConsensus = ShareConsensus.convert(shareConsensus.cValue++Array(
        (minerAddress.getErgoAddress.script.bytes, Array(c.shares, c.minPayout, c.storedPayout))))
      memberList = MemberList.convert(memberList.cValue++Array((minerAddress.getErgoAddress.script.bytes, minerAddress.toString)))
    }


    logger.info(s"Total rewards from all blocks: ${blockReward}")

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
      val metadataInputs = ctx.getUnspentBoxesFor(Address.create(metaConf.getMetadataAddress), 0, 500).asScala

      for (mb <- metadataInputs) {
        if (mb.getValue == metaConf.getMetadataValue) {
          if (mb.getTokens.size() > 0) {
            if (mb.getTokens.get(0).getId.toString == paramsConf.getSmartPoolId) {

              //logger.info("Tokens " + mb.getTokens.asScala.toArray.mkString("Array(", ", ", ")"))
              val tempMetaBox = new MetadataInputBox(mb, ErgoId.create(paramsConf.getSmartPoolId))
              if (tempMetaBox.getSubpoolId == subpoolid) {
                metadataBox = tempMetaBox
                logger.info("Found metadata box from blockchain!")
                logger.info("Metadata Box: " + metadataBox)
              }
            }
          }

        }
      }


      val storedPayouts = metadataBox.getShareConsensus.cValue.map(c => c._2(2)).sum
      var holdingBoxesList = List[InputBox]()
      val holdingBox = BoxHelpers.findExactBox(ctx, holdingContract.getAddress, blockReward, holdingBoxesList).get
      holdingBoxesList = holdingBoxesList ++ List(holdingBox)
      if (storedPayouts > 0) {
        val storedPayoutBox = BoxHelpers.findExactBox(ctx, holdingContract.getAddress, storedPayouts, holdingBoxesList).get
        holdingBoxesList = holdingBoxesList ++ List(storedPayoutBox)
      }
      logger.info("Total holding boxes: " + holdingBoxesList.length)
      logger.info("Total held value: " + BoxHelpers.sumBoxes(holdingBoxesList))
      logger.info(metadataBox.toString)
      val voteTokenId = ErgoId.create(voteConf.getVoteTokenId)
      val commandTx = new CreateCommandTx(ctx.newTxBuilder())
      val commandContract = VoteTokensContract.generateContract(ctx,voteTokenId, nodeAddress)

      var inputBoxes = ctx.getWallet.getUnspentBoxes(cmdConf.getCommandValue + commandTx.txFee).get().asScala.toList
      val tokenBoxes = inputBoxes.filter(ib => ib.getTokens.asScala.exists(t => t.getId.toString == voteTokenId.toString))
      if(tokenBoxes.isEmpty){
        val newTokenBox = BoxHelpers.findExactTokenBox(ctx, nodeAddress, voteTokenId, 100)
        if(newTokenBox.isDefined){
          inputBoxes = inputBoxes++List(newTokenBox.get)
        }else{
          logger.error("Exact token box could not be found!")
          exit(logger, ExitCodes.COMMAND_FAILED)
        }
      }
      logger.warn("Using hard-coded command value and tx fee, ensure this value is added to configuration file later for more command box options")

        commandTx
          .metadataToCopy(metadataBox)
          .withCommandContract(commandContract)
          .commandValue(cmdConf.getCommandValue)
          .inputBoxes(inputBoxes: _*)
          .withHolding(holdingContract, holdingBoxesList)
          .withChange(nodeAddress)
          .setConsensus(shareConsensus)
          .setMembers(memberList)

      commandTx.cOB.tokens(new ErgoToken(voteTokenId, BoxHelpers.sumBoxes(holdingBoxesList)))
      val unsignedCommandTx = commandTx.buildCommandTx()
      val signedCmdTx = prover.sign(unsignedCommandTx)
      logger.info("Next command box" + commandTx.commandOutBox)
      logger.info("Command Tx successfully signed")

      val cmdTxId = ctx.sendTransaction(signedCmdTx)
      logger.info(s"Tx was successfully sent with id: $cmdTxId")
      //val exactCommandBox = BoxHelpers.findExactTokenBox(ctx, commandContract.getAddress, voteTokenId, BoxHelpers.sumBoxes(holdingBoxesList))
      val commandBox = new CommandInputBox(signedCmdTx.getOutputsToSpend.get(0), commandContract)
      logger.info("Now building DistributionTx using new command box...")
      logger.info(commandBox.toString)
      val distTx = new DistributionTx(ctx.newTxBuilder())
      val unsignedDistTx =
        distTx
          .metadataInput(metadataBox)
          .commandInput(commandBox)
          .holdingInputs(holdingBoxesList)
          .holdingContract(holdingContract)
          .operatorAddress(nodeAddress)
          .tokenToDistribute(commandBox.getTokens.get(0))
          .buildMetadataTx()
      val signedDistTx = prover.sign(unsignedDistTx)
      logger.info("Distribution Tx successfully signed.")

      txId = ctx.sendTransaction(signedDistTx).filter(c => c != '\"')
      nextCommandBox = commandBox

      logger.info(s"Tx successfully sent with id: $txId and cost: ${signedDistTx.getCost}")
      metadataId = signedDistTx.getOutputsToSpend.get(0).getId
      signedTx = signedDistTx
      val boxIndexEntry = BoxIndexEntry(paramsConf.getPoolId, metadataId.toString, txId,
        nextCommandBox.getCurrentEpoch, "success", smartPoolId.toString, nextCommandBox.getSubpoolId.toString, Array(blockHeight))
      val boxIndexUpdate = new BoxIndexUpdate(dbConn).setVariables(boxIndexEntry).execute()

      val smartpoolDeletion = new SmartPoolDeletion(dbConn).setVariables(subpoolResponse).execute()
      val consensusDeletion = new ConsensusDeletion(dbConn).setVariables(subpoolResponse).execute()

      val membersSerialized = nextCommandBox.getMemberList.cValue.map(m => m._2)
      val feesSerialized = nextCommandBox.getPoolFees.cValue.map(f => f._2.toLong)
      val opsSerialized = nextCommandBox.getPoolOperators.cValue.map(o => o._2)
      val outputMap: Map[String, Long] = signedDistTx.getOutputsToSpend.asScala.map{
        o => (Address.fromErgoTree(o.getErgoTree, nodeConf.getNetworkType).toString, o.getValue.toLong)
      }.toMap

      val smartPoolEntry = SmartPoolEntry(config.getParameters.getPoolId, txId ,nextCommandBox.getCurrentEpoch,
        nextCommandBox.getCurrentEpochHeight, membersSerialized, feesSerialized, nextCommandBox.getPoolInfo.cValue,
        opsSerialized, smartPoolId.toString, Array(blockHeight.toLong), nextCommandBox.getSubpoolId.toString)

      val consensusEntries =  nextCommandBox.getMemberList.cValue.map{
        (memberVal: (Array[Byte], String)) =>
          val consensusValues =  nextCommandBox.getShareConsensus.cValue.filter{
            c =>
              c._1 sameElements memberVal._1
          }.head
          ConsensusEntry(config.getParameters.getPoolId, txId, nextCommandBox.getCurrentEpoch, nextCommandBox.getCurrentEpochHeight,
            smartPoolId.toString, memberVal._2, consensusValues._2(0), consensusValues._2(1), consensusValues._2(2), outputMap.getOrElse(memberVal._2, 0L),
            nextCommandBox.getSubpoolId.toString)
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

      signedDistTx.toJson(true)
    })
    logger.info("Command has finished execution")

  }

  def recordToDb: Unit = {
    exit(logger, ExitCodes.SUCCESS)

  }

  private def applyMinimumPayouts(dbConn: DatabaseConnection, memberList: MemberList, shareConsensus: ShareConsensus): ShareConsensus ={
    var newShareConsensus = ShareConsensus.convert(shareConsensus.cValue)
    logger.info(s"Now querying minimum payouts for ${newShareConsensus.cValue.length} different members in the smart pool.")
    for(member <- memberList.cValue){
      val minimumPayoutsQuery = new MinimumPayoutsQuery(dbConn, paramsConf.getPoolId, member._2)
      val settingsResponse = minimumPayoutsQuery.setVariables().execute().getResponse
      logger.info(s"Minimum Payout For Address ${member._2}: ${settingsResponse.paymentthreshold}")
      if(settingsResponse.paymentthreshold > 0.1){
        val propBytesIndex = newShareConsensus.cValue.map(c => c._1).indexOf(member._1, 0)
        newShareConsensus = ShareConsensus.convert(
          newShareConsensus.cValue.updated(propBytesIndex,
            (member._1, Array(newShareConsensus.cValue(propBytesIndex)._2(0), (BigDecimal(settingsResponse.paymentthreshold) * BigDecimal(Parameters.OneErg)).toLong, newShareConsensus.cValue(propBytesIndex)._2(2)))))
      }
    }
    logger.info("Minimum payouts updated for all smart pool members!")
    newShareConsensus
  }


  def setFailedValues(failedVal: Double, failedBlock: Long): Unit ={
    blockReward = BigDecimal(failedVal * Parameters.OneErg).toLong
    blockHeight = failedBlock
  }
}

