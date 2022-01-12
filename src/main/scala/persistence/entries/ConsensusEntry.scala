package persistence.entries

import java.sql.Date

case class ConsensusEntry(poolId: String, transactionHash: String, epoch: Long, height: Long, smartPoolNft: String,
                          miner: String, shares: Long, minPayout: Long, storedPayout: Long, paid: Long, subpoolId: String)
