package app

import config.{ConfigHandler, SmartPoolConfig}
import contracts.command.{CommandContract, PKContract}
import contracts.{MetadataContract, holding}
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import logging.LoggingHandler
import org.ergoplatform.appkit.config.ErgoNodeConfig
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoClient, ErgoId, ErgoToken, ErgoValue, NetworkType, Parameters, RestApiErgoClient, SecretStorage, SecretString, UnsignedTransactionBuilder}
import org.ergoplatform.restapi.client.{ApiClient, Body3, WalletApi}
import org.slf4j.LoggerFactory
import retrofit2.RetrofitUtil
import sigmastate.CreateAvlTree
import transactions.GenesisTx

import scala.collection.JavaConverters.seqAsJavaListConverter
// TODO: Change how wallet mneumonic is handled in order to be safer against hacks(maybe unlock file from node)
class GenerateMetadataCmd(config: SmartPoolConfig) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_GEN_METADATA_CMD)

  override val appCommand: app.AppCommand.Value = AppCommand.GenerateMetadataCmd
  private var smartPoolId: ErgoId = _
  private var metadataId: ErgoId = _
  private var metadataAddress: Address = _
  private var holdingContract: HoldingContract = _
  private var commandContract: CommandContract = _

  private var secretStorage: SecretStorage = _
  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    // Make sure smart pool id is not set
    assert(paramsConf.getSmartPoolId == "")
    assert(metaConf.getMetadataId == "")
    assert(walletConf.getSecretStoragePath != "")

    logger.info(nodeConf.getNodeApi.getApiUrl)
    logger.info(nodeConf.getNodeApi.getApiKey)


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
      val metadataValue = metaConf.getMetadataValue
      val txFee = paramsConf.getInitialTxFee
      val metadataContract = MetadataContract.generateMetadataContract(ctx)
      metadataAddress = Address.fromErgoTree(metadataContract.getErgoTree, nodeConf.getNetworkType)


      val boxesToSpend = ctx.getCoveringBoxesFor(nodeAddress, metadataValue + (txFee * 2), List[ErgoToken]().asJava).getBoxes
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      val outB = txB.outBoxBuilder()
      smartPoolId = boxesToSpend.get(0).getId
      val smartPoolToken = new ErgoToken(smartPoolId, 1)
      // TODO: Remove constant values for tokens and place them into config
      val tokenBox = outB
        .value(metadataValue + (Parameters.MinFee * 4))
        .mintToken(smartPoolToken, "GetBlok.io SmartPool Display Token", "This NFT identifies this box to be a part of GetBlok.io's smart-contract based mining pool", 0)
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


      logger.info("Parameters given:")
      logger.info(s"MetadataValue=${metadataValue / Parameters.OneErg} ERG")
      logger.info(s"TxFee=${txFee.toDouble / Parameters.OneErg} ERG")
      logger.info("Now building transaction...")

      val genesisTx = new GenesisTx(ctx.newTxBuilder())
      val unsignedTx =
        genesisTx
        .tokenInputBox(tokenInputBox)
        .smartPoolNFT(smartPoolToken)
        .creatorAddress(nodeAddress)
        .metadataContract(MetadataContract.generateMetadataContract(ctx))
        .metadataValue(metadataValue)
        .txFee(txFee)
        .build()
      logger.info("Tx has been built")



      logger.info("Tx is now being signed...")
      //TODO check if this works
      val signedTx = prover.sign(unsignedTx)

      logger.info("Tx was successfully signed!")
      val txId = ctx.sendTransaction(signedTx)


      metadataId = signedTx.getOutputsToSpend.get(0).getId

      logger.info(s"Tx was successfully sent with id: $txId")

      logger.info("Generating holding contract and address for new SmartPool Id")
      logger.warn("Currently using default HoldingContract hardcoded into tx, add to config later for more flexibility")
      holdingContract = new holding.SimpleHoldingContract(SimpleHoldingContract.generateHoldingContract(ctx, metadataAddress, smartPoolId))
      logger.warn("Currently using default CommandContract hardcoded into tx, add to config later for more flexibility")
      commandContract = new PKContract(nodeAddress)
      signedTx.toJson(true)
    })
    logger.info("Command has finished execution")
  }

  def recordToConfig: Unit = {

    logger.info("Recording tx info into config...")

    logger.info("The following information will be updated:")
    logger.info(s"SmartPool Id: ${paramsConf.getSmartPoolId}(old) -> $smartPoolId")
    logger.info(s"Metadata Id: ${metaConf.getMetadataId}(old) -> $metadataId")
    logger.info(s"Metadata Address: ${metaConf.getMetadataAddress}(old) -> $metadataAddress")
    logger.info(s"Pool Operators: ${paramsConf.getPoolOperators.mkString("Array(", ", ", ")")}(old) -> ${Array(commandContract.getAddress).mkString("Array(", ", ", ")")}")
    logger.info(s"Holding Address: ${holdConf.getHoldingAddress}(old) -> ${holdingContract.getAddress}")
    logger.info(s"Holding Type: ${holdConf.getHoldingType}(old) -> default")

    val newConfig = config.copy()
    newConfig.getParameters.setSmartPoolId(smartPoolId.toString)
    newConfig.getParameters.getMetaConf.setMetadataId(metadataId.toString)
    newConfig.getParameters.getMetaConf.setMetadataAddress(metadataAddress.toString)
    newConfig.getParameters.getHoldingConf.setHoldingAddress(holdingContract.getAddress.toString)
    newConfig.getParameters.setPoolOperators(Array(commandContract.getAddress.toString))
    newConfig.getParameters.getHoldingConf.setHoldingType("default")

    ConfigHandler.writeConfig(AppParameters.configFilePath, newConfig)
    logger.info("Config file has been successfully updated")
  }


}

