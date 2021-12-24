package app

import logging.LoggingHandler
import org.ergoplatform.appkit.JavaHelpers.UniversalConverter
import org.ergoplatform.{DataInput, ErgoBox, ErgoBoxCandidate, ErgoLikeTransaction, Input, UnsignedErgoLikeTransaction}
import org.ergoplatform.appkit.impl.ScalaBridge
import org.ergoplatform.appkit.{Address, Iso, ReducedErgoLikeTransaction, ReducedTransaction, UnsignedTransaction}
import org.ergoplatform.restapi.client.{ApiClient, Body3, ErgoTransaction, ErgoTransactionDataInput, ErgoTransactionInput, ErgoTransactionOutput, UnsignedErgoTransaction, WalletApi}
import org.slf4j.{Logger, LoggerFactory}

import java.util.List

class NodeHandler(apiClient: ApiClient) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_NODE_HANDLER)

  logger.info("Creating wallet api...")
  val walletApi = apiClient.createService(classOf[WalletApi])

  def getAddress(idx: Int): Address = {
    logger.info("Getting address from node...")
    val walletAddresses = walletApi.walletAddresses().execute()
    if(walletAddresses.isSuccessful){
      logger.info("Wallet unlocked successfully")
      logger.info("Wallet Address At Index 0: " + walletAddresses.body().get(0))
      Address.create(walletAddresses.body().get(idx))
    }else{
      logger.warn("Address from node could not be created...")
      logger.warn(walletAddresses.errorBody().string())
      exit(logger, ExitCodes.INVALID_NODE_ADDRESS)
    }
  }

  def unlockWallet(pass: String):Boolean = {
    logger.info("Unlocking node wallet...")
    val unlockedWallet = walletApi.walletUnlock((new Body3).pass(pass)).execute()
    if(unlockedWallet.isSuccessful) {
      logger.info("Wallet unlocked successfully")
      true
    } else {
      logger.warn("Wallet could not be unlocked!")
      false
    }
  }

  def signTx(tx: ReducedTransaction): Unit = {


  }

}
