package transactions

import boxes.builders.MetadataOutputBuilder
import contracts.MetadataContract
import org.ergoplatform.appkit._
import transactions.models.TransactionTemplate

import scala.collection.JavaConverters.seqAsJavaListConverter

class GenerateMultipleTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends TransactionTemplate(unsignedTxBuilder){

  private[this] var _creatorAddress: Address = _
  private[this] var _metadataValue: Long = 0L
  private[this] var _txFee: Long = 0L
  private[this] var _metadataContract: ErgoContract = _
  private[this] var _smartPoolToken: ErgoToken = _
  private[this] var _tokenInputBox: InputBox = _

  def tokenInputBox: InputBox = _tokenInputBox

  def tokenInputBox(value: InputBox): GenerateMultipleTx = {
    _tokenInputBox = value
    this
  }

  def smartPoolToken: ErgoToken = _smartPoolToken

  def smartPoolToken(value: ErgoToken): GenerateMultipleTx = {
    _smartPoolToken = value
    this
  }

  def creatorAddress: Address = _creatorAddress

  def creatorAddress(value: Address): GenerateMultipleTx = {
    _creatorAddress = value
    this
  }

  def metadataValue: Long = _metadataValue

  def metadataValue(value: Long): GenerateMultipleTx = {
    _metadataValue = value
    this
  }

  def txFee: Long = _txFee

  def txFee(value: Long): GenerateMultipleTx = {
    _txFee = value
    this
  }

  def metadataContract: ErgoContract = _metadataContract

  def metadataContract(contract: ErgoContract): GenerateMultipleTx = {
    _metadataContract = contract
    this
  }

  override def build(): UnsignedTransaction = {
    var genesisBoxes = Array.empty[OutBox]
    for(i <- 0L to smartPoolToken.getValue - 1){
      val smartPoolSingleton = new ErgoToken(smartPoolToken.getId, 1)
      val genesisBox: OutBox = MetadataContract.buildGenesisBox(new MetadataOutputBuilder(this.outBoxBuilder()), metadataContract, creatorAddress,
        metadataValue, ctx.getHeight, smartPoolSingleton, i)
      genesisBoxes = genesisBoxes++Array(genesisBox)
    }

    val unsignedTx = asUnsignedTxB
      .boxesToSpend(List[InputBox](tokenInputBox).asJava)
      .fee(txFee)
      .sendChangeTo(_creatorAddress.getErgoAddress)
      .outputs(genesisBoxes: _*)
      .build()
    unsignedTx
  }
}
