package boxes

import app.AppParameters
import org.ergoplatform.appkit._
import sigmastate.Values
import sigmastate.serialization.ErgoTreeSerializer

/**
 * Wrapper class that wraps input boxes as command boxes
 * May have any contract
 * @param inputBox Input box to wrap as command box
 */
class CommandInputBox(inputBox: InputBox, smartPoolNFTId: ErgoId, commandContract: ErgoContract) extends InputTemplate(inputBox, smartPoolNFTId) {
  // Explicitly define command contract so as to ensure input box is correct
  override val contract: ErgoContract = commandContract
  assert(asInput.getErgoTree.bytes sameElements contract.getErgoTree.bytes)

  override def toString: String = {
    val serializer = new ErgoTreeSerializer()
    //val shareConsensusDeserialized = shareConsensus.cValue.map{(sc) => (serializer.deserializeErgoTree(sc._1), sc._2)}
    //val shareConsensusWithAddress = shareConsensusDeserialized.map{(sc) => (Address.fromErgoTree(sc._1, AppParameters.networkType), sc._2)}
    val asString = s"""
    Command Box Info:
    - Id: ${this.getId.toString}
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - SmartPool NFT: ${this.getSmartPoolId}
    - Epoch: ${this.getCurrentEpoch}
    - Epoch Height: ${this.getCurrentEpochHeight}
    - Creation Height: ${this.getCreationHeight}
    - Creation ID: ${this.getCreationBox}
    - Share Consensus: ${this.getShareConsensus.getConversionValue.map { (sc: (Array[Byte], Array[Long])) => (sc._1, sc._2.mkString("Array(", ", ", ")")) }.mkString("Array(", ", ", ")")}
    - Share Consensus(DeSerialized): ${this.getShareConsensus.getConversionValue.map { (sc: (Array[Byte], Array[Long])) => (sc._1, sc._2.mkString("Array(", ", ", ")")) }.mkString("Array(", ", ", ")")}
    - Members List: ${this.getMemberList.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Fees: ${this.getPoolFees.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Ops: ${this.getPoolOperators.getConversionValue.mkString("Array(", ", ", ")")}
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
}
