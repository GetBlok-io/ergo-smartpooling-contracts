

import app.AppParameters
import logging.LoggingHandler
import org.ergoplatform.appkit.{Address, Parameters}
import org.slf4j.{Logger, LoggerFactory}
import persistence.responses.ShareResponse
import registers.{MemberList, ShareConsensus}

import scala.util.Try

// Object that handles payments in form of PPLNS and converts them into consensus and members list
package object payments {
  // TODO make following variables in config
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PAYMENT_HANDLER)
  final val ERGO_SHARE_CONSTANT = BigDecimal("256")
  final val PPLNS_WINDOW = BigDecimal("0.5")
  var minerScores = Map[String, BigDecimal]()


}
