package transactions

import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import boxes.{CommandInputBox, CommandOutBox, MetadataInputBox, MetadataOutBox}
import contracts.MetadataContract
import contracts.command.CommandContract
import contracts.holding.HoldingContract
import org.ergoplatform.appkit.{ErgoToken, InputBox, Parameters, UnsignedTransaction, UnsignedTransactionBuilder}
import registers.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
import transactions.models.{CommandTxTemplate, MetadataTxTemplate}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

class CreateCommandTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends CommandTxTemplate(unsignedTxBuilder) {

  val txFee: Long = Parameters.MinFee * 2

  protected[this] var _mainHoldingContract: HoldingContract = _
  protected[this] var _holdingValue: Long = 0L
  protected[this] var _withHolding = false

  // Custom metadata registers. These registers are set before the cOB is initialized(epoch and height are updated)
  // Therefore, one must be careful when setting pool info manually
  protected[this] var _shareConsensus: ShareConsensus = _
  protected[this] var _memberList: MemberList = _
  protected[this] var _poolFees: PoolFees = _
  protected[this] var _poolInfo: PoolInfo = _
  protected[this] var _poolOps: PoolOperators = _

  def holdingContract: HoldingContract = _mainHoldingContract

  def withHolding(holdingContract: HoldingContract, holdingVal: Long): CreateCommandTx = {
    _mainHoldingContract = holdingContract
    _holdingValue = holdingVal
    _withHolding = true
    this
  }

  def holdingValue: Long = _holdingValue

  // This metadata input is not used in the transaction, and is only used to copy initial values from.
  def metadataToCopy(value: MetadataInputBox): CreateCommandTx = {
    this._metadataInputBox = value
    this
  }

  def inputBoxes(boxes: InputBox*): CreateCommandTx = {
    this._inputBoxes = boxes.toList
    this
  }

  def withCommandContract(commandContract: CommandContract): CreateCommandTx = {
    _commandContract = commandContract
    this
  }

  def commandValue(value: Long): CreateCommandTx = {
    _commandValue = value
    this
  }

  private def commandOutput(value: CommandOutBox): CreateCommandTx = {
    this._commandOutBox = value
    this
  }

  def setConsensus(shareConsensus: ShareConsensus): CreateCommandTx = {
    _shareConsensus = shareConsensus
    this
  }
  def setMembers(memberList: MemberList): CreateCommandTx = {
    _memberList = memberList
    this
  }
  def setPoolFees(poolFees: PoolFees): CreateCommandTx = {
    _poolFees = poolFees
    this
  }
  def setPoolInfo(poolInfo: PoolInfo): CreateCommandTx = {
    _poolInfo = poolInfo
    this
  }
  def setPoolOps(poolOperators: PoolOperators): CreateCommandTx = {
    _poolOps = poolOperators
    this
  }

  def shareConsensus: ShareConsensus = _shareConsensus
  def memberList: MemberList = _memberList
  def poolFees: PoolFees = _poolFees
  def poolInfo: PoolInfo = _poolInfo
  def poolOperators: PoolOperators = _poolOps

  /**
   * Returns an unsigned Tx that creates a command out box
   * @return Unsigned Transaction that creates command out box with specified registers and
   */
  override def buildCommandTx(): UnsignedTransaction = {

    cOB
      .contract(commandContract)
      .value(commandValue)

    this.applyCustomMetadata
    CommandContract.updatePoolInfo(cOB, metadataInputBox, ctx.getHeight)

    if(_withHolding){
      this.holdingContract.applyToCommand(this)
    }
    val commandOutBox = cOB.build()

    this.commandOutput(commandOutBox)

    this.asUnsignedTxB
      .boxesToSpend(this._inputBoxes.asJava)
      .outputs(commandOutBox)
      .fee(txFee)
      .sendChangeTo(commandContract.getAddress.getErgoAddress)
      .build()

  }

  /**
   * Copies custom set metadata registers onto cOB
   */
  protected def applyCustomMetadata: CommandOutputBuilder = {
    val metadataRegs = metadataInputBox.getMetadataRegisters
    cOB.setMetadata(metadataRegs)
    if(_shareConsensus != null)
      metadataRegs.shareConsensus = _shareConsensus
    if(_memberList != null)
      metadataRegs.memberList = _memberList
    if(_poolFees != null)
      metadataRegs.poolFees = _poolFees
    if(_poolInfo != null)
      metadataRegs.poolInfo = _poolInfo
    if(_poolOps != null)
      metadataRegs.poolOps = _poolOps

    cOB.setMetadata(metadataRegs)
  }

}
