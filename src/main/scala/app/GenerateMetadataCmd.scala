package app

import config.{ConfigHandler, SmartPoolConfig, SmartPoolNodeConfig, SmartPoolWalletConfig}
import contracts.MetadataContract
import logging.LoggingHandler
import org.ergoplatform.appkit.config.ErgoNodeConfig
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoClient, ErgoId, ErgoToken, NetworkType, Parameters, RestApiErgoClient, SecretString}
import org.ergoplatform.restapi.client.{ApiClient, WalletApi}
import org.slf4j.LoggerFactory
import retrofit2.RetrofitUtil
import transactions.GenesisTx

import scala.collection.JavaConverters.seqAsJavaListConverter
// TODO: Change how wallet mneumonic is handled in order to be safer against hacks(maybe unlock file from node)
class GenerateMetadataCmd(config: SmartPoolConfig) extends SmartPoolCmd(config) {
  val logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_GEN_METADATA_CMD)

  override val appCommand: app.AppCommand.Value = AppCommand.GenerateMetadataCmd
  private var smartPoolId: ErgoId = _
  private var metadataId: ErgoId = _
  private var metadataAddress: Address = _

  def initiateCommand: Unit = {
    logger.info("Initiating command...")
    // Make sure smart pool id is not set
    assert(config.getParameters.getSmartPoolId == "")
    assert(config.getParameters.getMetadataId == "")

  }

  def executeCommand: Unit = {
    logger.info("Command has begun execution")
    val apiClient = new ApiClient(nodeConf.getNodeApi.getApiUrl, "ApiKeyAuth", nodeConf.getNodeApi.getApiKey)
    apiClient.createDefaultAdapter()
    apiClient.getAdapterBuilder


    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {

      val mnemonic = SecretString.create(nodeConf.getWallet.getWalletMneumonic)
      val password = SecretString.create(nodeConf.getWallet.getWalletPass)

      val prover = ctx.newProverBuilder().withMnemonic(mnemonic, password).build()
      val nodeAddress = Address.fromMnemonic(nodeConf.getNetworkType, mnemonic, password)

      logger.info("The following addresses must be exactly the same:")
      logger.info(s"Prover Address=${prover.getAddress}")
      logger.info(s"Node Address=${nodeAddress}")
      assert(prover.getAddress == nodeAddress)

      val metadataValue = config.getParameters.getMetadataValue
      val txFee = config.getParameters.getInitialTxFee
      val metadataContract = MetadataContract.generateMetadataContract(ctx)
      metadataAddress = Address.fromErgoTree(metadataContract.getErgoTree, nodeConf.getNetworkType)

      logger.info("Parameters given:")
      logger.info(s"MetadataValue=${metadataValue / Parameters.OneErg} ERG")
      logger.info(s"TxFee=${txFee / Parameters.OneErg} ERG")
      logger.info("Now building transaction...")

      val genesisTx = new GenesisTx(ctx.newTxBuilder())
      val unsignedTx =
        genesisTx
        .creatorAddress(nodeAddress)
        .metadataContract(MetadataContract.generateMetadataContract(ctx))
        .metadataValue(metadataValue)
        .txFee(txFee)
        .build()
      logger.info("Tx is now being signed...")
      //TODO check if this works
      val signedTx = prover.sign(unsignedTx)

      logger.info("Tx was successfully signed!")
      val txId = ctx.sendTransaction(signedTx)

      smartPoolId = signedTx.getOutputsToSpend.get(0).getId
      metadataId = signedTx.getOutputsToSpend.get(0).getId

      logger.info(s"Tx was successfully sent with id: $txId")

      signedTx.toJson(true)
    })
    logger.info("Command has finished execution")
  }

  def recordToConfig: Unit = {
    logger.info("Recording tx info into config...")

    logger.info("The following information will be updated:")
    logger.info(s"SmartPool Id: ${config.getParameters.getSmartPoolId}(old) -> $smartPoolId")
    logger.info(s"Metadata Id: ${config.getParameters.getMetadataId}(old) -> $metadataId")
    logger.info(s"Metadata Address: ${config.getParameters.getMetadataAddress}(old) -> $metadataAddress")

    val newConfig = config.copy()
    newConfig.getParameters.setSmartPoolId(smartPoolId.toString)
    newConfig.getParameters.setMetadataId(metadataId.toString)
    newConfig.getParameters.setMetadataAddress(metadataAddress.toString)

    ConfigHandler.writeConfig(AppParameters.configFilePath, newConfig)
    logger.info("Config file has been successfully updated")
  }


}

