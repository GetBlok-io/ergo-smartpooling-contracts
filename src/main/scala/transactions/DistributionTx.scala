package transactions

import boxes.{CommandInputBox, CommandOutBox, MetadataInputBox, MetadataOutBox}
import boxes.builders.{HoldingOutputBuilder, MetadataOutputBuilder}
import contracts.MetadataContract
import contracts.command.{CommandContract, PKContract}
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.{BlockchainContext, ErgoToken, InputBox, NetworkType, OutBox, OutBoxBuilder, Parameters, PreHeader, SignedTransaction, UnsignedTransaction, UnsignedTransactionBuilder}
import transactions.models.{MetadataTxTemplate, TransactionTemplate}

import java.util
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

class DistributionTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends MetadataTxTemplate(unsignedTxBuilder) {

  private var hOB: HoldingOutputBuilder = _
  private[this] var _mainHoldingContract: HoldingContract = _
  private[this] var _otherCommandContracts: List[CommandContract] = List[CommandContract]()
  private[this] var _holdingValue: Long = 0L


  private def otherCommandContracts: List[CommandContract] = _otherCommandContracts

  private def otherCommandContracts(contracts: List[CommandContract]): Unit = {
    _otherCommandContracts = contracts.toList

  }

  def holdingContract: HoldingContract = _mainHoldingContract

  def holdingContract(holdingContract: HoldingContract): DistributionTx = {
    _mainHoldingContract = holdingContract
    this
  }

  def holdingValue: Long = _holdingValue

  def holdingValue(value: Long): DistributionTx = {
    _holdingValue = value
    this
  }

  def withCommandContracts(commandContracts: CommandContract*): DistributionTx = {
    otherCommandContracts(commandContracts.toList)
    this
  }

  def metadataInput(value: MetadataInputBox): DistributionTx = {
    this._metadataInputBox = value
    this
  }

  def commandInput(value: CommandInputBox): DistributionTx = {
    this._commandInputBox = value
    this
  }

  def metadataOutput(value: MetadataOutBox): DistributionTx = {
    this._metadataOutBox = value
    this
  }

  override def buildMetadataTx(): UnsignedTransaction = {
    val commandContract = commandInputBox.contract
    val holdingAddress = holdingContract.getAddress
    val holdingBoxes = ctx.getCoveringBoxesFor(holdingAddress, holdingValue, List[ErgoToken]().asJava).getBoxes
    val metadataContract = metadataInputBox.getContract

    val initBoxes = List(metadataInputBox.asInput, commandInputBox.asInput)
    val inputBoxes = initBoxes++holdingBoxes.asScala

    metadataOutput(MetadataContract.buildFromCommandBox(mOB, commandInputBox, metadataContract, metadataInputBox.getValue, metadataInputBox.getSmartPoolId))

    hOB = holdingContract
      .generateInitialOutputs(ctx, this, holdingBoxes.asScala.toList)
      .applyCommandContract(commandContract)

    otherCommandContracts.foreach(c => hOB.applyCommandContract(c))

    val holdingOutputs = hOB.build()

    val txFee = commandInputBox.getValue + (commandInputBox.getShareConsensus.nValue.size * Parameters.MinFee)
    val outputBoxes = List(metadataOutBox.asOutBox)++(holdingOutputs.map(h => h.asOutBox))
    outputBoxes.foreach(x => println(x.getValue))
      this.asUnsignedTxB
      .boxesToSpend(inputBoxes.asJava)
      .outputs(outputBoxes:_*)
      .fee(txFee)
      .sendChangeTo(commandInputBox.contract.getAddress.getErgoAddress)
      .build()

  }


}
