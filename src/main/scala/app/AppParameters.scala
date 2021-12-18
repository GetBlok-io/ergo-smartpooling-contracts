package app

import org.ergoplatform.appkit.NetworkType

import java.util.logging.Level

object AppParameters {
  var fileLoggingLevel: Level = Level.INFO
  var consoleLoggingLevel: Level = Level.INFO

  var networkType: NetworkType = NetworkType.TESTNET
  var fromPersistence = false
  var fromFilePath = false
  var smartPoolId = ""
  var configFilePath = ""
}
