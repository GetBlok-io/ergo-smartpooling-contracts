package persistence.queries

import persistence.DatabaseConnection
import persistence.responses.BlockResponse

import java.sql.PreparedStatement
// Query for minimum payouts to input into command box
// TODO: Finish this query for usage in mainnet. Currently not necessary for testnet
class MinimumPayoutsQuery(dbConn: DatabaseConnection, poolId: String, blockheight: Long) extends DatabaseQuery[BlockResponse](dbConn) {
  override val queryString: String =
    """SELECT * FROM miner_settings""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[BlockResponse] = {
    asStatement.setString(1, poolId)
    asStatement.setLong(2, blockheight)
    this
  }

  private var _response: BlockResponse = _

  override def execute(): DatabaseQuery[BlockResponse] = {

    rt = asStatement.executeQuery()

    if(rt.next())
      _response = BlockResponse(
        rt.getLong(1), rt.getString(2), rt.getLong(3),
        rt.getDouble(4), rt.getString(5), rt.getDouble(7),
        rt.getString(10), rt.getDouble(11), rt.getDate(14)
      )

    this
  }

  override def getResponse: BlockResponse = _response
}
