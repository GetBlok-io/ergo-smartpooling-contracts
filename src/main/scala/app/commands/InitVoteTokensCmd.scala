package app.commands

import app.{AppCommand, AppParameters}
import configs.{ConfigHandler, SmartPoolConfig}
import contracts.command.{CommandContract, PKContract}
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import contracts.{MetadataContract, holding}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.slf4j.LoggerFactory
import transactions.GenesisTx

class InitVoteTokensCmd(config: SmartPoolConfig) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_INIT_VOTE_CMD)

  override val appCommand: app.AppCommand.Value = AppCommand.InitializeVoteTokensCmd
  private var voteTokenId: ErgoId = _

  private var commandContract: CommandContract = _

  private var secretStorage: SecretStorage = _
  def initiateCommand: Unit = {
    logger.info("Initiating command...")

    assert(walletConf.getSecretStoragePath != "")

    logger.info(nodeConf.getNodeApi.getApiUrl)



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

      val txFee = paramsConf.getInitialTxFee


      val boxesToSpend = ctx.getWallet.getUnspentBoxes(Parameters.OneErg + (txFee * 2)).get()
      voteTokenId = boxesToSpend.get(0).getId
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      val outB = txB.outBoxBuilder()

      val voteToken = new ErgoToken(voteTokenId, Parameters.OneErg * voteConf.getVoteTokensToMint)
      // TODO: Remove constant values for tokens and place them into config
      val tokenBox = outB
        .value(Parameters.OneErg + (txFee))
        .mintToken(voteToken, "GetBlok.io Soft-Fork Governance Token",
          "This token distributed to miners allows voting on GetBlok.io's decision during the EIP-0027 Soft-Fork Proposal Vote." +
            " Learn more at https://ergo.getblok.io/vote", 9)
        .contract(new ErgoTreeContract(nodeAddress.getErgoAddress.script, nodeAddress.getNetworkType))
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
      logger.info(s"Tx sent with id $tokenTxId and cost ${tokenTxSigned.getCost}")

      tokenTxId
    })
    logger.info("Command has finished execution")
  }

  def recordToDb: Unit = {

    logger.info("Recording tx info into config...")

    logger.info("The following information will be updated:")
    logger.info(s"VoteToken Id: ${voteConf.getVoteTokenId}(old) -> $voteTokenId")


    val newConfig = config.copy()
    newConfig.getParameters.getVotingConf.setVoteTokenId(voteTokenId.toString)

    ConfigHandler.writeConfig(AppParameters.configFilePath, newConfig)
    logger.info("Config file has been successfully updated")
  }


}

