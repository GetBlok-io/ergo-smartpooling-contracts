package app.commands

import app.{AppCommand, ExitCodes, exit}
import boxes.MetadataInputBox
import configs.SmartPoolConfig
import contracts.command.VoteTokensContract
import contracts.holding
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import groups.{DistributionGroup, DistributionGroup2}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import payments.{ShareCollector, SimplePPLNS, StandardPPLNS}
import persistence.entries.{BoxIndexEntry, ConsensusEntry, SmartPoolEntry}
import persistence.queries.{BlockByHeightQuery, BoxIndexQuery, MinimumPayoutsQuery}
import persistence.writes.{BoxIndexUpdate, ConsensusInsertion, SmartPoolDataInsertion}
import persistence.{BoxIndex, DatabaseConnection, PersistenceHandler}
import registers.{MemberList, PoolFees, ShareConsensus}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, mapAsScalaMapConverter, seqAsJavaListConverter}
import scala.util.Try


class DistributeRewardsCmd2(config: SmartPoolConfig, blockHeight: Int) extends SmartPoolCmd(config) {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DISTRIBUTE_REWARDS_CMD)


  override val appCommand: app.AppCommand.Value = AppCommand.DistributeRewardsCmd
  private var smartPoolId: ErgoId = _
  private var metadataId: ErgoId = _
  private var holdingContract: HoldingContract = _
  private var blockReward: Long = 0L

  private var memberList: MemberList = _
  private var shareConsensus: ShareConsensus = _

  private var dbConn: DatabaseConnection = _

  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    // Make sure smart pool ids are set
    logger.info(s"DistributeRewardsCmd with blockHeight $blockHeight")

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

    require(block.status == "confirmed", "Block status is not confirmed")

    // Assertions to make sure config is setup for command
    require(holdConf.getHoldingAddress != "", "Holding address is not defined")
    // Assume holding type is default for now
    require(holdConf.getHoldingType == "default", "Holding type must be default")

    blockReward = (block.reward * Parameters.OneErg).toLong

    val initBoxIdx = BoxIndex.fromDatabase(dbConn, paramsConf.getPoolId)
    if(initBoxIdx.getUsed.size == initBoxIdx.getUsed.getConfirmed.size){
      // All used blocks = all used + confirmed blocks, so all subpools in use are confirmed
      if(initBoxIdx.getUsed.getConfirmed.getByBlock(blockHeight).size == 0){
        // All used + confirmed blocks have not distributed a block at this height
        if(initBoxIdx.getUsed.getConfirmed.getByBlock(initBoxIdx.getUsed.boxes.head._2.blocks(0)).size == initBoxIdx.getUsed.getConfirmed.size){
          // All used + confirmed blocks have distributed the same block
          // Now we can initiate holding command
          val sendToHoldingCmd = new SendToHoldingCmd(config, blockHeight)
          sendToHoldingCmd.initiateCommand
          sendToHoldingCmd.executeCommand
          sendToHoldingCmd.recordToDb
        }
      }
    }

    if(config.getNode.getNetworkType == NetworkType.MAINNET) {
      val shares = ShareCollector.queryToWindow(dbConn, paramsConf.getPoolId, blockHeight)
      val lastShare = shares.last.last
      logger.info(s"Last share height and time: ${lastShare.height}  ${lastShare.created.toString}")
      val commandInputs = StandardPPLNS.standardPPLNSToConsensus(shares)
      val tempConsensus = commandInputs._1

      memberList = commandInputs._2
      shareConsensus = applyMinimumPayouts(dbConn, memberList, tempConsensus)
    }else {
      // We calculate share scores in a simpler manner in testnet to account for differences in difficulty and number
      // of miners
      val shares = ShareCollector.querySharePage(dbConn, paramsConf.getPoolId, blockHeight)
      val lastShare = shares.last
      logger.info(s"Last share height and time: ${lastShare.height} | ${lastShare.created.toString}")
      val commandInputs = SimplePPLNS.simplePPLNSToConsensus(shares)
      val tempConsensus = commandInputs._1

      memberList = commandInputs._2
      shareConsensus = applyMinimumPayouts(dbConn, memberList, tempConsensus)
    }
    logger.info(s"Share consensus and member list with ${shareConsensus.nValue.size} unique addresses have been built")
    logger.info(shareConsensus.nValue.toString())
    logger.info(memberList.nValue.toString())

  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")
    logger.info(s"Total Block Reward to Send: $blockReward")
    blockReward = blockReward - (blockReward % Parameters.MinFee)
    logger.info(s"Rounding block reward to minimum box amount: $blockReward")
    require(blockReward != 0, "Block reward was 0")
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

      val voteTokenId = ErgoId.create(voteConf.getVoteTokenId)
      val commandContract = VoteTokensContract.generateContract(ctx, voteTokenId, nodeAddress)

      logger.info(s"Command Contract: ${commandContract.getAddress}")
      logger.info("Pool Fees Map: ")
      val feeMap = paramsConf.getFees.asScala.map{
        f =>
          val address = Address.create(f._1)
          logger.info(f._1 + " | " + f._2)
          (address.getErgoAddress.script.bytes, (f._2 * 10).toInt)
      }.toArray
      val poolFees = PoolFees.convert(feeMap)

      val totalIdx = BoxIndex.fromDatabase(dbConn, paramsConf.getPoolId)
      var boxIndex = totalIdx.getInitiated
      var isFailureAttempt = false // Boolean that determines whether or not this distribution chain is resending txs for a failed attempt.
      if(totalIdx.getFailed.boxes.nonEmpty || totalIdx.getSuccessful.boxes.nonEmpty){
        isFailureAttempt = true
        boxIndex = totalIdx.getUsed

      }

      logger.warn("Using hard-coded command value and tx fee, ensure this value is added to configuration file later for more command box options")
      val distributionGroup = new DistributionGroup2(ctx, boxIndex, prover, nodeAddress,
        holdingContract, commandContract, config, shareConsensus, memberList, poolFees, isFailureAttempt)
      val executed = distributionGroup.buildGroup.executeGroup

      logger.info("Total Distribution Groups: " + boxIndex.boxes.size)
      logger.info("Distribution Groups Executed: " + executed.completed.size)
      logger.info("Distribution Groups Failed: " + executed.failed.size)
      logger.info("Distribution Groups Untouched: " + (totalIdx.boxes.size - (executed.failed.size + executed.completed.size)))

      if(boxIndex.boxes.size != executed.completed.size){
        logger.info("Failures were found!")
        totalIdx.writeFailures(executed.failed, Array(blockHeight))
      }
      logger.info("Now evaluating completed boxes...")
      for(boxPair <- executed.completed){
        val metadataInputBox = boxPair._1
        totalIdx.writeSuccessful(executed.completed, Array(blockHeight))

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
    var newShareConsensus = ShareConsensus.convert(shareConsensus.cValue)
    logger.info(s"Now querying minimum payouts for ${newShareConsensus.cValue.length} different members in the smart pool.")
    for(member <- memberList.cValue){
      val minimumPayoutsQuery = new MinimumPayoutsQuery(dbConn, paramsConf.getPoolId, member._2)
      val settingsResponse = minimumPayoutsQuery.setVariables().execute().getResponse
      var shareScore = 0L
      if(settingsResponse.paymentthreshold > 0.1){
        val propBytesIndex = newShareConsensus.cValue.map(c => c._1).indexOf(member._1, 0)
        shareScore = newShareConsensus.cValue(propBytesIndex)._2(0)
        newShareConsensus = ShareConsensus.convert(
          newShareConsensus.cValue.updated(propBytesIndex,
            (member._1, Array(newShareConsensus.cValue(propBytesIndex)._2(0), (BigDecimal(settingsResponse.paymentthreshold) * BigDecimal(Parameters.OneErg)).toLong, newShareConsensus.cValue(propBytesIndex)._2(2)))))
      }
      logger.info(s"Minimum Payout And Shares For Address ${member._2}: ${settingsResponse.paymentthreshold}")
    }
    logger.info("Minimum payouts updated for all smart pool members!")
    newShareConsensus
  }



}

