package app.commands

import app.{AppCommand, ExitCodes, exit}
import boxes.MetadataInputBox
import configs.SmartPoolConfig
import contracts.holding
import contracts.holding.SimpleHoldingContract
import groups.{DistributionGroup, HoldingGroup}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import payments.{ShareCollector, SimplePPLNS, StandardPPLNS}
import persistence.entries.BoxIndexEntry
import persistence.{BoxIndex, BoxStatus, DatabaseConnection, PersistenceHandler}
import persistence.queries.{BlockByHeightQuery, BoxIndexQuery, BoxIndexQuery2, MinimumPayoutsQuery}
import persistence.responses.BoxIndexResponse
import registers.{MemberList, ShareConsensus}


class SendToHoldingCmd(config: SmartPoolConfig, blockHeight: Int) extends SmartPoolCmd(config) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_SEND_TO_HOLDING_CMD)
  private var blockReward = 0L
  val txFee: Long = Parameters.MinFee
  private var memberList: MemberList = _
  private var shareConsensus: ShareConsensus = _
  private var dbConn: DatabaseConnection = _
  private var smartPoolId: ErgoId = _
  override val appCommand: app.AppCommand.Value = AppCommand.SendToHoldingCmd
  private var completedMap: Map[MetadataInputBox, InputBox] = Map()
  private var boxIndex: BoxIndex = _
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    // Basic assertions

    require(holdConf.getHoldingAddress != "")
    require(holdConf.getHoldingType == "default")
    require(paramsConf.getSmartPoolId != "")
    require(metaConf.getMetadataId != "")

    logger.info("Creating connection to persistence database")
    val persistence = new PersistenceHandler(Some(config.getPersistence.getHost), Some(config.getPersistence.getPort), Some(config.getPersistence.getDatabase))
    persistence.setConnectionProperties(config.getPersistence.getUsername, config.getPersistence.getPassword, config.getPersistence.isSslConnection)

    dbConn = persistence.connectToDatabase
    logger.info("Now performing BlockByHeight Query")
    val blockQuery = new BlockByHeightQuery(dbConn, paramsConf.getPoolId, blockHeight.toLong)
    val block =  blockQuery.setVariables().execute().getResponse
    logger.info("Query executed successfully")
    logger.info(s"Block From Query: ")
    smartPoolId = ErgoId.create(paramsConf.getSmartPoolId)
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

    // Block must still be pending
    require(block.status == "confirmed")
    // Block must have full num of confirmations
    require(block.confirmationProgress == 1.0)
    // Assertions to make sure config is setup for command
    require(holdConf.getHoldingAddress != "")
    // Assume holding type is default for now
    require(holdConf.getHoldingType == "default")
    blockReward = (block.reward * Parameters.OneErg).toLong
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
    boxIndex = BoxIndex.fromDatabase(dbConn, paramsConf.getPoolId)


    ergoClient.execute((ctx: BlockchainContext) => {
      val metadataBoxes = boxIndex.getConfirmed.grabFromContext(ctx)
      val secretStorage = SecretStorage.loadFrom(walletConf.getSecretStoragePath)
      secretStorage.unlock(nodeConf.getWallet.getWalletPass)

      val prover = ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
      val nodeAddress = prover.getEip3Addresses.get(0)


      val holdingContract = new holding.SimpleHoldingContract(SimpleHoldingContract.generateHoldingContract(ctx, Address.create(metaConf.getMetadataAddress), ErgoId.create(paramsConf.getSmartPoolId)))
      val holdingGroup = new HoldingGroup(ctx, metadataBoxes, prover, nodeAddress, blockReward,
        holdingContract, config, shareConsensus, memberList)
      val executed = holdingGroup.buildGroup.executeGroup
      logger.info("Total Holding Groups: " + metadataBoxes.length)
      logger.info("Holding Groups Executed: " + executed.completed.size)
      logger.info("Holding Groups Failed: " + executed.failed.size)
      logger.info("Holding Groups Untouched: " + (metadataBoxes.length - (executed.failed.size + executed.completed.size)))
      completedMap = holdingGroup.boxes
    })
    logger.info("Command has finished execution")
  }

  def recordToDb: Unit = {

    logger.info("Now recording holding ids and values into box_index")
    boxIndex.writeInitiated(completedMap, Array(blockHeight))
  }

  def setBlockReward(reward: Long): Unit = {
    blockReward = reward
  }

  def applyMinimumPayouts(dbConn: DatabaseConnection, memberList: MemberList, shareConsensus: ShareConsensus): ShareConsensus ={
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

