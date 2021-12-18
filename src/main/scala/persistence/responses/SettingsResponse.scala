package persistence.responses

import java.sql.Date
// TODO: Finish response for mainnet integration
case class SettingsResponse(poolid: String, miner: String, reward: Double,
                            created: Date)


