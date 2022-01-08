package persistence.responses

case class ConsensusResponse(poolId: String, transactionHash: String, smartpoolNFT: String,
                             miner: String, shares: Long, minPayout: Long, storedPayout: Long, valuePaid: Long)
