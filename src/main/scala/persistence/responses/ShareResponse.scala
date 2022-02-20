package persistence.responses

import org.ergoplatform.appkit.Address

import java.util.Date


case class ShareResponse(poolId: String, height: Long, diff: BigDecimal, netDiff: BigDecimal,
                         minerAddress: String, worker: String , userAgent: String, ipAddress: String, source:String, created:Date)

