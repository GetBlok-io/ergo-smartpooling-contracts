package boxes

import org.ergoplatform.appkit._
import sigmastate.Values
import special.collection.Coll
import registers._

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
    val asString = s"""
    Metadata Output Template Info:
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - SmartPool NFT: ${this.getSmartPoolId}
    - Epoch: ${this.getCurrentEpoch}
    - Epoch Height: ${this.getCurrentEpochHeight}
    - Creation Height: ${this.getCreationHeight}
    - Creation ID: ${this.getCreationBox}
    - Last Consensus: ${this.getShareConsensus.getConversionValue.map { (sc: (Array[Byte], Array[Long])) => (sc._1.mkString("Array(", ", ", ")"), sc._2.mkString("Array(", ", ", ")")) }.mkString("Array(", ", ", ")")}
    - Members List: ${this.getMemberList.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Fees: ${this.getPoolFees.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Ops: ${this.getPoolOperators.getConversionValue.mkString("Array(", ", ", ")")}
    """
    asString
  }

}
