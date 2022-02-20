package persistence.responses

case class BoxIndexResponse(poolId: String, boxId: String, txId: String, epoch: Long, status: String, smartPoolNft: String, subpoolId: String, blocks: Array[Long])
