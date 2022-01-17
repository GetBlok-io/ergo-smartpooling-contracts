package payments

import app.AppParameters
import org.ergoplatform.appkit.Address

import persistence.responses.ShareResponse
import registers.{MemberList, ShareConsensus}

import scala.util.Try

object StandardPPLNS {
  // Takes a set of share responses and converts them into a tuple of
  // (ShareConsensus, MemberList) so that the values may be easily entered into a command box output
  def standardPPLNSToConsensus(sharesResponseList: Array[Array[ShareResponse]]): (ShareConsensus, MemberList) = {
    logger.info("Creating consensus and member list from ShareResponse")
    var entireShareScore = BigDecimal("0")
    var done = false
    for(shares <- sharesResponseList) {
      if (entireShareScore < PPLNS_WINDOW && !done) {
        val shareScores = shares.map(s => (s.minerAddress, (s.diff * ERGO_SHARE_CONSTANT) / s.netDiff))
        logger.warn(s"Using hard-coded constants for ERGO_SHARE_CONSTANT($ERGO_SHARE_CONSTANT) and PPLNS_WINDOW($PPLNS_WINDOW)")
        logger.warn(s"Changes to these values in MiningCore will NOT be reflected in payout code.")
        var totalScore: BigDecimal = BigDecimal("0.0")

        logger.info(shareScores(0)._2.toString())
        logger.info(shareScores(0)._1)
        for (sc <- shareScores) {
          if (entireShareScore < PPLNS_WINDOW && !done) {
            var shScore = sc._2
            if (entireShareScore + shScore >= PPLNS_WINDOW) {
              shScore = PPLNS_WINDOW - totalScore
              done = true
            }
            if (minerScores.contains(sc._1)) {
              minerScores = minerScores.updated(sc._1, minerScores(sc._1) + shScore)
            } else {
              minerScores = minerScores + Tuple2(sc._1, shScore)
            }
            totalScore = totalScore + shScore
            entireShareScore = entireShareScore + shScore
          }else{
            logger.info("Share score has been calculated!")
          }
        }
        logger.info("Entire Share Score: " + entireShareScore)
        logger.info("Total Share Score: " + totalScore)
      }
      logger.info("EntireShareScore: " + entireShareScore)
    }
    logger.info("Total Share Score: " + entireShareScore)

    // Lets simply multiply by 1000000, this ensures we have a good amount of decimal places for each share score
    // The smart contract will take the ratio of share score to it's own calculated total score 10000000000000000
    logger.info("Creating list of miner shares")
    val minerShares: Map[String, BigDecimal] = minerScores.map(score => (score._1, (score._2 * 10000000L)))
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

  def standardMultiPPLNSToConsensus(sharesList: Array[Array[ShareResponse]]): (ShareConsensus, MemberList) = {
    logger.info("Creating consensus and member list from multiple ShareResponses")

    var totalScore: BigDecimal = BigDecimal("0.0")
    var minerScores = Map[String, BigDecimal]()

    for(shares <- sharesList) {
      val shareScores = shares.map(s => (s.minerAddress, (s.diff * ERGO_SHARE_CONSTANT) / s.netDiff))
      logger.warn(s"Using hard-coded constants for ERGO_SHARE_CONSTANT($ERGO_SHARE_CONSTANT)")
      logger.warn(s"Changes to this values in MiningCore will NOT be reflected in payout code.")

      logger.info(shareScores(0)._2.toString())
      var done = false
      for (sc <- shareScores) {

        if (!done) {
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
