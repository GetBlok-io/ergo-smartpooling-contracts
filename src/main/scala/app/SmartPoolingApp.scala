package app

import app.AppCommand.{DistributeRewardsCmd, EmptyCommand, GenerateMetadataCmd, SendToHoldingCmd, ViewMetadataCmd}
import org.slf4j.LoggerFactory
import config.{ConfigHandler, SmartPoolConfig}
import logging.LoggingHandler
import org.slf4j.Logger

import java.io.FileNotFoundException
import scala.util.{Failure, Try}



object SmartPoolingApp{

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_MAIN)

  var cmd: SmartPoolCmd = _
  var config: Try[SmartPoolConfig] = _
  def main(args: Array[String]): Unit = {
    logger.info("Now starting SmartPoolingApp")

    val usage = "Usage: java -jar SmartPoolingApp.jar -c=smart/pool/path/config.json [-v | -g | -d blockHeight | -h blockHeight ]"
    var txCommand = EmptyCommand

    var blockHeight = 0
    var blockHeights = Array[Int]()
    for(arg <- args){
      arg match {
        case arg if arg.startsWith("-") =>
          val commandArg = arg.charAt(1)
          commandArg match {
            case 'c' =>
              val commandValue = arg.split("=")(1)
              try {
                config = Try(SmartPoolConfig.load(commandValue))
                AppParameters.configFilePath = commandValue

              } catch {
                case ex: Exception =>
                  exit(logger, ExitCodes.CONFIG_NOT_FOUND)
              }
            case 'g' =>
              txCommand = GenerateMetadataCmd
            case 'd' =>
              txCommand = DistributeRewardsCmd
            case 'v' =>
              txCommand = ViewMetadataCmd
            case 'h' =>
              txCommand = SendToHoldingCmd
            case _ =>
              exit(logger, ExitCodes.INVALID_ARGUMENTS)
          }
        case arg if arg.forall(c => c.isDigit) =>
          blockHeight = arg.toInt
        case arg if arg.startsWith("[") && arg.endsWith("]") =>
          blockHeights = arg.substring(1, arg.length - 1).split(",").map(s => s.toInt)

        case _ => exit(logger, ExitCodes.INVALID_ARGUMENTS)
      }
    }
    if(config == null || config.isFailure) {
      config = Try(SmartPoolConfig.load(ConfigHandler.defaultConfigName))
      AppParameters.configFilePath = ConfigHandler.defaultConfigName

    }
    if(config.isFailure) {
      logger.warn("A config file could not be found, generating new file under sp_config.json")
      config = Try(ConfigHandler.newConfig)
      ConfigHandler.writeConfig(ConfigHandler.defaultConfigName, config.get)

      exit(logger, ExitCodes.CONFIG_NOT_FOUND)
    }
    try {
      LoggingHandler.initiateLogging(config.get)
    }catch {
      case ex: Exception =>
        exit(logger, ExitCodes.LOGGING_INIT_FAILURE)
    }

        logger.info(s"Configuration file and command arguments successfully loaded")

        logger.info(s"Evaluating SmartPool Command...")
        txCommand match {
          case GenerateMetadataCmd =>
            cmd = new GenerateMetadataCmd(config.get)
            logger.info(s"SmartPool Command: ${GenerateMetadataCmd.toString}")
          case DistributeRewardsCmd =>
            if(blockHeight != 0) {
              cmd = new DistributeRewardsCmd(config.get, blockHeight)
            }else if(blockHeights.length > 0){
              cmd = new DistributeMultipleCmd(config.get, blockHeights)
            }else{
              exit(logger, ExitCodes.INVALID_ARGUMENTS)
            }
            logger.info(s"SmartPool Command: ${DistributeRewardsCmd.toString}")
          case ViewMetadataCmd =>
            cmd = new ViewMetadataCmd(config.get)
            logger.info(s"SmartPool Command: ${ViewMetadataCmd.toString}")
          case SendToHoldingCmd =>
            if(blockHeight != 0) {
              cmd = new SendToHoldingCmd(config.get, blockHeight)
            } else if(blockHeights.length > 0) {
              cmd = new SendMultipleToHoldingCmd(config.get, blockHeights)
            } else {
              exit(logger, ExitCodes.INVALID_ARGUMENTS)
            }
            logger.info(s"SmartPool Command: ${SendToHoldingCmd.toString}")
          case _ =>
            exit(logger, ExitCodes.NO_COMMAND_TO_USE)
        }
        try {
          cmd.initiateCommand
          cmd.executeCommand
          cmd.recordToConfig
        }catch {
          case arg: Exception =>
            logger.error(s"An exception occurred while executing the command $txCommand", arg)
            if(arg != null && arg.getMessage != null){
              logger.error(arg.getMessage)
            }

            for(e <- arg.getStackTrace){
              logger.error(e.toString)
            }
            exit(logger, ExitCodes.COMMAND_FAILED)
        }

        logger.info(s"Command successfully completed")
        exit(logger, ExitCodes.SUCCESS)
    }
}





