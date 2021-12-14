package app

import app.TxCommand._
import config.SmartPoolConfig

object SmartPoolingApp{
    def main(args: Array[String]): Unit = {
      val usage = "Usage: java -jar SmartPoolingApp.jar -c=smart/pool/path/config.json [-g|-c|-s|-d]"
      var txCommand = EmptyCommand
      var config: Option[SmartPoolConfig] = None
      for(arg <- args){
        arg match {
          case arg if arg.startsWith("-") =>
            val commandArg = arg.charAt(1)
            commandArg match {
              case 'c' =>
                val commandValue = arg.split("=")(1)
                config = Some(SmartPoolConfig.load(commandValue))
              case 'g' =>
                txCommand = GenerateMetadata
              case 'c' =>
                txCommand = CommandBox
              case 's' =>
                txCommand = SkipEpoch
              case 'd' =>
                txCommand = Distribute
            }
        }
      }
      txCommand match {
        case GenerateMetadata =>

      }
    }


}

object TxCommand extends Enumeration {
  type TxCommand
  val EmptyCommand, GenerateMetadata, CommandBox, SkipEpoch, Distribute = Value
}
