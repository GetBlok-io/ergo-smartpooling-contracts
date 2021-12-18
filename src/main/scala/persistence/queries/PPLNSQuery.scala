package persistence.queries

import persistence.DatabaseConnection
import persistence.responses.ShareResponse

import java.sql.{Date, PreparedStatement, ResultSet}

// Query the last N shares created before a certain date. Used
class PPLNSQuery(dbConn: DatabaseConnection, poolId: String, before: Date, numShares: Int) extends DatabaseQuery[Array[ShareResponse]](dbConn) {
  override val queryString: String =
    """SELECT * FROM shares WHERE poolid = ?
      | AND created <= ? ORDER BY created DESC FETCH NEXT ? ROWS ONLY""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[Array[ShareResponse]] = {
    asStatement.setString(1, poolId)
    asStatement.setDate(2, before)
    asStatement.setInt(3, numShares)

    this
  }

  private var _response: Array[ShareResponse] = Array[ShareResponse]()

  override def execute(): DatabaseQuery[Array[ShareResponse]] = {

    rt = asStatement.executeQuery()
    var newResponse = Array[ShareResponse]()
    while(rt.next()){
      val asShareResponse =
        ShareResponse(
          rt.getString(1), rt.getInt(2), rt.getDouble(3),
          rt.getDouble(4), rt.getString(5),
          rt.getString(9), rt.getDate(10)
        )

      newResponse = newResponse++Array(asShareResponse)
    }
    _response = newResponse
    this
  }

  override def getResponse: Array[ShareResponse] = _response
}
