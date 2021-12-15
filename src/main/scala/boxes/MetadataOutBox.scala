package boxes

import boxes.models.OutputTemplate
import org.ergoplatform.appkit._
import sigmastate.Values
import special.collection.Coll
import registers._
import sigmastate.serialization.ErgoTreeSerializer

import java.{lang, util}

/**
 * Wrapper class that wraps output boxes as metadata boxes / command boxes
 *
 * @param outBox Out box to wrap as metadata box / command box
 */
class MetadataOutBox(outBox: OutBox, metadataRegisters: MetadataRegisters, smartPoolId: ErgoId)
                     extends OutputTemplate(outBox, metadataRegisters) {

  def getSmartPoolId: ErgoId = this.smartPoolId
  override def toString: String = {
    def serializer = new ErgoTreeSerializer()
    val deserializedConsensus = this.shareConsensus.getConversionValue.map((sc) => (Address.fromErgoTree(serializer.deserializeErgoTree(sc._1), NetworkType.TESTNET), sc._2.mkString("Array(", ", ", ")"))).mkString("Array(", ", ", ")")
    val deserializedMembers = this.memberList.getConversionValue.map((sc) => (Address.fromErgoTree(serializer.deserializeErgoTree(sc._1), NetworkType.TESTNET), sc._2)).mkString("Array(", ", ", ")")
    val asString = s"""
    Metadata Output Info:
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - SmartPool NFT: ${this.getSmartPoolId}
    - Epoch: ${this.getCurrentEpoch}
    - Epoch Height: ${this.getCurrentEpochHeight}
    - Genesis Epoch Height: ${this.getCreationEpochHeight}
    - Creation ID: ${this.getCreationBox}
    - Last Consensus: ${this.getShareConsensus.getConversionValue.map { (sc: (Array[Byte], Array[Long])) => (sc._1.mkString("Array(", ", ", ")"), sc._2.mkString("Array(", ", ", ")")) }.mkString("Array(", ", ", ")")}
    - Consensus Deserialized: ${deserializedConsensus}
    - Members List: ${this.getMemberList.getConversionValue.mkString("Array(", ", ", ")")}
    - Members Deserialized: ${deserializedMembers}
    - Pool Fees: ${this.getPoolFees.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Ops: ${this.getPoolOperators.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Info: ${this.getPoolInfo.getConversionValue.mkString("Array(", ", ", ")")}
    """
    asString
  }

}
