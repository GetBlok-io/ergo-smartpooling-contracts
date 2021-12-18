package app

import app.AppCommand.EmptyCommand
import org.slf4j.LoggerFactory
import config.SmartPoolConfig
import logging.LoggingHandler
import org.slf4j.Logger

import java.io.FileNotFoundException



object SmartPoolingApp{

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_MAIN)
  LoggingHandler.initiateLogging()

    def main(args: Array[String]): Unit = {
      logger.info("Now starting SmartPoolingApp")

      val usage = "Usage: java -jar SmartPoolingApp.jar -c=smart/pool/path/config.json [-p | -f]"
      var txCommand = EmptyCommand
      var config: Option[SmartPoolConfig] = None

      for(arg <- args){
        arg match {
          case arg if arg.startsWith("-") =>
            val commandArg = arg.charAt(1)
            commandArg match {
              case 'c' =>
                val commandValue = arg.split("=")(1)
                try {
                  config = Some(SmartPoolConfig.load(commandValue))
                  AppParameters.configFilePath = commandValue
                } catch {
                  case fileNotFoundException: FileNotFoundException =>
                    exit(logger, ExitCodes.CONFIG_NOT_FOUND)
                  case _ =>
                    exit(logger, ExitCodes.CONFIG_NOT_FOUND)
                }
              case 'p' =>
                AppParameters.fromPersistence = true
              case 'f' =>
                AppParameters.fromFilePath = true
              case _ =>
                exit(logger, ExitCodes.INVALID_ARGUMENTS)
            }
          case _ => exit(logger, ExitCodes.INVALID_ARGUMENTS)
        }
      }

      if(config.isEmpty)
        exit(logger, ExitCodes.CONFIG_NOT_FOUND)

      logger.info(s"Configuration file and command arguments successfully loaded")
    }


}


