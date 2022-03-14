package boxes.models

import org.ergoplatform.appkit._
import registers._
import special.collection.Coll

/**
 * Wrapper class that wraps output boxes as metadata boxes / command boxes
 *
 * @param outBox Out box to wrap as metadata box / command box
 */
abstract class OutputTemplate(outBox: OutBox, metadataRegs: MetadataRegisters) extends OutBox{
  final val asOutBox = this.outBox
  private val metadataRegisters = metadataRegs
  val shareConsensus: ShareConsensus = metadataRegisters.shareConsensus
  val memberList: MemberList = metadataRegisters.memberList
  val poolFees: PoolFees = metadataRegisters.poolFees
  val poolInfo: PoolInfo = metadataRegisters.poolInfo
  val poolOps: PoolOperators = metadataRegisters.poolOps

  def getValue: Long = asOutBox.getValue

  def convertToInputWith(txId: String, boxIdx: Short): InputBox = asOutBox.convertToInputWith(txId, boxIdx)

  def getRawMetaDataInfo: (Coll[(Coll[Byte], Coll[Long])], Coll[(Coll[Byte], Coll[Byte])], Coll[(Coll[Byte], Int)], Coll[Long], Coll[(Coll[Byte], Coll[Byte])]) = {
    (shareConsensus.getNormalValue, memberList.getNormalValue, poolFees.getNormalValue, poolInfo.getNormalValue, poolOps.getNormalValue)
  }

  override def getTokens: java.util.List[ErgoToken] = asOutBox.getTokens

  /**
   * Get copy of metadata registers. Actual MetadataRegisters is not returned since OutBox is immutable
   * @return New copy of this OutBox's MetadataRegisters
   */
  def getMetadataRegisters: MetadataRegisters = {
    metadataRegisters.copy
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

  def getCreationEpochHeight: Long = {
    poolInfo.getCreationHeight
  }

  def getSubpoolId: Long ={
    poolInfo.getSubpoolId
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

  override def getCreationHeight: Int = asOutBox.getCreationHeight

  override def toString: String = {
    val asString = s"""
    Output Template Info:
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - Epoch: ${this.getCurrentEpoch}
    - Epoch Height: ${this.getCurrentEpochHeight}
    - Creation Height: ${this.getCreationHeight}
    - Creation ID: ${this.getSubpoolId}
    - Last Consensus: ${this.getShareConsensus.getConversionValue.map { (sc: (Array[Byte], Array[Long])) => (sc._1.mkString("Array(", ", ", ")"), sc._2.mkString("Array(", ", ", ")")) }.mkString("Array(", ", ", ")")}
    - Members List: ${this.getMemberList.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Fees: ${this.getPoolFees.getConversionValue.mkString("Array(", ", ", ")")}
    - Pool Ops: ${this.getPoolOperators.getConversionValue.mkString("Array(", ", ", ")")}
    """
    asString
  }

}
