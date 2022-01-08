package persistence.responses

case class SmartPoolResponse(poolId: String, transactionHash: String, epoch: Long, height: Long, smartpoolNFT: String)
