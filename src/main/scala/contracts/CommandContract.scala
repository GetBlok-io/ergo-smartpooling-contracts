package contracts

import app.AppParameters
import boxes.{CommandOutBox, CommandOutputBuilder, MetadataInputBox}
import org.ergoplatform.appkit._
import registers._
import sigmastate.Values

abstract class CommandContract(commandContract: ErgoContract) extends ErgoContract {
  override def getConstants: Constants = commandContract.getConstants

  override def getErgoScript: String = commandContract.getErgoScript

  override def substConstant(name: String, value: Any): ErgoContract = commandContract.substConstant(name, value)

  override def getErgoTree: Values.ErgoTree = commandContract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  /**
   * Apply this command contract's effects to an unbuilt command output. Multiple command contracts
   * may apply their effects so as to get the final value of the outputted command box.
   * @param commandOutputBuilder
   * @return
   */
  def applyToCommand(commandOutputBuilder: CommandOutputBuilder): CommandOutputBuilder
}
object CommandContract {

  /**
   * Build a CommandOutBox with the given Smart Pool registers
   *
   * @param cOB    builder to wrap
   * @param commandContract ErgoContract to make metadata template box under
   * @param value     Value to use for output box
   * @return New CommandOutBox with given registers
   */
  def buildCommandBox(cOB: CommandOutputBuilder, commandContract: CommandContract, value: Long,
                      metadataRegisters: MetadataRegisters): CommandOutBox = {
    cOB
      .contract(commandContract)
      .value(value)
      .setMetadata(metadataRegisters)
      .build()
  }

  /**
   * Copy the contents of a command outbox into an unbuilt template builder.
   *
   * @param commandOutBox out box to copy from
   * @param outBoxBuilder       outBoxBuilder to build output from
   * @param commandContract    Contract to use for metadata-like output
   * @return New MetadataOutBox with same exact register registers
   */
  def copyCommandOutBox(cOB: CommandOutputBuilder, commandOutBox: CommandOutBox, commandContract: CommandContract): CommandOutputBuilder = {
    cOB
      .contract(commandContract)
      .value(commandOutBox.getValue)
      .setMetadata(commandOutBox.getMetadataRegisters)
    cOB
  }

  def initializeOutputBuilder(cOB: CommandOutputBuilder, metadataInputBox: MetadataInputBox): CommandOutputBuilder = {

  }


}

