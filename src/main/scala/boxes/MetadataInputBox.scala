package boxes

import app.AppParameters
import boxes.models.InputTemplate
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.Values
import special.collection.Coll
import registers.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
import sigmastate.serialization.ErgoTreeSerializer
import contracts.MetadataContract

import java.nio.charset.StandardCharsets
import java.{lang, util}
import scala.math.BigInt

/**
 * Wrapper class that wraps input boxes as metadata boxes / command boxes
 * The metadata input box ensures that token 0 is equal to the smart pool id
 * @param inputBox Input box to wrap as metadata box / command box
 */
class MetadataInputBox(inputBox: InputBox, smartPoolNFTId: ErgoId) extends InputTemplate(inputBox) {
  val smartPoolId: ErgoId = smartPoolNFTId
  if(this.getCurrentEpoch != 0) {
    require(smartPoolNFTId == this.getTokens.get(0).getId)
  } else {
    if(this.getTokens.size() > 0){
      require(smartPoolNFTId == this.getTokens.get(0).getId)
    }else {
      require(smartPoolNFTId == this.getId)
    }
  }

  override def toString: String = {
    val serializer = new ErgoTreeSerializer()
    //val shareConsensusDeserialized = shareConsensus.cValue.map{(sc) => (serializer.deserializeErgoTree(sc._1), sc._2)}
    //val shareConsensusWithAddress = shareConsensusDeserialized.map{(sc) => (Address.fromErgoTree(sc._1, AppParameters.networkType), sc._2)}
    val deserializedOps = this.poolOps.getConversionValue.map((sc) => (Address.fromErgoTree(serializer.deserializeErgoTree(sc._1), AppParameters.networkType), sc._2)).mkString("Array(", ", ", ")")

    val asString = s"""
    Metadata Box Info:
    - Id: ${this.getId.toString}
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - SmartPool NFT: ${this.getSmartPoolId}
    - Epoch: ${this.getCurrentEpoch}
    - Epoch Height: ${this.getCurrentEpochHeight}
    - Creation Height: ${this.getCreationHeight}
    - Subpool Id: ${this.getSubpoolId}
    - Share Consensus: ${this.getShareConsensus.getConversionValue.map { (sc: (Array[Byte], Array[Long])) => (sc._1, sc._2.mkString("Array(", ", ", ")")) }.mkString("Array(", ", ", ")")}
    - Members List: ${this.getMemberList.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Fees: ${this.getPoolFees.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Ops: ${this.getPoolOperators.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Ops Deserialized: ${deserializedOps}
    """
    asString
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case box: MetadataInputBox => this.getId.equals(box.getId)
      case _ => false
    }
  }

  def getSmartPoolId: ErgoId = this.smartPoolId

  override def getBytes: Array[Byte] = asInput.getBytes
}
