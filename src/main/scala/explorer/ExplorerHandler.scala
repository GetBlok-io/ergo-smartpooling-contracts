package explorer

import logging.LoggingHandler
import org.ergoplatform.explorer.client.model.TransactionInfo
import org.ergoplatform.explorer.client.{DefaultApi, ExplorerApiClient}
import org.slf4j.{Logger, LoggerFactory}


class ExplorerHandler(apiClient: ExplorerApiClient) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_NODE_HANDLER)

  logger.info("Creating explorer api...")
  val explorerApi: CustomExplorerApi = apiClient.createService(classOf[CustomExplorerApi])

  def getTxConfirmations(txId: String): Int = {
    logger.info(s"Getting num confirmations for $txId")
    val txInfoResponse = explorerApi.getTransactionById(txId).execute()

    if (txInfoResponse.isSuccessful) {
      logger.info("Request was successful")

      val txInfo = txInfoResponse.body().string()

      val txFields = txInfo.split(",")
      logger.info(s"Tx contains numConfirmations: ${txInfo.indexOf("numConfirmations")}")
      val numConfirmationsString = txFields.filter(s => s.startsWith("\"numConfirmations\""))
      logger.info(numConfirmationsString.mkString("Array(", ", ", ")"))
      if (numConfirmationsString.length >= 1) {
        val numConfirmations = numConfirmationsString.head.split(":")(1)
        logger.info(s"Num confirmations for tx: ${numConfirmations}")
        numConfirmations.toInt
      } else {
        logger.info("Num confirmations could not be parsed!")
        -1
      }
    } else {
      logger.info("Request was unsuccessful, returning -1 for confirmations.")
      -1
    }
  }
  def getTxOutputs(id: String): Option[OutputRequest] = {
    val txResponse = explorerApi.getTxOutputsById(id).execute()
    if(txResponse.isSuccessful){
      Some(txResponse.body())
    }else{
      None
    }
  }






}
