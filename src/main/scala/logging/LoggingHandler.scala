package logging

import app.AppParameters
import org.slf4j.{Logger, LoggerFactory}

import java.util.Date
import java.util.logging.{ConsoleHandler, ErrorManager, FileHandler, Formatter, Level, LogRecord, SimpleFormatter}

object LoggingHandler {
  val fileHandler: FileHandler = new FileHandler("smartpool.%u.%g.log", 1000000, 3, true)
  val consoleHandler: ConsoleHandler = new ConsoleHandler()

  object loggers {
    val LOG_MAIN = "Main"
    val LOG_TEST = "Test"

    val LOG_PERSISTENCE = "Persistence"
    val LOG_PAYMENT_HANDLER = "PaymentHandler"
    val LOG_GEN_METADATA_CMD = "GenerateMetadataCmd"
    val LOG_MODIFY_SMARTPOOL_CMD = "ModifySmartPoolCmd"
    val LOG_DISTRIBUTE_REWARDS_CMD = "DistributeRewardsCmd"
    val LOG_NODE_HANDLER = "NodeHandler"

    val LOG_COMMAND_TX = "CommandTx"
    val LOG_DIST_TX = "DistributionTx"
    val LOG_GEN_TX = "GenesisTx"
    val LOG_MOD_TX = "ModificationTx"

    val loggerNames = List(
      LOG_MAIN, LOG_TEST,
      LOG_PERSISTENCE, LOG_PAYMENT_HANDLER, LOG_GEN_METADATA_CMD, LOG_MODIFY_SMARTPOOL_CMD, LOG_DISTRIBUTE_REWARDS_CMD,
      LOG_NODE_HANDLER,
      LOG_COMMAND_TX, LOG_DIST_TX, LOG_GEN_TX, LOG_MOD_TX
    )

  }


  import java.util.logging.LogManager

  LogManager.getLogManager.reset()
  val globalLogger: java.util.logging.Logger = java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME)
  globalLogger.setLevel(java.util.logging.Level.OFF)

  def initializeLogger(logger: org.slf4j.Logger): Logger = {
    val javaLogger: java.util.logging.Logger = java.util.logging.Logger.getLogger(logger.getName)
    val fileFormatter = new FileFormatter()
    val consoleFormatter = new ConsoleFormatter()

    fileHandler.setFormatter(fileFormatter)
    consoleHandler.setFormatter(consoleFormatter)

    javaLogger.addHandler(fileHandler)
    javaLogger.addHandler(consoleHandler)

    fileHandler.setLevel(AppParameters.fileLoggingLevel)
    consoleHandler.setLevel(AppParameters.consoleLoggingLevel)

    logger
  }

  def initiateLogging(): Unit = {

    loggers.loggerNames.foreach{
      (loggerName: String) =>
        val loggerObj = LoggerFactory.getLogger(loggerName)
        initializeLogger(loggerObj)
    }

  }



}




