package persistence.responses

import java.util.Date

case class PaymentResponse(poolId: String, address: String, amount: Double, txConfirmationData: String)
