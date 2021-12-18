package persistence

import persistence.queries.DatabaseQuery

import java.sql.Connection

class DatabaseConnection(c: Connection){
  val asConnection: Connection = c
  def close(): Unit = c.close()
}
