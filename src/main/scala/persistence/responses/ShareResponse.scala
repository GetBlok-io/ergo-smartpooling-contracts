package persistence.responses

import org.ergoplatform.appkit.Address

import java.sql.Date

case class ShareResponse(poolId: String, height: Long, diff: BigDecimal, netDiff: BigDecimal, minerAddress: String, source:String, created:Date)

