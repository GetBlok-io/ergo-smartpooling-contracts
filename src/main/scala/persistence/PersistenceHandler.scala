package persistence

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}

import java.sql.{Connection, DriverManager}
import java.util.Properties

class PersistenceHandler(hostName: Option[String], portNum: Option[Int], dbName: Option[String]) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  private val baseUrl = "jdbc:postgresql:"
  private val properties: Properties = new Properties()

  private def buildDBUrl: String = {
    val hostDefined = hostName.isDefined
    val portDefined = portNum.isDefined
    val dbDefined = dbName.isDefined
    var dbUrl = baseUrl

    if(hostDefined){
      dbUrl = dbUrl++s"//${hostName.get}"
      if(portDefined){
        dbUrl = dbUrl++s":${portNum.get}/"
      }
    }

    if(dbDefined){
      dbUrl = dbUrl++dbName.get
    }
    dbUrl
  }

  def setConnectionProperties(userName: String, password: String, ssl: Boolean): Unit ={
    properties.setProperty("user", userName)
    properties.setProperty("password", password)
    properties.setProperty("ssl", ssl.toString)
  }

  def connectToDatabase: DatabaseConnection = {
    val dbUrl = buildDBUrl
    val conn: Connection = DriverManager.getConnection(dbUrl, properties)
    new DatabaseConnection(conn)
  }
}
