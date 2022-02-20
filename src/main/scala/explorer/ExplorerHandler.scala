package explorer

import configs.params.VotingConfig
import logging.LoggingHandler
import org.ergoplatform.explorer.client.model.TransactionInfo
import org.ergoplatform.explorer.client.{DefaultApi, ExplorerApiClient}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.mutable.ArrayBuffer


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

  def getTx(id: String): Option[TransactionInfo] = {
    val txResponse = explorerApi.getFullTxById(id).execute()
    if(txResponse.isSuccessful){
      Some(txResponse.body())
    }else{
      None
    }
  }

  def printVoters(): Unit = {
    val voteYes = "2iHok9WwTp2wk1MKdvx7JFiK74FPZPPWLKJC42mGT7JMYwk83xBJeciaQt1eymTXGyEFC76hgDt7bVPXkrU61TEYu1EZr7v8G1aCqtJeHzzHFU3Wib3Y2BfHdcMQCdFoZzqUPHxvQCkrtKtD1b5vUQ"
    val voteNo = "2iHok9WwTp2wk1MKdvx7JFiK74FPZPPWLKJC42mGT7JMYwk83xBJeciaQt1eymTXGyEFC76hgDt7bVPXkrU61TEYu1EZr7v8G1aCqtJeHzzHFU3Wib3Y2BfHdcMQCdFoZzqUPHxvQCkrtKththUnwP"
    val recordingBox = "3JyRnabj25cBBJa1SXzzm7JrsowHqxUdxvbtsxKRt7gfCoh6GmyZDPV1o6KxB3gKLyT7NdyuqebfEqAPKqffo6qKTdL73tzTbnqz1SmEFfL1op5t2uLcJSwUbsDpsbRMgRfUWMjLRbqayDxoXuEaLJCrKBm5tGF6xwndPae9hD6bzKx8W2DcuAPPGeAccDzZsG2MaVcRqC7cNNEk1szDCJ6idev7WyRjzCsBosWp52MTGDk7YL8xhWdGWtC5X8Fx3mcqCncFAhq5BDeLUF2PmE2HVQ8aVu7ojT33QSFkcGKzNiJ4MsSDgvVFKjMrmzy1rnoF5Eg8nvkQwwxH2dmnL5MX7Y3WJxTU4LbxoYVEQUD3nyvTMeSpdBedfZKwdvK2bMfnbpL1mGZZpCt1p2yFzNodgcwW5v4xRwvZVnudxw6cBCyBLSSGptLXA6WE4dv5ocASZwv84gjqos6DzqUREGzsgxjBLFnHxP8AUmZ17Q4Wj7JZYQ6zCDYR7ce9AySMCcz21N7Xz88L59gWixp6cbTt9SuJfN8K8hotx7VriRkvdgmqsSzXrop43qgMyz1CWrunkSha1wcy2mjEc2DkCxuY3nTvaHZfwr5P5uTv88DtMuYfHv3hPHtsNQb6FVWFiLZhZoC2ZwWTsqvY2zUcK4gku6eKPMzkdiHs2ABidcEoKNUGCzecM3ug2Ph4scrx34Y2YDqEHr7b8vrzFF8ANgHozxQ4vjtNJR9SgUy7wwaat5gkDbnrFo9GRAEhi7Sdyy88GmNtoWdM6Dmdv4m5pe8tXsv3aF8z7bgzWd4fndFsBJM4GMMDXXXerKWRB9GZ3NV7ozBrk7KKHuYosqTWfaXG6Jdg5UUn3RtYeZ3ySQuFsT8PFCSAxZkVutNXiX6n2VArqCShuKuTwmEQoG8t7Uu6TkwqoVQKktYSkHtASgkZtcbR1hQAicS6gxDi2ga4W7M2SsA4QNtnpYARJxdqcmb8WbRXssiTsC6HSi"

    var txResponse = explorerApi.getTxsByAddress(voteYes, 0, 400).execute()
    val minersVoted = ArrayBuffer[String]()
    if(txResponse.isSuccessful){
      txResponse.body().getItems.asScala.foreach {
        t =>
          val firstInput = t.getInputs.get(0)
          if(firstInput.getAddress != voteYes && firstInput.getAddress != voteNo && firstInput.getAddress != recordingBox){
            logger.info(s"Found address ${firstInput.getAddress} voted yes")
            minersVoted += firstInput.getAddress
          }

      }
    }else{
      logger.info(txResponse.raw().toString)
      logger.info(txResponse.errorBody().string())
      logger.info(txResponse.message())

    }
    txResponse = explorerApi.getTxsByAddress(voteNo, 0, 400).execute()
    if(txResponse.isSuccessful){
      txResponse.body().getItems.asScala.foreach {
        t =>
          val firstInput = t.getInputs.get(0)
          if(firstInput.getAddress != voteYes && firstInput.getAddress != voteNo && firstInput.getAddress != recordingBox){
            logger.info(s"Found address ${firstInput.getAddress} voted no")
            minersVoted += firstInput.getAddress
          }

      }
    }
    val distinctVoters = minersVoted.distinct
    logger.info("Total distinct voters: ")
    var printString = "\n"
    distinctVoters.foreach(d => printString = printString + d + "\n")
    logger.info(printString)
    logger.info(s"Total of ${distinctVoters.length} distinct voters")

  }






}
