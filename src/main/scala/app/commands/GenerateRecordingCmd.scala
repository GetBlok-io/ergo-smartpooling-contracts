package app.commands

import app.{AppCommand, AppParameters, ExitCodes, exit}
import config.{ConfigHandler, SmartPoolConfig}
import contracts.command.{CommandContract, PKContract}
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import contracts.voting.{ProxyBallotContract, RecordingContract}
import contracts.{MetadataContract, holding}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.slf4j.LoggerFactory
import transactions.GenesisTx

import scala.collection.JavaConverters.seqAsJavaListConverter

class GenerateRecordingCmd(config: SmartPoolConfig) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_GEN_RECORDING_CMD)

  override val appCommand: app.AppCommand.Value = AppCommand.GenerateRecordingCmd
  private var voteTokenId: ErgoId = _
  private var recordingNFT: ErgoId = _
  private var recordingContract: RecordingContract = _
  private var commandContract: CommandContract = _

  private var secretStorage: SecretStorage = _
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    // Make sure smart pool id is not set
    //assert(voteConf.getVoteTokenId == "")

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

      val recordingValue = metaConf.getMetadataValue
      val txFee = paramsConf.getInitialTxFee
      val boxesToSpend = ctx.getWallet.getUnspentBoxes(recordingValue + (txFee * 2)).get()
      recordingNFT = boxesToSpend.get(0).getId
      val voteYes = ProxyBallotContract.generateContract(ctx, voteTokenId, voteYes=true, recordingNFT)
      val voteNo = ProxyBallotContract.generateContract(ctx, voteTokenId, voteYes=false, recordingNFT)

      logger.info("Vote Yes Address: " + voteYes.getAddress)
      logger.info("Vote No Address: " + voteNo.getAddress)

      recordingContract = RecordingContract.generateContract(ctx, voteTokenId, voteYes.getAddress, voteNo.getAddress)
      val recordingAddress = recordingContract.getAddress
      logger.info("Recording Address: " + recordingAddress)
      logger.info("Recording NFT: " + recordingNFT)
      logger.info("VoteTokenId: " + voteTokenId)

      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      val outB = txB.outBoxBuilder()
      val recordingToken = new ErgoToken(recordingNFT, 1)
      // TODO: Remove constant values for tokens and place them into config
      val tokenBox = outB
        .value(recordingValue + (txFee))
        .mintToken(recordingToken, "GetBlok.io Recording Box Token", "This token identifies this box to be a part of GetBlok.io's proof-of-vote protocol.", 0)
        .contract(new ErgoTreeContract(nodeAddress.getErgoAddress.script))
        .build()

      val tokenTx =
        txB
          .boxesToSpend(boxesToSpend)
          .fee(txFee)
          .outputs(tokenBox)
          .sendChangeTo(nodeAddress.getErgoAddress)
          .build()

      val tokenTxSigned = prover.sign(tokenTx)
      val tokenTxId: String = ctx.sendTransaction(tokenTxSigned).filter(c => c !='\"')

      val tokenInputBox = tokenBox.convertToInputWith(tokenTxId, 0)
      val inputBoxes = List(tokenInputBox)

      logger.info("Parameters given:")
      logger.info(s"RecordingValue=${recordingValue / Parameters.OneErg} ERG")
      logger.info(s"TxFee=${txFee.toDouble / Parameters.OneErg} ERG")
      logger.info("Now building transaction...")
      val recordingOutput = RecordingContract.buildNewRecordingBox(ctx, recordingNFT, recordingContract, recordingValue)
      val recordingTx = ctx.newTxBuilder()
      val unsignedTx =
        recordingTx
          .boxesToSpend(inputBoxes.asJava)
          .outputs(recordingOutput.asOutBox)
          .fee(txFee)
          .sendChangeTo(nodeAddress.getErgoAddress)
        .build()
      logger.info("Tx has been built")



      logger.info("Tx is now being signed...")

      val signedTx = prover.sign(unsignedTx)

      logger.info("Tx was successfully signed!")
      val txId = ctx.sendTransaction(signedTx)


      val recordingInput = signedTx.getOutputsToSpend.get(0).getId

      logger.info(s"Tx was successfully sent with id: $txId")

      signedTx.toJson(true)
    })
    logger.info("Command has finished execution")

  }

  def recordToDb: Unit = {

    logger.info("Recording tx info into config...")

    logger.info("The following information will be updated:")
    logger.info(s"Recording Id: ${voteConf.getPovTokenId}(old) -> $recordingNFT")


    val newConfig = config.copy()

    newConfig.getParameters.getVotingConf.setPovTokenId(recordingNFT.toString)
    ConfigHandler.writeConfig(AppParameters.configFilePath, newConfig)
    logger.info("Config file has been successfully updated")
    exit(logger, ExitCodes.SUCCESS)
  }


}

