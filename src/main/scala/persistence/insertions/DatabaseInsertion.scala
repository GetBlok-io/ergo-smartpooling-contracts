package persistence.insertions

import persistence.DatabaseConnection

import java.sql.{PreparedStatement, ResultSet}

abstract class DatabaseInsertion[T](dbConn: DatabaseConnection) {
  val insertionString: String
  val asStatement: PreparedStatement
  protected var rowsAffected: Long = _

  def setVariables(entry: T): DatabaseInsertion[T]
  def execute(): Long

}
