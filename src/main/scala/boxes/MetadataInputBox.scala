package boxes

import org.ergoplatform.appkit._
import sigmastate.Values
import special.collection.Coll
import values.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}

import java.nio.charset.StandardCharsets
import java.{lang, util}
import scala.math.BigInt

/**
 * Wrapper class that wraps input boxes as metadata boxes / command boxes
 * @param inputBox Input box to wrap as metadata box / command box
 */
class MetadataInputBox(inputBox: InputBox) extends InputBox{
  final val asInput = this.inputBox
  val shareConsensus: ShareConsensus = new ShareConsensus(this.getRegisters.get(0).getValue.asInstanceOf[Coll[(Coll[Byte], Long)]])
  val memberList: MemberList = new MemberList(this.getRegisters.get(1).getValue.asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]])
  val poolFees: PoolFees = new PoolFees(this.getRegisters.get(2).getValue.asInstanceOf[Coll[(Coll[Byte], Int)]])
  val poolInfo: PoolInfo = new PoolInfo(this.getRegisters.get(3).getValue.asInstanceOf[Coll[Long]])
  val poolOps: PoolOperators = new PoolOperators(this.getRegisters.get(4).getValue.asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]])


  def getId: ErgoId = asInput.getId

  def getValue: lang.Long = asInput.getValue

  def getTokens: util.List[ErgoToken] = asInput.getTokens

  def getRegisters: util.List[ErgoValue[_]] = asInput.getRegisters

  def getErgoTree: Values.ErgoTree = asInput.getErgoTree

  def withContextVars(variables: ContextVar*): InputBox = asInput.withContextVars(variables:_*)

  def toJson(prettyPrint: Boolean): String = asInput.toJson(prettyPrint)

  def toJson(prettyPrint: Boolean, formatJson: Boolean): String = asInput.toJson(prettyPrint, formatJson)

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
    Metadata Box Info:
    - Id: ${this.getId.toString}
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

  override def equals(obj: Any): Boolean = {
    obj match {
      case box: MetadataInputBox => this.getId.equals(box.getId)
      case _ => false
    }
  }
}
