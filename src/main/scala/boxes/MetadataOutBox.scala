package boxes

import org.ergoplatform.appkit._
import sigmastate.Values
import special.collection.Coll
import values._

import java.{lang, util}

/**
 * Wrapper class that wraps output boxes as metadata boxes / command boxes
 *
 * @param outBox Out box to wrap as metadata box / command box
 */
class MetadataOutBox(outBox: OutBox, shareConsensus: ShareConsensus, memberList: MemberList, poolFees: PoolFees, poolInfo: PoolInfo, poolOps: PoolOperators){
  final val asOutBox = this.outBox

  def getValue: lang.Long = asOutBox.getValue

  def convertToInputWith(txId: String, boxIdx: Short) = new MetadataInputBox(asOutBox.convertToInputWith(txId, boxIdx))

  def getRawMetaDataInfo: (Coll[(Coll[Byte], Long)], Coll[(Coll[Byte], Coll[Byte])], Coll[(Coll[Byte], Int)], Coll[Long], Coll[(Coll[Byte], Coll[Byte])]) = {
    (shareConsensus.getNormalValue, memberList.getNormalValue, poolFees.getNormalValue, poolInfo.getNormalValue, poolOps.getNormalValue)
  }

  def getPoolInfo: PoolInfo = {
    poolInfo
  }

  def getCurrentEpoch: Long = {
    poolInfo.getCurrentEpoch
  }

  def getCurrentEpochHeight: Long ={
    poolInfo.getCurrentEpochHeight
  }

  def getCreationHeight: Long ={
    poolInfo.getCreationHeight
  }

  def getCreationBox: String ={
    poolInfo.getCreationBox
  }

  def getShareConsensus: ShareConsensus = {
    shareConsensus
  }

  def getMemberList: MemberList ={
    memberList
  }

  def getPoolFees: PoolFees ={
    poolFees
  }
  def getPoolOperators: PoolOperators ={
    poolOps
  }

  override def toString: String = {
    val asString = s"""
    Metadata OutBox Info:
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - Epoch: ${this.getCurrentEpoch}
    - Epoch Height: ${this.getCurrentEpochHeight}
    - Creation Height: ${this.getCreationHeight}
    - Creation ID: ${this.getCreationBox}
    - Last Consensus: ${this.getShareConsensus.getConversionValue.mkString("Array(", ", ", ")")}
    - Members List: ${this.getMemberList.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Fees: ${this.getPoolFees.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Ops: ${this.getPoolOperators.getConversionValue.mkString("Array(", ", ", ")")}
    """
    asString
  }

}
