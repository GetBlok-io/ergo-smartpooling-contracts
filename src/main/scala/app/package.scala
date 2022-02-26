import app.ExitCodes.{COMMAND_FAILED, CONFIG_NOT_FOUND, FAILED_TO_RETRIEVE_METADATA, HOLDING_NOT_COVERED, INVALID_ARGUMENTS, INVALID_CONFIG, LOGGING_INIT_FAILURE, NOT_ALL_SUBPOOLS_RETRIEVED, NO_CONFIRMED_TXS_FOUND, REGROUP_TX_SENT, SUBPOOL_TX_FAILED, SUCCESS}
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.ergoplatform.appkit.NetworkType

import java.util.logging.Level

package object app {

  object ExitCodes {
    final val SUCCESS =                         0
    final val INVALID_ARGUMENTS =               1

    final val CONFIG_NOT_FOUND =                100
    final val INVALID_CONFIG =                  101
    final val NO_SMARTPOOL_ID_IN_CONFIG =       102
    final val NO_CONSENSUS_PATH_IN_CONFIG =     103
    final val NO_WALLET =                       104

    final val INVALID_NODE_ADDRESS =            105
    final val INVALID_NODE_APIKEY =             106

    final val NO_COMMAND_TO_USE =               200
    final val COMMAND_FAILED =                  201
    final val LOGGING_INIT_FAILURE =            202

    final val HOLDING_NOT_COVERED =             203
    final val FAILED_TO_RETRIEVE_METADATA =     204
    final val NO_CONFIRMED_TXS_FOUND =          205
    final val REGROUP_TX_SENT =                 206
    final val TX_GROUPING =                     207
    final val SUBPOOL_TX_FAILED =               208
    final val NOT_ALL_SUBPOOLS_RETRIEVED =      209

    final val HOLDING_SENT =                    300
    final val FAILURE_RETRIAL =                 301

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
      case FAILED_TO_RETRIEVE_METADATA =>
        logger.error("The metadata box could not be retrieved from the blockchain.")
      case NO_CONFIRMED_TXS_FOUND =>
        logger.error("No confirmed transactions found for the current smartpool.")
      case REGROUP_TX_SENT =>
        logger.error("A regroup tx was sent to obtain exact holding inputs.")
      case SUBPOOL_TX_FAILED =>
        logger.error("A subpool tx chain has a failure in it!")
      case NOT_ALL_SUBPOOLS_RETRIEVED =>
        logger.error("Not all subpools could be retrieved from the blockchain. Have all txs confirmed yet?")
      case LOGGING_INIT_FAILURE =>
        logger.error("Logging could not be started, please ensure any .lck files have been deleted from past runs.")
      case _ =>
        logger.error("An unknown error occurred")
    }
    logger.warn("SmartPoolingApp is now exiting... [exitCode = " + exitCode + "]")
    sys.exit(exitCode)
  }

  object AppCommand extends Enumeration {
    type AppCommand
    val EmptyCommand, GenerateMetadataCmd, ModifySmartPoolCmd, DistributeRewardsCmd, ViewMetadataCmd, SendToHoldingCmd,
    ResetStatusCmd, CheckAndCleanDbCmd, PayoutBalancesCmd, DistributeFailedCmd, InitializeVoteTokensCmd, InitializePOVTokensCmd,
    GenerateRecordingCmd, VoteCollectionCmd, ScanMetadataCmd = Value
  }

}
