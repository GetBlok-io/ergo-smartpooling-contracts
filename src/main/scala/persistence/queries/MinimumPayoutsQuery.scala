package persistence.queries

import persistence.DatabaseConnection
import persistence.responses.{BlockResponse, SettingsResponse}

import java.sql.{Date, PreparedStatement}
// Query for minimum payouts to input into command box

class MinimumPayoutsQuery(dbConn: DatabaseConnection, poolId: String, address: String) extends DatabaseQuery[SettingsResponse](dbConn) {
  override val queryString: String =
    """SELECT * FROM miner_settings WHERE poolid = ? AND address = ?""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[SettingsResponse] = {
    asStatement.setString(1, poolId)
    asStatement.setString(2, address)
    this
  }

  private var _response: SettingsResponse = _

  override def execute(): DatabaseQuery[SettingsResponse] = {

    rt = asStatement.executeQuery()

    if(rt.next()) {
      _response = SettingsResponse(
       rt.getString(1), rt.getString(2), rt.getDouble(3), rt.getString(6)
      )
    }else{
      // We create a default response if no response is found.
      _response = SettingsResponse(poolId, address, 0.1, "")
    }

    this
  }

  override def getResponse: SettingsResponse = _response
}
