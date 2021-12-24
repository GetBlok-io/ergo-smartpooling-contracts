package transactions

import boxes.builders.{CommandOutputBuilder, MetadataOutputBuilder}
import boxes.{CommandInputBox, CommandOutBox, MetadataInputBox, MetadataOutBox}
import contracts.MetadataContract
import org.ergoplatform.appkit._
import transactions.models.{MetadataTxTemplate, TransactionTemplate}

import scala.collection.JavaConverters.seqAsJavaListConverter

class GenesisTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends TransactionTemplate(unsignedTxBuilder){

  private[this] var _creatorAddress: Address = _
  private[this] var _metadataValue: Long = 0L
  private[this] var _txFee: Long = 0L
  private[this] var _metadataContract: ErgoContract = _

  def creatorAddress: Address = _creatorAddress

  def creatorAddress(value: Address): GenesisTx = {
    _creatorAddress = value
    this
  }

  def metadataValue: Long = _metadataValue

  def metadataValue(value: Long): GenesisTx = {
    _metadataValue = value
    this
  }

  def txFee: Long = _txFee

  def txFee(value: Long): GenesisTx = {
    _txFee = value
    this
  }

  def metadataContract: ErgoContract = _metadataContract

  def metadataContract(contract: ErgoContract): GenesisTx = {
    _metadataContract = contract
    this
  }

  override def build(): UnsignedTransaction = {
    val genesisBox: OutBox = MetadataContract.buildGenesisBox(new MetadataOutputBuilder(this.outBoxBuilder()), metadataContract, creatorAddress, metadataValue, ctx.getHeight)

    val creatorBoxes = ctx.getCoveringBoxesFor(_creatorAddress, _metadataValue + txFee, List[ErgoToken]().asJava).getBoxes
    val unsignedTx = asUnsignedTxB
      .boxesToSpend(creatorBoxes)
      .fee(txFee)
      .sendChangeTo(_creatorAddress.getErgoAddress)
      .outputs(genesisBox)
      .build()
    unsignedTx
  }
}
