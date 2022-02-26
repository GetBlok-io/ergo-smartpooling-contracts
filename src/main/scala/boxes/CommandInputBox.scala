package boxes

import app.AppParameters
import boxes.models.InputTemplate
import contracts.command.CommandContract
import org.ergoplatform.appkit._
import sigmastate.Values
import sigmastate.serialization.ErgoTreeSerializer

/**
 * Wrapper class that wraps input boxes as command boxes
 * May have any contract
 * @param inputBox Input box to wrap as command box
 */
class CommandInputBox(inputBox: InputBox, commandContract: CommandContract) extends InputTemplate(inputBox) {
  // Explicitly define command contract so as to ensure input box is correct
  override val contract: CommandContract = commandContract
  assert(asInput.getErgoTree.bytes sameElements contract.getErgoTree.bytes)

  override def toString: String = {
    def serializer = new ErgoTreeSerializer()
    val deserializedConsensus = this.shareConsensus.getConversionValue.map((sc) => (Address.fromErgoTree(serializer.deserializeErgoTree(sc._1), NetworkType.TESTNET), sc._2.mkString("Array(", ", ", ")"))).mkString("Array(", ", ", ")")
    val deserializedMembers = this.memberList.getConversionValue.map((sc) => (Address.fromErgoTree(serializer.deserializeErgoTree(sc._1), NetworkType.TESTNET), sc._2)).mkString("Array(", ", ", ")")
    val deserializedOps = this.poolOps.getConversionValue.map((sc) => (Address.fromErgoTree(serializer.deserializeErgoTree(sc._1), NetworkType.TESTNET), sc._2)).mkString("Array(", ", ", ")")
    val asString = s"""
    Command Box Info:
    - Id: ${this.getId.toString}
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - Epoch: ${this.getCurrentEpoch}
    - Epoch Height: ${this.getCurrentEpochHeight}
    - Creation Height: ${this.getCreationHeight}
    - Subpool ID: ${this.getSubpoolId}
    - Last Consensus: ${this.getShareConsensus.getConversionValue.map { (sc: (Array[Byte], Array[Long])) => (sc._1.mkString("Array(", ", ", ")"), sc._2.mkString("Array(", ", ", ")")) }.mkString("Array(", ", ", ")")}
    - Consensus Deserialized: ${deserializedConsensus}
    - Members List: ${this.getMemberList.getConversionValue.mkString("Array(", ", ", ")")}
    - Members Deserialized: ${deserializedMembers}
    - Pool Fees: ${this.getPoolFees.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Ops: ${this.getPoolOperators.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Ops Deserialized: ${deserializedOps}
    """
    asString
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case box: CommandInputBox => this.getId.equals(box.getId)
      case _ => false
    }
  }

  override def getErgoTree: Values.ErgoTree = contract.getErgoTree

  override def getBytes: Array[Byte] = asInput.getBytes
}
