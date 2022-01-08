package persistence.entries

import java.util.Date

case class PaymentEntry(poolId: String, address: String, amount: Double, txConfirmationData: String)
