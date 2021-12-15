package contracts.command

import boxes.builders.CommandOutputBuilder
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract

class PKContract(p2pkAddress: Address) extends CommandContract(new ErgoTreeContract(p2pkAddress.getErgoAddress.script)) {

  override def getAddress: Address = p2pkAddress

  /**
   * A simple P2PK Command Contract performs no specifically coded changes to the command box. Therefore,
   * we may simply return the command output builder that was passed in.
   */
  def applyToCommand(commandOutputBuilder: CommandOutputBuilder): CommandOutputBuilder = {
    commandOutputBuilder
  }
}


