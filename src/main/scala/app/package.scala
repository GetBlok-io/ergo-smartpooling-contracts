import app.ExitCodes.{COMMAND_FAILED, CONFIG_NOT_FOUND, HOLDING_NOT_COVERED, INVALID_ARGUMENTS, INVALID_CONFIG, SUCCESS}
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.ergoplatform.appkit.NetworkType

import java.util.logging.Level

package object app {

  object ExitCodes {
    final val SUCCESS = 0
    final val INVALID_ARGUMENTS = 1

    final val CONFIG_NOT_FOUND = 100
    final val INVALID_CONFIG = 101
    final val NO_SMARTPOOL_ID_IN_CONFIG = 102
    final val NO_CONSENSUS_PATH_IN_CONFIG = 103
    final val NO_WALLET = 104

    final val INVALID_NODE_ADDRESS = 200
    final val INVALID_NODE_APIKEY = 201

    final val NO_COMMAND_TO_USE = 300
    final val COMMAND_FAILED = 301

    final val HOLDING_NOT_COVERED = 400
  }

  def exit(implicit logger: Logger, exitCode: Int): Nothing = {
    exitCode match {
      case SUCCESS =>
        logger.info("SmartPoolingApp exited successfully")
      case INVALID_ARGUMENTS =>
        logger.error("Invalid arguments given")
      case INVALID_CONFIG =>
        logger.error("Given configuration file is invalid")
      case CONFIG_NOT_FOUND =>
        logger.error("A configuration file could not be found at the given filepath")
      case HOLDING_NOT_COVERED =>
        logger.error("The holding address did not have enough ERG to cover the transaction")
      case COMMAND_FAILED =>
        logger.error("The command could not proceed due to unknown errors.")
      case _ =>
        logger.error("An unknown error occurred")
    }
    logger.warn("SmartPoolingApp is now exiting... [exitCode = " + exitCode + "]")
    sys.exit(exitCode)
  }

  object AppCommand extends Enumeration {
    type AppCommand
    val EmptyCommand, GenerateMetadataCmd, ModifySmartPoolCmd, DistributeRewardsCmd, ViewMetadataCmd, SendToHoldingCmd = Value
  }

}
