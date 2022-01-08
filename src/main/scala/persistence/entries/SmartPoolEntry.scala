package persistence.entries

import java.sql.Date

case class SmartPoolEntry(poolId: String, transactionHash: String, epoch: Long, height: Long, members: Array[String],
                         fees: Array[Long], info: Array[Long], operators: Array[String], smartPoolNft: String, blocks: Array[Long])
