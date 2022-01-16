package persistence.queries

import persistence.DatabaseConnection
import persistence.responses.{PaymentResponse, SettingsResponse}

import java.sql.PreparedStatement


class PaymentsQuery(dbConn: DatabaseConnection, poolId: String, address: String, transactionhash: String) extends DatabaseQuery[Array[PaymentResponse]](dbConn) {
  override val queryString: String =
    """SELECT * FROM payments WHERE poolid = ? AND address = ? AND transactionconfirmationdata = ? ORDER BY created""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(queryString)

  override def setVariables(): DatabaseQuery[Array[PaymentResponse]] = {
    asStatement.setString(1, poolId)
    asStatement.setString(2, address)
    asStatement.setString(3, transactionhash)
    this
  }

  private var _response: Array[PaymentResponse] = Array[PaymentResponse]()

  override def execute(): DatabaseQuery[Array[PaymentResponse]] = {

    rt = asStatement.executeQuery()

    while(rt.next()) {
      _response = _response++Array(PaymentResponse(
       rt.getString(2), rt.getString(4), rt.getDouble(5), rt.getString(6)
      ))
    }

    this
  }

  override def getResponse: Array[PaymentResponse] = _response
}
