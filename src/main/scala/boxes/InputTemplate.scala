package boxes

import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{ContextVar, ErgoContract, ErgoId, ErgoToken, ErgoValue, InputBox}
import registers.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
import sigmastate.Values
import special.collection.Coll

import java.{lang, util}

abstract class InputTemplate(inputBox: InputBox, smartPoolNFTId: ErgoId) extends InputBox{
  final val asInput = this.inputBox
  val smartPoolId: ErgoId = smartPoolNFTId
  val shareConsensus: ShareConsensus = new ShareConsensus(asInput.getRegisters.get(0).getValue.asInstanceOf[Coll[(Coll[Byte], Coll[Long])]])
  val memberList: MemberList = new MemberList(asInput.getRegisters.get(1).getValue.asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]])
  val poolFees: PoolFees = new PoolFees(asInput.getRegisters.get(2).getValue.asInstanceOf[Coll[(Coll[Byte], Int)]])
  val poolInfo: PoolInfo = new PoolInfo(asInput.getRegisters.get(3).getValue.asInstanceOf[Coll[Long]])
  val poolOps: PoolOperators = new PoolOperators(asInput.getRegisters.get(4).getValue.asInstanceOf[Coll[(Coll[Byte], Coll[Byte])]])
  val contract: ErgoContract = new ErgoTreeContract(asInput.getErgoTree)

  def getRawMetaDataInfo: (Coll[(Coll[Byte], Coll[Long])], Coll[(Coll[Byte], Coll[Byte])], Coll[(Coll[Byte], Int)], Coll[Long], Coll[(Coll[Byte], Coll[Byte])]) = {
    (shareConsensus.getNormalValue, memberList.getNormalValue, poolFees.getNormalValue, poolInfo.getNormalValue, poolOps.getNormalValue)
  }

  def getId: ErgoId = asInput.getId

  def getSmartPoolId: ErgoId = this.smartPoolId

  def getValue: lang.Long = asInput.getValue

  def getTokens: util.List[ErgoToken] = asInput.getTokens

  def getRegisters: util.List[ErgoValue[_]] = asInput.getRegisters

  def getErgoTree: Values.ErgoTree = asInput.getErgoTree

  def withContextVars(variables: ContextVar*): InputBox = asInput.withContextVars(variables:_*)

  def toJson(prettyPrint: Boolean): String = asInput.toJson(prettyPrint)

  def toJson(prettyPrint: Boolean, formatJson: Boolean): String = asInput.toJson(prettyPrint, formatJson)


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
  def getContract: ErgoContract = {
    ErgoContract
  }
}
