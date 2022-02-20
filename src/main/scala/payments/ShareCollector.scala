package payments

import persistence.DatabaseConnection
import persistence.queries.{PPLNSQuery, SharesBeforeCreatedQuery}
import persistence.responses.ShareResponse
import persistence.writes.{SharesArchiveInsertion, SharesBeforeCreatedDeletion}

object ShareCollector {

  /**
   * Query shares until PPLNS window is hit
   */
  def queryToWindow(dbConn: DatabaseConnection, poolId: String, blockHeight: Long): Array[Array[ShareResponse]] = {
    var shares = Array[Array[ShareResponse]]()
    var totalShareScore = BigDecimal("0")
    var offset = 0
    while (totalShareScore < PPLNS_WINDOW) {
      logger.info("Now performing PPLNS Query to page shares!")
      logger.info("Old offset: " + offset)
      val response = querySharePage(dbConn, poolId, blockHeight)
      shares = shares ++ Array(response)
      logger.info("Shares length: " + shares.length)
      logger.info("Response length: " + response.length)
      offset = offset + response.length
      if(response.nonEmpty)
        totalShareScore = response.map(s => (s.diff * BigDecimal("256") / s.netDiff)).sum + totalShareScore
      logger.info("totalShareScore: " + totalShareScore)
      logger.info("Query executed successfully")
      logger.info("New offset: " + offset)
    }
    shares
  }

  /**
   * Query shares until QUERY_SIZE
   */
  def querySharePage(dbConn: DatabaseConnection, poolId: String, blockHeight: Long): Array[ShareResponse] = {
    logger.info("Now performing PPLNS Query")
    val pplnsQuery = new PPLNSQuery(dbConn, poolId, blockHeight, QUERY_SIZE, 0)
    val shares: Array[ShareResponse] = pplnsQuery.setVariables().execute().getResponse
    logger.info(s"Query executed successfully with ${shares.length} responses")
    shares
  }

  /**
   * Remove fake shares from query. Should only be used when we are sure fake shares are being produced (From unsynced node)
   */
  @deprecated
  def removeFakes(shareResponses: Array[ShareResponse], fakeHeight: Long, rateConstant: Double): Array[ShareResponse] = {
    var fakeShares = shareResponses.filter(s => s.height == fakeHeight)
    val realShares = shareResponses.filter(s => s.height != fakeHeight)

    logger.info(s"Fake shares: ${fakeShares.length} Real Shares ${realShares.length}")
    fakeShares = fakeShares.slice(0, (fakeShares.length * rateConstant).toInt)
    logger.info(s"Fake shares: ${fakeShares.length} Real Shares ${realShares.length}")
    val totalShares = realShares++fakeShares

    if(totalShares.length != fakeShares.length)
      realShares++fakeShares
    else
      Array[ShareResponse]()
  }

  /**
   * Remove all shares created before the given share. Deleted shares are copied into shares_archive first.
   */
  def removeBeforeLast(dbConn: DatabaseConnection, poolId: String, lastShare: ShareResponse): Long = {
    logger.info(s"Last share has info: created - ${lastShare.created} height - ${lastShare.height} miner - ${lastShare.minerAddress}")
    val sharesBeforeLast = new SharesBeforeCreatedQuery(dbConn, poolId, lastShare.created).setVariables().execute().getResponse
    var archiveNum = 0L
    sharesBeforeLast.foreach{
      s =>
        archiveNum = archiveNum + new SharesArchiveInsertion(dbConn).setVariables(s).execute()
    }
    logger.info(s"Total of $archiveNum rows inserted into shares_archive.")

    if(archiveNum > 0){
      logger.info("Now deleting shares from shares table...")
      val shareDeletion = new SharesBeforeCreatedDeletion(dbConn).setVariables(lastShare).execute()
      logger.info(s"$shareDeletion shares were deleted from share table.")
    }
    archiveNum
  }
}
