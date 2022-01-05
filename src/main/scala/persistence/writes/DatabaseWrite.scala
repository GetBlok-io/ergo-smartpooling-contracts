package persistence.writes

import persistence.DatabaseConnection

import java.sql.{PreparedStatement, ResultSet}

abstract class DatabaseWrite[T](dbConn: DatabaseConnection) {
  val insertionString: String
  val asStatement: PreparedStatement
  protected var rowsAffected: Long = _

  def setVariables(entry: T): DatabaseWrite[T]
  def execute(): Long

}
