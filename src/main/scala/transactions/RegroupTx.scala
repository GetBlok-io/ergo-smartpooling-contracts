package transactions

import boxes.BoxHelpers
import boxes.builders.MetadataOutputBuilder
import contracts.MetadataContract
import contracts.holding.HoldingContract
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import transactions.models.TransactionTemplate

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

class RegroupTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends TransactionTemplate(unsignedTxBuilder){
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_REGROUP_TX)
  private[this] var _feeAddress: Address = _
  private[this] var _txFee: Long = 0L
  private[this] var _holdingContract: HoldingContract = _
  private[this] var _holdingInputs: List[InputBox] = _
  private[this] var _newHoldingValue: Long = 0L

  def feeAddress: Address = _feeAddress

  def feeAddress(value: Address): RegroupTx = {
    _feeAddress = value
    this
  }

  def txFee: Long = _txFee

  def txFee(value: Long): RegroupTx = {
    _txFee = value
    this
  }

  def holdingContract: HoldingContract = _holdingContract

  def holdingContract(value: HoldingContract): RegroupTx = {
    _holdingContract = value
    this
  }

  def holdingInputs: List[InputBox] = _holdingInputs

  def holdingInputs(inputs: List[InputBox]): RegroupTx = {
    _holdingInputs = inputs
    this
  }

  def newHoldingValue: Long = _newHoldingValue

  def newHoldingValue(value: Long): RegroupTx = {
    _newHoldingValue = value
    this
  }


  override def build(): UnsignedTransaction = {
    val heldValue = BoxHelpers.sumBoxes(holdingInputs)

    var outputBoxes = List[OutBox]()

    val holdingOutbox: OutBox =
      asUnsignedTxB.outBoxBuilder()
        .value(newHoldingValue)
        .contract(holdingContract.asErgoContract)
        .build()

    outputBoxes = outputBoxes++List(holdingOutbox)

    val valueDelta = Math.abs(newHoldingValue - heldValue)
    // We only add change box if value delta exists, but users of this class should ensure
    // that a value delta exists so that the transaction is actually useful.
    if(valueDelta != 0){
      val holdingChangeBox: OutBox =
        asUnsignedTxB.outBoxBuilder()
          .value(valueDelta)
          .contract(holdingContract.asErgoContract)
          .build()
      outputBoxes = outputBoxes++List(holdingChangeBox)
    }

    val feeInputs = ctx.getCoveringBoxesFor(feeAddress, txFee, List[ErgoToken]().asJava).getBoxes.asScala

    val unsignedTx = asUnsignedTxB
      .boxesToSpend((holdingInputs++feeInputs).asJava)
      .fee(txFee)
      .sendChangeTo(feeAddress.getErgoAddress)
      .outputs(outputBoxes:_*)
      .build()
    unsignedTx
  }
}
