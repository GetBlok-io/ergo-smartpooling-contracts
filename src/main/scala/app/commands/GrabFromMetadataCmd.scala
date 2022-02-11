package app.commands

import app.{AppCommand, ExitCodes, exit}
import boxes.{CommandInputBox, MetadataInputBox}
import config.SmartPoolConfig
import contracts.holding
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import explorer.ExplorerHandler
import logging.LoggingHandler
import node.NodeScanner
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.{BlockchainContextBase, ErgoTreeContract}
import org.ergoplatform.explorer.client.ExplorerApiClient
import org.ergoplatform.restapi.client.ApiClient
import org.slf4j.{Logger, LoggerFactory}
import persistence.{DatabaseConnection, PersistenceHandler}
import persistence.entries.{BoxIndexEntry, ConsensusEntry, SmartPoolEntry}
import persistence.responses.SmartPoolResponse
import persistence.writes.{BoxIndexUpdate, ConsensusInsertion, SmartPoolDataInsertion}
import registers.{MemberList, ShareConsensus}
import sigmastate.Values.ErgoTree
import sigmastate.eval.CostingSigmaDslBuilder.Colls
import sigmastate.serialization.ErgoTreeSerializer

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class GrabFromMetadataCmd(config: SmartPoolConfig, subpoolId: Int) extends SmartPoolCmd(config) {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_BOX_HELPER)

  override val appCommand: app.AppCommand.Value = AppCommand.DistributeRewardsCmd
  val smartPoolId: ErgoId = ErgoId.create(paramsConf.getSmartPoolId)
  private var explorerHandler: ExplorerHandler = _
  private var dbConn: DatabaseConnection = _
  def initiateCommand: Unit = {
    logger.info(s"Grabbing metadata for subpool $subpoolId")
    val persistence = new PersistenceHandler(Some(config.getPersistence.getHost), Some(config.getPersistence.getPort), Some(config.getPersistence.getDatabase))
    persistence.setConnectionProperties(config.getPersistence.getUsername, config.getPersistence.getPassword, config.getPersistence.isSslConnection)
    val explorerApiClient = new ExplorerApiClient(explorerUrl)
    explorerHandler = new ExplorerHandler(explorerApiClient)
    dbConn = persistence.connectToDatabase

  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")
    val txid = "6e7104610cbc84c5f5a088f3b9804f917e6ba18e5bc40286143d312275375576"
    val txInfo = explorerHandler.getTxOutputs(txid)
    val blockHeight = config.getFailure.getFailedBlock
    if(txInfo.isDefined) {
      ergoClient.execute(ctx => {
        val metadataInputBox = new MetadataInputBox(ctx.getBoxesById("47f85f723d638231ca63d0f17c6c8e7e375801c7b816d7ef88d4ea1eea85bb65").head, smartPoolId)
        logger.info("Making new box update for subpoolId: " + metadataInputBox.getSubpoolId.toString + " with status success.")
        logger.info("txId: " + txid)
        val boxIndexEntry = BoxIndexEntry(paramsConf.getPoolId, metadataInputBox.getId.toString, txid,
          metadataInputBox.getCurrentEpoch, "success", smartPoolId.toString, metadataInputBox.getSubpoolId.toString, Array(blockHeight))
        val boxIndexUpdate = new BoxIndexUpdate(dbConn).setVariables(boxIndexEntry).execute()

        logger.info("SmartPool Data now being built and inserted into database.")
        val ergoTreeSerializer = new ErgoTreeSerializer()
        val membersSerialized = metadataInputBox.getMemberList.cValue.map(m => m._2)
        val feesSerialized = metadataInputBox.getPoolFees.cValue.map(f => f._2.toLong)
        val opsSerialized = metadataInputBox.getPoolOperators.cValue.map(o => o._2)
        val outputMap: Map[String, Long] = txInfo.get.getOutputs.map {
          o =>
            logger.info(s"Adding address ${o.getAddress} with value ${o.getValue}")
            (o.getAddress, o.getValue)
        }.toMap

        val smartPoolEntry = SmartPoolEntry(config.getParameters.getPoolId, txid, metadataInputBox.getCurrentEpoch,
          metadataInputBox.getCurrentEpochHeight, membersSerialized, feesSerialized, metadataInputBox.getPoolInfo.cValue,
          opsSerialized, smartPoolId.toString, Array(blockHeight.toLong), metadataInputBox.getSubpoolId.toString)

        val consensusEntries = metadataInputBox.getMemberList.cValue.map {
          (memberVal: (Array[Byte], String)) =>
            val consensusValues = metadataInputBox.getShareConsensus.cValue.filter {
              c =>
                c._1 sameElements memberVal._1
            }.head
            ConsensusEntry(config.getParameters.getPoolId, txid, metadataInputBox.getCurrentEpoch, metadataInputBox.getCurrentEpochHeight,
              smartPoolId.toString, memberVal._2, consensusValues._2(0), consensusValues._2(1), consensusValues._2(2), outputMap.getOrElse(memberVal._2, 0L),
              metadataInputBox.getSubpoolId.toString)
        }

        val smartPoolDataUpdate = new SmartPoolDataInsertion(dbConn)
        smartPoolDataUpdate.setVariables(smartPoolEntry).execute()
        var rowsInserted = 0L
        logger.info(s"Attempting to insert ${consensusEntries.length} entries into consensus table")
        consensusEntries.foreach {
          ce =>

            val consensusUpdate = new ConsensusInsertion(dbConn)
            rowsInserted = rowsInserted + consensusUpdate.setVariables(ce).execute()
        }
        logger.info(s"$rowsInserted rows were inserted!")


      })
    }
    exit(logger, ExitCodes.SUCCESS)

  }

  def recordToDb: Unit = {


  }




}

