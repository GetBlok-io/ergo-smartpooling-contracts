package payments

import app.AppParameters
import logging.LoggingHandler
import org.ergoplatform.appkit.{Address, Parameters}
import org.slf4j.{Logger, LoggerFactory}
import persistence.responses.ShareResponse
import registers.{MemberList, ShareConsensus}

import scala.util.Try

// Object that handles payments in form of PPLNS and converts them into consensus and members list
object PaymentHandler {
  // TODO make following variables in config
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PAYMENT_HANDLER)
  private final val ERGO_SHARE_CONSTANT = 256
  private final val PPLNS_WINDOW = 0.5

  // Takes a set of share responses and converts them into a tuple of
  // (ShareConsensus, MemberList) so that the values may be easily entered into a command box output
  def pplnsToConsensus(shares: Array[ShareResponse]): (ShareConsensus, MemberList) = {
    logger.info("Creating consensus and member list from ShareResponse")
    val shareScores = shares.map(s => (s.minerAddress, (s.diff * ERGO_SHARE_CONSTANT.toDouble) / s.netDiff))
    logger.warn(s"Using hard-coded constants for ERGO_SHARE_CONSTANT($ERGO_SHARE_CONSTANT) and PPLNS_WINDOW($PPLNS_WINDOW)")
    logger.warn(s"Changes to these values in MiningCore will NOT be reflected in payout code.")
    var totalScore: Double = 0.0
    var minerScores = Map[String, Double]()
    var done = false

    for (sc <- shareScores) {
      var shScore = sc._2
      if (totalScore + shScore >= PPLNS_WINDOW) {
        shScore = PPLNS_WINDOW - totalScore
        done = true
      }
      if (minerScores.contains(sc._1)) {
        minerScores = minerScores.updated(sc._1, minerScores(sc._1) + shScore)
      } else {
        minerScores = minerScores + Tuple2(sc._1, shScore)
      }
      totalScore = totalScore + shScore
    }
    // Lets simply multiply by one erg to ensure we have a long as our share number.
    // The smart contract will take the ratio of share score to it's own calculated total score
    val minerShares: Map[String, Long] = minerScores.map(score => (score._1, (score._2 * Parameters.OneErg).toLong))
    val baseArray: Array[(Try[Address], Array[Long])] = minerShares.toArray.map {
      sh =>
        val asAddress = Try {
          Address.create(sh._1)
        }
        // Long array will be updated later by holding box to evaluate minimum and stored payouts
        if (asAddress.isFailure) {
          logger.warn(s"Failed to create address from string ${sh._1}, this address will not be added to consensus.")
        }
        val longArray = Array(sh._2, AppParameters.minimumPayout, 0L)
        (asAddress, longArray)
    }

    val arrayConsensus = baseArray.filter(b => b._1.isSuccess).map(b => (b._1.get.getErgoAddress.script.bytes, b._2))
    val memberArray = baseArray.filter(b => b._1.isSuccess).map(b => (b._1.get.getErgoAddress.script.bytes, b._1.get.toString))

    val shareConsensus = ShareConsensus.fromConversionValues(arrayConsensus)
    val memberList = MemberList.fromConversionValues(memberArray)

    (shareConsensus, memberList)
  }
}
