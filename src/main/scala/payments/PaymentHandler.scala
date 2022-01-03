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
  private final val ERGO_SHARE_CONSTANT = BigDecimal("256")
  private final val PPLNS_WINDOW = BigDecimal("500.0")

  // Takes a set of share responses and converts them into a tuple of
  // (ShareConsensus, MemberList) so that the values may be easily entered into a command box output
  def standardPPLNSToConsensus(shares: Array[ShareResponse]): (ShareConsensus, MemberList) = {
    logger.info("Creating consensus and member list from ShareResponse")
    val shareScores = shares.map(s => (s.minerAddress, (s.diff * ERGO_SHARE_CONSTANT) / s.netDiff))
    logger.warn(s"Using hard-coded constants for ERGO_SHARE_CONSTANT($ERGO_SHARE_CONSTANT) and PPLNS_WINDOW($PPLNS_WINDOW)")
    logger.warn(s"Changes to these values in MiningCore will NOT be reflected in payout code.")
    var totalScore: BigDecimal = BigDecimal("0.0")
    var minerScores = Map[String, BigDecimal]()
    var done = false
    logger.info(shareScores(0)._2.toString())
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
    logger.info("Total Share Score: " + totalScore)

    // Lets simply multiply by 1000000, this ensures we have a good amount of decimal places for each share score
    // The smart contract will take the ratio of share score to it's own calculated total score
    logger.info("Creating list of miner shares")
    val minerShares: Map[String, BigDecimal] = minerScores.map(score => (score._1, (score._2 * BigDecimal("10000"))))
    logger.info(minerShares.toString())
    val baseArray: Array[(Try[Address], Option[Array[Long]])] = minerShares.toArray.map {
      sh =>
        val asAddress = Try {
          Address.create(sh._1)
        }
        // Long array will be updated later by holding box to evaluate minimum and stored payouts
        if (asAddress.isFailure) {
          logger.warn(s"Failed to create address from string ${sh._1}, this address will not be added to consensus.")
        }
        val shareNumber = sh._2.toLong

        val longArray =
          if(shareNumber >= 1) {
            Some(Array(shareNumber, AppParameters.minimumPayout, 0L))
          } else {
            logger.warn(s"Share number for address ${sh._1} was too low to add to consensus")
            None
          }

        (asAddress, longArray)
    }

    val arrayConsensus = baseArray.filter(b => b._1.isSuccess && b._2.isDefined).map(b => (b._1.get.getErgoAddress.script.bytes, b._2.get))
    val memberArray = baseArray.filter(b => b._1.isSuccess && b._2.isDefined).map(b => (b._1.get.getErgoAddress.script.bytes, b._1.get.toString))

    val shareConsensus = ShareConsensus.fromConversionValues(arrayConsensus)
    val memberList = MemberList.fromConversionValues(memberArray)

    (shareConsensus, memberList)
  }

  /**
   * Simplified PPLNS that does not use PPLNS window.
   * @param shares Array of share responses to build consensus and members list from
   * @return New consensus and member list to be used in distribution transaction.
   */
  def simplePPLNSToConsensus(shares: Array[ShareResponse]): (ShareConsensus, MemberList) = {
    logger.info("Creating consensus and member list from ShareResponse")
    val shareScores = shares.map(s => (s.minerAddress, (s.diff * ERGO_SHARE_CONSTANT) / s.netDiff))
    logger.warn(s"Using hard-coded constants for ERGO_SHARE_CONSTANT($ERGO_SHARE_CONSTANT)")
    logger.warn(s"Changes to this values in MiningCore will NOT be reflected in payout code.")

    var totalScore: BigDecimal = BigDecimal("0.0")
    var minerScores = Map[String, BigDecimal]()

    logger.info(shareScores(0)._2.toString())
    for (sc <- shareScores) {
      val shScore = sc._2
      if (minerScores.contains(sc._1)) {
        minerScores = minerScores.updated(sc._1, minerScores(sc._1) + shScore)
      } else {
        minerScores = minerScores + Tuple2(sc._1, shScore)
      }
      totalScore = totalScore + shScore
    }
    logger.info("Total Share Score: " + totalScore)

    // Lets simply multiply by 10000, this ensures we have a good amount of decimal places for each share score
    // The smart contract will take the ratio of share score to it's own calculated total score
    logger.info("Creating list of miner shares")
    val minerShares: Map[String, BigDecimal] = minerScores.map(score => (score._1, (score._2 * BigDecimal("10000"))))
    logger.info(minerShares.toString())
    val baseArray: Array[(Try[Address], Option[Array[Long]])] = minerShares.toArray.map {
      sh =>
        val asAddress = Try {
          Address.create(sh._1)
        }
        // Long array will be updated later by holding box to evaluate minimum and stored payouts
        if (asAddress.isFailure) {
          logger.warn(s"Failed to create address from string ${sh._1}, this address will not be added to consensus.")
        }
        val shareNumber = sh._2.toLong

        val longArray =
          if(shareNumber >= 1) {
            Some(Array(shareNumber, AppParameters.minimumPayout, 0L))
          } else {
            logger.warn(s"Share number for address ${sh._1} was too low to add to consensus")
            None
          }
        (asAddress, longArray)
    }

    val arrayConsensus = baseArray.filter(b => b._1.isSuccess && b._2.isDefined).map(b => (b._1.get.getErgoAddress.script.bytes, b._2.get))
    val memberArray = baseArray.filter(b => b._1.isSuccess && b._2.isDefined).map(b => (b._1.get.getErgoAddress.script.bytes, b._1.get.toString))

    val shareConsensus = ShareConsensus.fromConversionValues(arrayConsensus)
    val memberList = MemberList.fromConversionValues(memberArray)

    (shareConsensus, memberList)
  }


  /**
   * Simplified PPLNS that does not use PPLNS window and supports multiple PPLNS responses
   * @param shares Array of share responses to build consensus and members list from
   * @return New consensus and member list to be used in distribution transaction.
   */
  def simpleMultiPPLNSToConsensus(sharesList: Array[Array[ShareResponse]]): (ShareConsensus, MemberList) = {
    logger.info("Creating consensus and member list from multiple ShareResponses")

    var totalScore: BigDecimal = BigDecimal("0.0")
    var minerScores = Map[String, BigDecimal]()

    for(shares <- sharesList) {
      val shareScores = shares.map(s => (s.minerAddress, (s.diff * ERGO_SHARE_CONSTANT) / s.netDiff))
      logger.warn(s"Using hard-coded constants for ERGO_SHARE_CONSTANT($ERGO_SHARE_CONSTANT)")
      logger.warn(s"Changes to this values in MiningCore will NOT be reflected in payout code.")

      for (sc <- shareScores) {
        val shScore = sc._2
        if (minerScores.contains(sc._1)) {
          minerScores = minerScores.updated(sc._1, minerScores(sc._1) + shScore)
        } else {
          minerScores = minerScores + Tuple2(sc._1, shScore)
        }
        totalScore = totalScore + shScore
      }
      logger.info("Total Share Score: " + totalScore)
    }
    // Lets simply multiply by 10000, this ensures we have a good amount of decimal places for each share score
    // The smart contract will take the ratio of share score to it's own calculated total score
    logger.info("Creating list of miner shares")
    val minerShares: Map[String, BigDecimal] = minerScores.map(score => (score._1, (score._2 * BigDecimal("10000"))))
    logger.info(minerShares.toString())
    val baseArray: Array[(Try[Address], Option[Array[Long]])] = minerShares.toArray.map {
      sh =>
        val asAddress = Try {
          Address.create(sh._1)
        }
        // Long array will be updated later by holding box to evaluate minimum and stored payouts
        if (asAddress.isFailure) {
          logger.warn(s"Failed to create address from string ${sh._1}, this address will not be added to consensus.")
        }
        val shareNumber = sh._2.toLong

        val longArray =
          if(shareNumber >= 1) {
            Some(Array(shareNumber, AppParameters.minimumPayout, 0L))
          } else {
            logger.warn(s"Share number for address ${sh._1} was too low to add to consensus")
            None
          }
        (asAddress, longArray)
    }

    val arrayConsensus = baseArray.filter(b => b._1.isSuccess && b._2.isDefined).map(b => (b._1.get.getErgoAddress.script.bytes, b._2.get))
    val memberArray = baseArray.filter(b => b._1.isSuccess && b._2.isDefined).map(b => (b._1.get.getErgoAddress.script.bytes, b._1.get.toString))

    val shareConsensus = ShareConsensus.fromConversionValues(arrayConsensus)
    val memberList = MemberList.fromConversionValues(memberArray)

    (shareConsensus, memberList)
  }
}
