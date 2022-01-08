package persistence.entries

case class BalanceChangeEntry(poolId: String, address: String, amount: Double, usage:String)
