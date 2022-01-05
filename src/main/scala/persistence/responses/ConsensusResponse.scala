package persistence.responses

case class ConsensusResponse(poolId: String, transactionHash: String, epoch: Long, height: Long, smartPoolNft: String,
                             miner: String, shares: Long, minPayout: Long, storedPayout: Long)
