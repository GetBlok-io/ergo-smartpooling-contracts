package app.commands

import app.{AppCommand, AppParameters, ExitCodes, exit}
import boxes.{BoxHelpers, CommandInputBox, MetadataInputBox}
import config.{ConfigHandler, SmartPoolConfig}
import contracts.command.PKContract
import contracts.holding
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.slf4j.{Logger, LoggerFactory}
import payments.{SimplePPLNS, StandardPPLNS}
import persistence.entries.{BoxIndexEntry, ConsensusEntry, PaymentEntry, SmartPoolEntry}
import persistence.queries.{BlockByHeightQuery, BoxIndexQuery, MinimumPayoutsQuery, PPLNSQuery}
import persistence.responses.ShareResponse
import persistence.writes.{BoxIndexUpdate, ConsensusInsertion, PaymentInsertion, SmartPoolDataInsertion}
import persistence.{DatabaseConnection, PersistenceHandler}
import registers.{MemberList, PoolFees, ShareConsensus}
import transactions.groups.DistributionGroup
import transactions.{CreateCommandTx, DistributionTx, RegroupTx}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try



class DistributeRewardsCmd(config: SmartPoolConfig, blockHeight: Int) extends SmartPoolCmd(config) {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DISTRIBUTE_REWARDS_CMD)
  final val PPLNS_CONSTANT = 50000

  override val appCommand: app.AppCommand.Value = AppCommand.DistributeRewardsCmd
  private var smartPoolId: ErgoId = _
  private var metadataId: ErgoId = _
  private var holdingContract: HoldingContract = _
  private var blockReward: Long = 0L

  private var memberList: MemberList = _
  private var shareConsensus: ShareConsensus = _

  private var txId: String = _
  private var nextCommandBox: CommandInputBox = _
  private var signedTx: SignedTransaction = _
  private var dbConn: DatabaseConnection = _

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
    logger.info("Now performing BlockByHeight Query")
    val blockQuery = new BlockByHeightQuery(dbConn, paramsConf.getPoolId, blockHeight.toLong)
    val block =  blockQuery.setVariables().execute().getResponse
    logger.info("Query executed successfully")
    logger.info(s"Block From Query: ")

    if(block == null){
      logger.error("Block is null")
      exit(logger, ExitCodes.COMMAND_FAILED)
    }

    // Lets ensure that blocks are only set to confirmed once we pay them out.
    // TODO: CHANGE BACK AFTER TEST
   require(block.status == "confirmed", "Block status is not confirmed")

    // Assertions to make sure config is setup for command
    require(holdConf.getHoldingAddress != "", "Holding address is not defined")
    // Assume holding type is default for now
    require(holdConf.getHoldingType == "default", "Holding type must be default")

    blockReward = (block.reward * Parameters.OneErg).toLong

    var totalShareScore = BigDecimal("0")
    var shares = Array[Array[ShareResponse]]()
    while(totalShareScore < 0.5) {
      logger.info("Now performing PPLNS Query to page shares!")
      val pplnsQuery = new PPLNSQuery(dbConn, paramsConf.getPoolId, blockHeight, PPLNS_CONSTANT)
      val response = pplnsQuery.setVariables().execute().getResponse
      shares = shares++Array(response)
      totalShareScore = response.map(s => (s.diff * BigDecimal("256") / s.netDiff)).sum + totalShareScore
      logger.info("totalShareScore: " + totalShareScore)
      logger.info("Query executed successfully")
    }

   val commandInputs = StandardPPLNS.standardPPLNSToConsensus(shares)
    val tempConsensus = commandInputs._1
    memberList = commandInputs._2

    shareConsensus = applyMinimumPayouts(dbConn, memberList, tempConsensus)

    logger.info(s"Share consensus and member list with ${shareConsensus.nValue.size} unique addresses have been built")
    logger.info(shareConsensus.nValue.toString())
    logger.info(memberList.nValue.toString())



  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")
    logger.info(s"Total Block Reward to Send: $blockReward")
    blockReward = blockReward - (blockReward % Parameters.MinFee)
    logger.info(s"Rounding block reward to minimum box amount: $blockReward")
    ergoClient.execute((ctx: BlockchainContext) => {

      val secretStorage = SecretStorage.loadFrom(walletConf.getSecretStoragePath)
      secretStorage.unlock(nodeConf.getWallet.getWalletPass)

      val prover = ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
      val nodeAddress = prover.getEip3Addresses.get(0)
      holdingContract = new holding.SimpleHoldingContract(SimpleHoldingContract.generateHoldingContract(ctx, Address.create(metaConf.getMetadataAddress), ErgoId.create(paramsConf.getSmartPoolId)))
      logger.info("Holding Address: " + holdingContract.getAddress.toString)

      logger.info(s"Prover Address=${prover.getAddress}")
      logger.info(s"Node Address=${nodeAddress}")

      logger.warn("Using hard-coded PK Command Contract, ensure this value is added to configuration file later for more command box options")
      logger.info("Now attempting to retrieve metadata box from blockchain")

      val boxIndex = new BoxIndexQuery(dbConn).setVariables().execute().getResponse
      var isFailureAttempt = false // Boolean that determines whether or not this distribution chain is resending txs for a failed attempt.
      var metaIds = Array[String]()
      var failureIds = Array[String]()
      if(boxIndex.forall(br => br.status == "success")){
        metaIds = boxIndex.map(br => br.boxId)
      }else{
        metaIds = boxIndex.map(br => br.boxId)
        logger.warn(s"Failure retrial for ${metaIds.length} subpools")
        isFailureAttempt = true
        failureIds =  boxIndex.filter(b => b.status == "failure").map(b => b.boxId)
      }
      val metadataRetrieval = Try{ctx.getBoxesById(metaIds:_*)}
      if(metadataRetrieval.isFailure)
        exit(logger, ExitCodes.NOT_ALL_SUBPOOLS_RETRIEVED)
      val metaInputs = metadataRetrieval.get

      val metadataBoxes = metaInputs.map(b => new MetadataInputBox(b, smartPoolId))
      val storedPayoutsList = for(m <- metadataBoxes) yield m.getShareConsensus.cValue.map(c => c._2(2)).sum
      val storedPayouts = storedPayoutsList.sum
      logger.info("Stored Payouts found: " +  storedPayouts)
      if(storedPayouts > 0) {
        val isHoldingCovered = Try(ctx.getCoveringBoxesFor(holdingContract.getAddress, storedPayouts, List[ErgoToken]().asJava).isCovered)

        if (isHoldingCovered.isFailure) {
          exit(logger, ExitCodes.HOLDING_NOT_COVERED)
        } else {
          if (isHoldingCovered.get)
            logger.info("Holding address has enough ERG to cover transaction")
          else
            exit(logger, ExitCodes.HOLDING_NOT_COVERED)
        }
      }

      var holdingBoxes = ctx.getUnspentBoxesFor(holdingContract.getAddress, 0, 30).asScala.filter(i => i.getValue == blockReward).toList

      logger.warn("Using hard-coded command value and tx fee, ensure this value is added to configuration file later for more command box options")
      val distributionGroup = new DistributionGroup(ctx, metadataBoxes, prover, nodeAddress, blockReward,
        holdingContract, config, shareConsensus, memberList, isFailureAttempt, failureIds)
      val executed = distributionGroup.buildGroup.executeGroup
      logger.info("Total Distribution Groups: " + metadataBoxes.length)
      logger.info("Distribution Groups Executed: " + executed.completed.size)
      logger.info("Distribution Groups Failed: " + executed.failed.size)
      logger.info("Distribution Groups Untouched: " + (metadataBoxes.length - (executed.failed.size + executed.completed.size)))

      if(metadataBoxes.length != executed.completed.size){
        logger.info("Failures were found!")
        for(boxPair <- executed.failed){
          val metadataInputBox = boxPair._1
          logger.info("Making new box update for subpoolId: " +  metadataInputBox.getSubpoolId.toString + " with status failure.")
          logger.info("txId: " +  boxPair._2)
          val boxIndexEntry = BoxIndexEntry(paramsConf.getPoolId, metadataInputBox.getId.toString, boxPair._2,
            metadataInputBox.getCurrentEpoch, "failure", smartPoolId.toString, metadataInputBox.getSubpoolId.toString, Array(blockHeight))
          val boxIndexUpdate = new BoxIndexUpdate(dbConn).setVariables(boxIndexEntry).execute()
        }
      }
      logger.info("Now evaluating completed boxes...")
      for(boxPair <- executed.completed){
        val metadataInputBox = boxPair._1
        logger.info("Making new box update for subpoolId: " +  metadataInputBox.getSubpoolId.toString + " with status success.")
        logger.info("txId: " +  boxPair._2)
        val boxIndexEntry = BoxIndexEntry(paramsConf.getPoolId, metadataInputBox.getId.toString, boxPair._2,
          metadataInputBox.getCurrentEpoch, "success", smartPoolId.toString, metadataInputBox.getSubpoolId.toString, Array(blockHeight))
        val boxIndexUpdate = new BoxIndexUpdate(dbConn).setVariables(boxIndexEntry).execute()

        logger.info("SmartPool Data now being built and inserted into database.")

        val membersSerialized = metadataInputBox.getMemberList.cValue.map(m => m._2)
        val feesSerialized = metadataInputBox.getPoolFees.cValue.map(f => f._2.toLong)
        val opsSerialized = metadataInputBox.getPoolOperators.cValue.map(o => o._2)
        val outputMap: Map[String, Long] = distributionGroup.successfulTxs(boxPair._1).getOutputsToSpend.asScala.map{
          o => (Address.fromErgoTree(o.getErgoTree, nodeConf.getNetworkType).toString, o.getValue.toLong)
        }.toMap

        val smartPoolEntry = SmartPoolEntry(config.getParameters.getPoolId, boxPair._2,metadataInputBox.getCurrentEpoch,
          metadataInputBox.getCurrentEpochHeight, membersSerialized, feesSerialized, metadataInputBox.getPoolInfo.cValue,
          opsSerialized, smartPoolId.toString, Array(blockHeight.toLong), metadataInputBox.getSubpoolId.toString)

        val consensusEntries =  boxPair._1.getMemberList.cValue.map{
          (memberVal: (Array[Byte], String)) =>
            val consensusValues =  boxPair._1.getShareConsensus.cValue.filter{
              c =>
                c._1 sameElements memberVal._1
            }.head
            ConsensusEntry(config.getParameters.getPoolId, boxPair._2, metadataInputBox.getCurrentEpoch, metadataInputBox.getCurrentEpochHeight,
              smartPoolId.toString, memberVal._2, consensusValues._2(0), consensusValues._2(1), consensusValues._2(2), outputMap.getOrElse(memberVal._2, 0L),
              metadataInputBox.getSubpoolId.toString)
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



    })
    logger.info("Command has finished execution")
  }

  def recordToDb: Unit = {
    logger.info("Recording tx info into config...")

    logger.info("The following information will be updated:")
   // logger.info(s"Metadata Id: ${metaConf.getMetadataId}(old) -> $metadataId")


   // ConfigHandler.writeConfig(AppParameters.configFilePath, newConfig)
    logger.info("Config file has been successfully updated")



    exit(logger, ExitCodes.SUCCESS)
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

