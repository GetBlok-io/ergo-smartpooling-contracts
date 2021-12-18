package persistence.queries

import persistence.DatabaseConnection

import java.sql.{PreparedStatement, ResultSet}

abstract class DatabaseQuery[T](dbConn: DatabaseConnection) {
  val queryString: String
  val asStatement: PreparedStatement
  protected var rt: ResultSet = _

  def setVariables(): DatabaseQuery[T]
  def execute(): DatabaseQuery[T]
  def getResponse: T

}
