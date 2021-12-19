package app

import app.AppCommand.{DistributeRewardsCmd, EmptyCommand, GenerateMetadataCmd}
import org.slf4j.LoggerFactory
import config.{ConfigHandler, SmartPoolConfig}
import logging.LoggingHandler
import org.slf4j.Logger

import java.io.FileNotFoundException



object SmartPoolingApp{

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_MAIN)
  LoggingHandler.initiateLogging()
  var cmd: SmartPoolCmd = _

  def main(args: Array[String]): Unit = {
    logger.info("Now starting SmartPoolingApp")

    val usage = "Usage: java -jar SmartPoolingApp.jar -c=smart/pool/path/config.json [-g | -d blockHeight ]"
    var txCommand = EmptyCommand
    var config: Option[SmartPoolConfig] = None
    var blockHeight = 0
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
            case 'g' =>
              txCommand = GenerateMetadataCmd
            case 'd' =>
              txCommand = DistributeRewardsCmd
            case _ =>
              exit(logger, ExitCodes.INVALID_ARGUMENTS)
          }
        case arg if arg.forall(c => c.isDigit) =>
          blockHeight = arg.toInt

        case _ => exit(logger, ExitCodes.INVALID_ARGUMENTS)
      }
    }

    if(config.isEmpty) {
      logger.warn("A config file could not be found, generating new file under sp_config.json")
      config = Some(ConfigHandler.newConfig)
      ConfigHandler.writeConfig(ConfigHandler.defaultConfigName, config.get)

      exit(logger, ExitCodes.CONFIG_NOT_FOUND)
    }
    logger.info(s"Configuration file and command arguments successfully loaded")

    logger.info(s"Evaluating SmartPool Command...")
    txCommand match {
      case GenerateMetadataCmd =>
        cmd = new GenerateMetadataCmd(config.get)
        logger.info(s"SmartPool Command: ${GenerateMetadataCmd.toString}")
      case DistributeRewardsCmd =>
        assert(blockHeight != 0)
        cmd = new DistributeRewardsCmd(config.get, blockHeight)
        logger.info(s"SmartPool Command: ${DistributeRewardsCmd.toString}")
      case _ =>
        exit(logger, ExitCodes.NO_COMMAND_TO_USE)
    }

    cmd.initiateCommand
    cmd.executeCommand
    cmd.recordToConfig

    logger.info(s"Command successfully completed")
    exit(logger, ExitCodes.SUCCESS)
  }


}


