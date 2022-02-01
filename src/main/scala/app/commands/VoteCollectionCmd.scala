package app.commands

import app.{AppCommand, ExitCodes, exit}
import boxes.{BoxHelpers, RecordingInputBox}
import config.SmartPoolConfig
import contracts.command.CommandContract
import contracts.voting.{ProxyBallotContract, RecordingContract}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

class VoteCollectionCmd(config: SmartPoolConfig) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_VOTE_COLLECTION_CMD)

  override val appCommand: app.AppCommand.Value = AppCommand.VoteCollectionCmd
  private var voteTokenId: ErgoId = _
  private var recordingNFT: ErgoId = _
  private var recordingContract: RecordingContract = _
  private var commandContract: CommandContract = _

  private var secretStorage: SecretStorage = _
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    // Make sure smart pool id is not set
   // assert(voteConf.getVoteTokenId == "")

    assert(walletConf.getSecretStoragePath != "")

    logger.info(nodeConf.getNodeApi.getApiUrl)

    voteTokenId = ErgoId.create(voteConf.getVoteTokenId)

    secretStorage = SecretStorage.loadFrom(walletConf.getSecretStoragePath)
    secretStorage.unlock(nodeConf.getWallet.getWalletPass)
  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")


    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {


      val prover = ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
      var nodeAddress = prover.getAddress
      logger.info("Now printing EIP3 Addresses")
      for(i <- 0 to 10){
        try {
          logger.info(prover.getEip3Addresses.get(i).toString)
          nodeAddress = prover.getEip3Addresses.get(i)
        }
        catch {case exception: Exception => logger.warn(s"Could not find EIP address number $i for node wallet")}
      }


      logger.info("The following addresses must be exactly the same:")
      logger.info(s"Prover Address=${prover.getAddress}")
      logger.info(s"Node Address=${nodeAddress}")
      //assert(prover.getAddress == nodeAddress)
      val recordingNFT = ErgoId.create(voteConf.getPovTokenId)
      val recordingValue = metaConf.getMetadataValue


      val voteYes = ProxyBallotContract.generateContract(ctx, voteTokenId, voteYes=true, recordingNFT)
      val voteNo = ProxyBallotContract.generateContract(ctx, voteTokenId, voteYes=false, recordingNFT)

      logger.info("Vote Yes Address: " + voteYes.getAddress)
      logger.info("Vote No Address: " + voteNo.getAddress)

      recordingContract = RecordingContract.generateContract(ctx, voteTokenId, voteYes.getAddress, voteNo.getAddress)
      val recordingAddress = recordingContract.getAddress
      logger.info("Recording Address: " + recordingAddress)
      logger.info("Recording NFT: " + recordingNFT)
      logger.info("VoteTokenId: " + voteTokenId)

      val recordingBox = new RecordingInputBox(BoxHelpers.findExactTokenBox(ctx, recordingAddress, recordingNFT, 1L).get, recordingNFT)

      logger.info("Current RecordingBox: " + recordingBox.toString)

      val yesBoxes = ctx.getUnspentBoxesFor(voteYes.getAddress, 0, 100).asScala.toArray

      val noBoxes = ctx.getUnspentBoxesFor(voteNo.getAddress, 0, 100).asScala.toArray

      logger.info("Current YesBoxes: " + yesBoxes.length)
      logger.info("Current NoBoxes: " + noBoxes.length)

      val newYesVotes = yesBoxes
        .filter(ib => ib.getTokens.size() > 0)
        .filter(ib => ib.getTokens.get(0).getId == voteTokenId)
        .map(ib => ib.getTokens.get(0).getValue)
        .sum

      val newNoVotes = noBoxes
        .filter(ib => ib.getTokens.size() > 0)
        .filter(ib => ib.getTokens.get(0).getId == voteTokenId)
        .map(ib => ib.getTokens.get(0).getValue)
        .sum

      logger.info("Current YesVotes: " + newYesVotes)
      logger.info("Current NoVotes: " + newNoVotes)


      val recordingOutBox = RecordingContract.buildNextRecordingBox(ctx, recordingBox, recordingContract, yesBoxes, noBoxes, voteTokenId)
      val boxesToSpend = Array(recordingBox.asInput)++yesBoxes++noBoxes
      //var changeAddress = Address.create("CHANGE_ADDRY") //TODO Change this
      val changeAddress = nodeAddress
      val totalValue = BoxHelpers.sumBoxes(yesBoxes.toList) + BoxHelpers.sumBoxes(noBoxes.toList)
      val txFee = if(totalValue > paramsConf.getInitialTxFee) paramsConf.getInitialTxFee else totalValue
      logger.info("Recording Out Box: " + recordingOutBox)
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder

      // TODO: Remove constant values for tokens and place them into config

      val recordingTx =
        txB
          .boxesToSpend(boxesToSpend.toList.asJava)
          .fee(txFee)
          .outputs(recordingOutBox.asOutBox)
          .sendChangeTo(changeAddress.getErgoAddress)
          .tokensToBurn(new ErgoToken(voteTokenId, newNoVotes + newYesVotes))
          .build()

      val recTxSigned = prover.sign(recordingTx)
      val recTxId: String = ctx.sendTransaction(recTxSigned).filter(c => c !='\"')

      val nextRecordingBox = recordingOutBox.convertToInputWith(recTxId, 0)

      logger.info(s"Tx was successfully sent with id: $recTxId and cost: ${recTxSigned.getCost}")

      recTxSigned.toJson(true)
    })
    logger.info("Command has finished execution")
    exit(logger, ExitCodes.SUCCESS)
  }

  def recordToDb: Unit = {

    logger.info("Recording tx info into config...")

    logger.info("The following information will be updated:")



//    val newConfig = config.copy()
//    newConfig.getParameters.setSmartPoolId(smartPoolId.toString)
//    newConfig.getParameters.getMetaConf.setMetadataId(metadataId.toString)
//    newConfig.getParameters.getMetaConf.setMetadataAddress(metadataAddress.toString)
//    newConfig.getParameters.getHoldingConf.setHoldingAddress(holdingContract.getAddress.toString)
//    newConfig.getParameters.setPoolOperators(Array(commandContract.getAddress.toString))
//    newConfig.getParameters.getHoldingConf.setHoldingType("default")
//
//    ConfigHandler.writeConfig(AppParameters.configFilePath, newConfig)
    logger.info("Config file has been successfully updated")
  }


}

