package boxes.builders

import boxes.CommandOutBox
import contracts.command.CommandContract
import org.ergoplatform.appkit._
import registers._

/**
 * Outbox Builder wrapper that treats outboxes like metadata/command boxes
 * @param outBoxBuilder - builder supplied by context to wrap
 */
class CommandOutputBuilder(outBoxBuilder: OutBoxBuilder){

  final val asOutBoxBuilder = outBoxBuilder
  var metadataRegisters: MetadataRegisters = _
  var registerList: Array[ErgoValue[_]] = new Array[ErgoValue[_]](5)

  def value(value: Long): CommandOutputBuilder = { asOutBoxBuilder.value(value); this}

  def contract(contract: CommandContract): CommandOutputBuilder = { asOutBoxBuilder.contract(contract); this}

  def tokens(tokens: ErgoToken*): CommandOutputBuilder = { asOutBoxBuilder.tokens(tokens:_*); this}


  /**
   * Custom set registers
   * @param ergoValues register registers to set
   * @return Returns this template builder
   */
  def registers(ergoValues: ErgoValue[_]*): CommandOutputBuilder = {
    registerList = Array(ergoValues: _*)
    this
  }

  def creationHeight(height: Int): CommandOutputBuilder = {
    asOutBoxBuilder.creationHeight(height)
    this
  }

  /**
   * Sets registers in format of command box
   * @return This command box builder
   */
  def setMetadata(metadataRegs: MetadataRegisters): CommandOutputBuilder = {
    metadataRegisters = metadataRegs
    registerList(0) = metadataRegs.shareConsensus.getErgoValue
    registerList(1) = metadataRegs.memberList.getErgoValue
    registerList(2) = metadataRegs.poolFees.getErgoValue
    registerList(3) = metadataRegs.poolInfo.getErgoValue
    registerList(4) = metadataRegs.poolOps.getErgoValue
    this
  }



  def build(): CommandOutBox = {
    asOutBoxBuilder.registers(registerList: _*)
    new CommandOutBox(asOutBoxBuilder.build(), metadataRegisters)
  }

}
