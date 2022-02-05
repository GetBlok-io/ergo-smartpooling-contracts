package app.api.models

import app.api.models.APIModels.{OutputCandidate, TokenAmount}
import org.ergoplatform.appkit.JavaHelpers.UniversalConverter
import org.ergoplatform.appkit.impl.ScalaBridge.{isoErgoTransactionDataInput, isoErgoTransactionOutput}
import org.ergoplatform.appkit.impl.{InputBoxImpl, UnsignedTransactionImpl}
import org.ergoplatform.{DataInput, ErgoBox, ErgoBoxCandidate, UnsignedErgoLikeTransaction}
import org.ergoplatform.appkit.{Address, InputBox, Iso, JavaHelpers, NetworkType, Parameters, ReducedTransaction, UnsignedTransaction}
import org.ergoplatform.restapi.client.{ErgoTransactionDataInput, ErgoTransactionOutput, ErgoTransactionUnsignedInput, UnsignedErgoTransaction}
import org.ergoplatform.settings.ErgoAlgos
import org.ergoplatform.wallet.transactions.TransactionBuilder
import sigmastate.interpreter.ContextExtension

import java.util
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

object APIMapper {

  implicit val isoUnsignedErgoTransaction: Iso[UnsignedErgoTransaction, UnsignedErgoLikeTransaction] = new Iso[UnsignedErgoTransaction, UnsignedErgoLikeTransaction] {
    override def to(apiTx: UnsignedErgoTransaction): UnsignedErgoLikeTransaction =
      new UnsignedErgoLikeTransaction(
        apiTx.getInputs.asScala.map(i => JavaHelpers.createUnsignedInput(i.getBoxId)).toIndexedSeq,
        apiTx.getDataInputs.convertTo[IndexedSeq[DataInput]],
        apiTx.getOutputs.convertTo[IndexedSeq[ErgoBox]]
      )

    override def from(tx: UnsignedErgoLikeTransaction): UnsignedErgoTransaction = {
      val extensionMap = new util.HashMap[String, String]()
      new UnsignedErgoTransaction()
        .id(tx.id)
        .inputs(tx.inputs.map(i => new ErgoTransactionUnsignedInput().boxId(ErgoAlgos.encode(i.boxId)).extension(extensionMap)).asJava)
        .dataInputs(tx.dataInputs.convertTo[util.List[ErgoTransactionDataInput]])
        .outputs(tx.outputs.convertTo[util.List[ErgoTransactionOutput]])
    }
  }

  def buildErgoPayTx(reducedTx: ReducedTransaction): APIModels.ReducedTransaction = {
    APIModels.ReducedTransaction(reducedTx.getTx.unsignedTx.convertTo[UnsignedErgoTransaction], reducedTx.getTx.reducedInputs.asJava, reducedTx.getCost)
  }
  /** Assumes no context extensions or data inputs required */
  def buildEIP12Tx(inputBoxes: Array[InputBox], outputs: Array[ErgoBoxCandidate],
                      changeAddress: Address, fee: Long, height: Int) = {

    val ergoBoxes = inputBoxes.map(i => i.asInstanceOf[InputBoxImpl].getErgoBox)

    val unsignedTx = TransactionBuilder.buildUnsignedTx(
      ergoBoxes, Array.empty[DataInput], outputs.toSeq, height,
      Some(fee), changeAddress.getErgoAddress, Parameters.MinChangeValue,
      Parameters.MinerRewardDelay_Mainnet
    ).get

    val unsignedInputs = ergoBoxes.map{
      b =>
        val tokens = new util.ArrayList[TokenAmount]()
        val regs = new util.HashMap[String, String]()
        b.additionalTokens.toArray.foreach(t => tokens.add(TokenAmount(ErgoAlgos.encode(t._1), t._2)))

        b.additionalRegisters.foreach {
          r => regs.put(r._1.toString(), r._2.toString)
        }

        APIModels.UnsignedErgoBox(new util.HashMap[String, String](), ErgoAlgos.encode(b.id),
        b.value, b.ergoTree.toString, tokens, regs, height, b.transactionId, b.index)
    }
    val outputtedBoxes = unsignedTx.outputs.map(o => o.convertTo[ErgoTransactionOutput]).toList.asJava
    val txId: String = unsignedTx.id

    APIModels.FullUnsignedTransaction(txId, unsignedInputs.toList.asJava,
      List[ErgoTransactionDataInput]().asJava, outputtedBoxes)
  }


}
