package app.api.models

import org.ergoplatform.appkit.ReducedInputData
import org.ergoplatform.restapi.client.{ErgoTransactionDataInput, ErgoTransactionOutput, UnsignedErgoTransaction}

object APIModels {

  case class TokenAmount(tokenId: String, amount: Long)
  case class DataInput(boxId: String)

  case class OutputCandidate(boxId: String, value: Long, ergoTree: String, assets: java.util.List[TokenAmount],
                             additionalRegisters: java.util.Map[String, String], creationHeight: Int)

  case class UnsignedErgoBox(extension: java.util.Map[String, String], boxId: String,
                             value: Long, ergoTree: String, assets: java.util.List[TokenAmount],
                             additionalRegisters: java.util.Map[String, String], creationHeight: Int,
                             transactionId: String, index: Int)

  case class ReducedTransaction(unsignedTx: UnsignedErgoTransaction, reducedInputs: java.util.List[ReducedInputData], txCost: Int)

  case class FullUnsignedTransaction(id: String, inputs: java.util.List[UnsignedErgoBox],
                                     dataInputs: java.util.List[ErgoTransactionDataInput], outputCandidates: java.util.List[ErgoTransactionOutput])
}
