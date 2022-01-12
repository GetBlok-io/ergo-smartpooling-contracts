package persistence.entries

case class BoxIndexEntry(poolId: String, boxId: String, txId: String, epoch: Long, status: String, smartPoolNft: String, subpoolId: String, blocks: Array[Long])
