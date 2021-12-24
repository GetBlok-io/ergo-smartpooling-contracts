package persistence.responses

import java.sql.Date

case class BlockResponse(id: Long, poolid: String, blockheight: Long, netDiff: Double,
                         status: String, confirmationProgress: Double, miner: String, reward: Double,
                         created: Date)


