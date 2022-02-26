package groups.chains

import boxes.{BoxHelpers, CommandInputBox, CommandOutBox, MetadataInputBox}
import configs.SmartPoolConfig
import contracts.command.{CommandContract, PKContract}
import contracts.holding.HoldingContract
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.slf4j.{Logger, LoggerFactory}
import persistence.models.Models.BoxEntry
import registers.{MemberList, PoolFees, PoolOperators, ShareConsensus}
import transactions.{CreateCommandTx, DistributionTx}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try

class DistributionChain(ctx: BlockchainContext, boxToCommand: Map[MetadataInputBox, CommandInputBox], boxToHolding: Map[MetadataInputBox, InputBox],
                        boxToStorage: Map[MetadataInputBox, InputBox], boxToShare: Map[MetadataInputBox, ShareConsensus],
                        prover: ErgoProver, address: Address, holdingContract: HoldingContract, config: SmartPoolConfig){

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DIST_CHAIN)
  private[this] var _completed = Map.empty[MetadataInputBox, String]
  private[this] var _failed = Map.empty[MetadataInputBox, String]

  private val cmdConf = config.getParameters.getCommandConf
  private val metaConf = config.getParameters.getMetaConf
  private val holdConf = config.getParameters.getHoldingConf

  private var boxToTx = Map.empty[MetadataInputBox, SignedTransaction]


  var tokenAssigned: String = ""

  def executeChain: DistributionChain = {
    logger.info("Now executing DistributionChain")
    for(metadataBox <- boxToShare.keys){
      val distributionChain = Try {

        Thread.sleep(500)
        val commandBox = boxToCommand(metadataBox)
        logger.info("Now building DistributionTx using new command box...")
        logger.info("Command Box: " + commandBox.toString)
        logger.info("Metadata Box: " + metadataBox.toString)

        var holdingInputs = List(boxToHolding(metadataBox))
        if (boxToStorage.contains(metadataBox)) {
          logger.info("Exact storage box with value " + boxToStorage(metadataBox).getValue + " is being used")
          holdingInputs = holdingInputs ++ List(boxToStorage(metadataBox))
        }

        logger.info("Initial holding inputs made!")

        logger.info("Total Holding Input Value: " + BoxHelpers.sumBoxes(holdingInputs))
        val distTx = new DistributionTx(ctx.newTxBuilder())
        val unbuiltDistTx =
          distTx
            .metadataInput(metadataBox)
            .commandInput(commandBox)
            .holdingInputs(holdingInputs)
            .holdingContract(holdingContract)
            .operatorAddress(address)


        if(tokenAssigned != "" && metadataBox.getPoolOperators.cValue.exists(o => o._1 sameElements commandBox.contract.getErgoTree.bytes)){
          logger.info("VoteTokenId set, now adding tokens to distribute to distribution tx.")
          unbuiltDistTx.tokenToDistribute(commandBox.getTokens.get(0))
        }

        val unsignedDistTx = unbuiltDistTx.buildMetadataTx()
        val signedDistTx = prover.sign(unsignedDistTx)

        logger.info(s"Signed Tx Num Bytes Cost: ${
          signedDistTx.toBytes.length
        }")

        logger.info(s"Signed Tx Cost: ${signedDistTx.getCost}")
        val distAsErgoBox = signedDistTx.getOutputsToSpend.get(0).asInstanceOf[InputBoxImpl].getErgoBox

        logger.info("Total ErgoBox Bytes: " + distAsErgoBox.bytes.length)


        val signedTx = signedDistTx
        logger.info("Distribution Tx successfully signed.")
        val txId = ctx.sendTransaction(signedDistTx).filter(c => c != '\"')
        logger.info(s"Tx successfully sent with id: $txId and cost: ${signedDistTx.getCost}")
        val newMetadataBox = new MetadataInputBox(signedDistTx.getOutputsToSpend.get(0), metadataBox.getSmartPoolId)
        signedDistTx.toJson(true)
        _completed = _completed ++ Map((newMetadataBox, txId))
        boxToTx = boxToTx ++ Map((newMetadataBox, signedDistTx))
        logger.info("Now waiting for 0.5secs")
        Thread.sleep(500)
      }

      if(distributionChain.isFailure) {
        logger.warn(s"Exception caught for metadata box ${metadataBox.getId.toString}")
        logger.info(distributionChain.failed.get.getMessage)

        logger.warn("Now adding metadata box to failure list")
        _failed = _failed ++ Map((metadataBox, BoxEntry.DIST_TX))
        removeFromMaps(metadataBox)
      }

    }
    this
  }

  def result: Map[MetadataInputBox, SignedTransaction] = boxToTx
  def completed: Map[MetadataInputBox, String] = _completed
  def failed: Map[MetadataInputBox, String] = _failed

  def removeFromMaps(metadataInputBox: MetadataInputBox): Unit = {
    boxToTx = boxToTx -- Array(metadataInputBox)
  }



}