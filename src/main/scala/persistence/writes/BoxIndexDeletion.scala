package persistence.writes

import logging.LoggingHandler
import org.slf4j.{Logger, LoggerFactory}
import persistence.DatabaseConnection
import persistence.entries.BoxIndexEntry

import java.sql.PreparedStatement

class BoxIndexDeletion(dbConn: DatabaseConnection) extends DatabaseWrite[AnyRef](dbConn){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_PERSISTENCE)

  override val insertionString: String =
    """DELETE FROM box_index""".stripMargin
  override val asStatement: PreparedStatement = dbConn.asConnection.prepareStatement(insertionString)
  // TODO: ugly code
  override def setVariables(emptyRef: AnyRef): DatabaseWrite[AnyRef] = {

    this
  }

  override def execute(): Long = {
    logger.info("Executing update")
    val rowsInserted = asStatement.executeUpdate()
    asStatement.close()
    logger.info(s"Update executed. ${rowsInserted} Rows deleted from box_index in db.")
    rowsInserted
  }
}
