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
    logger.info(s"Now querying shares until PPLNS Window $PPLNS_WINDOW")
    while (totalShareScore < PPLNS_WINDOW) {
      logger.info(s"Current offset: $offset")
      val response = querySharePage(dbConn, poolId, blockHeight, offset)
      shares = shares ++ Array(response)
      logger.info("Shares length: " + shares.length)
      logger.info("Response length: " + response.length)
      offset = offset + response.length
      if(response.nonEmpty)
        totalShareScore = response.map(s => (s.diff * BigDecimal("256") / s.netDiff)).sum + totalShareScore
      logger.info("totalShareScore: " + totalShareScore)
      logger.info("Share values updated successfully.")
      if(response.isEmpty){
        logger.info("No responses found, returning shares")
        return shares
      }
    }
    logger.info("queryToWindow complete")
    shares
  }

  /**
   * Query shares until QUERY_SIZE
   */
  def querySharePage(dbConn: DatabaseConnection, poolId: String, blockHeight: Long, offset: Int = 0): Array[ShareResponse] = {
    logger.info(s"Now performing PPLNS Query for blockHeight $blockHeight at offset $offset with QuerySize $QUERY_SIZE")
    val pplnsQuery = new PPLNSQuery(dbConn, poolId, blockHeight, QUERY_SIZE, offset)
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

    var archiveNum = 0L
    var deletionNum = 0L
    var currOffset = 0

    var sharesInserted = new SharesArchiveInsertion(dbConn).setVariables(lastShare).execute()
    archiveNum = sharesInserted
    logger.info(s"Total of $archiveNum rows inserted into shares_archive.")

    if(archiveNum > 0){
      logger.info("Now deleting shares from shares table...")
      val shareDeletion = new SharesBeforeCreatedDeletion(dbConn).setVariables(lastShare).execute()
      logger.info(s"$shareDeletion shares were deleted from share table.")
    }
    archiveNum
  }
}
