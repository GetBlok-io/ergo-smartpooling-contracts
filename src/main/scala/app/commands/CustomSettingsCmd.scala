package app.commands

import app.{AppCommand, ExitCodes, exit}
import boxes.{BoxHelpers, CommandInputBox, MetadataInputBox}
import configs.SmartPoolConfig
import contracts.command.VoteTokensContract
import contracts.holding
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.slf4j.{Logger, LoggerFactory}
import persistence.entries.{BoxIndexEntry, ConsensusEntry, SmartPoolEntry}
import persistence.queries.{ConsensusByTransactionQuery, MinimumPayoutsQuery, SmartPoolBySubpoolQuery}
import persistence.responses.SmartPoolResponse
import persistence.writes._
import persistence.{DatabaseConnection, PersistenceHandler}
import registers.{MemberList, ShareConsensus}
import transactions.{CreateCommandTx, DistributionTx}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class CustomSettingsCmd(config: SmartPoolConfig) extends SmartPoolCmd(config) {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DISTRIBUTE_REWARDS_CMD)
  final val PPLNS_CONSTANT = 50000

  override val appCommand: app.AppCommand.Value = AppCommand.DistributeRewardsCmd
  private var smartPoolId: ErgoId = _
  private var metadataId: ErgoId = _
  private var holdingContract: HoldingContract = _
  private var dbConn: DatabaseConnection = _
  private var memberList: MemberList = MemberList.convert(Array())
  private var shareConsensus: ShareConsensus = ShareConsensus.convert(Array())
  private var metadataBox: MetadataInputBox = _

  private var txId: String = _
  private var nextCommandBox: CommandInputBox = _
  private var signedTx: SignedTransaction = _
  private var subpoolResponse: SmartPoolResponse = _
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    logger.info("Attempting retrial of failed subpool!")
    // Make sure smart pool ids are set
    require(paramsConf.getSmartPoolId != "")
    require(metaConf.getMetadataId != "")

  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")

    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {

      val secretStorage = SecretStorage.loadFrom(walletConf.getSecretStoragePath)
      secretStorage.unlock(nodeConf.getWallet.getWalletPass)

      val prover = ctx.newProverBuilder().withSecretStorage(secretStorage).withEip3Secret(0).build()
      val nodeAddress = prover.getEip3Addresses.get(0)
      val txFee = paramsConf.getInitialTxFee


      val boxesToSpend = ctx.getWallet.getUnspentBoxes(Parameters.OneErg + (txFee * 2)).get()

      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      
      val outB = txB.outBoxBuilder()
      val payoutAddress = Address.create("9fuWDTpbo4gAczS9ooyLgNXyBsMf1eaZd633YBUrDi1H3r55Kcv")
      // TODO: Remove constant values for tokens and place them into config
      val payoutBox = outB
        .value(((BigDecimal(3.544) * Parameters.OneErg).toLong) + (txFee))
        .contract(new ErgoTreeContract(payoutAddress.getErgoAddress.script, nodeAddress.getNetworkType))
        .build()

      val payoutTx =
        txB
          .boxesToSpend(boxesToSpend)
          .fee(txFee)
          .outputs(payoutBox)
          .sendChangeTo(nodeAddress.getErgoAddress)
          .build()

      val payoutTxSigned = prover.sign(payoutTx)
      val payoutTxId: String = ctx.sendTransaction(payoutTxSigned).filter(c => c != '\"')
      logger.info(s"Tx sent with id $payoutTxId and cost ${payoutTxSigned.getCost}")

      payoutTxId
    })
    logger.info("Command has finished execution")

  }

  def recordToDb: Unit = {


  }




}

