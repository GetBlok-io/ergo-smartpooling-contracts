package persistence.responses

import org.ergoplatform.appkit.Address

import java.sql.Date

case class ShareResponse(poolId: String, height: Int, diff: Double, netDiff: Double, minerAddress: String, source:String, created:Date)

