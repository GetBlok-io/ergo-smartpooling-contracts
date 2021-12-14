package app

import config.SmartPoolConfig

abstract class SmartPoolCommand(smartPoolConfig: SmartPoolConfig) {
  val txCommand: TxCommand.Value
}

