package boxes

import org.ergoplatform.appkit._
import special.collection.Coll
import registers.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}

import scala.List

/**
 * Outbox Builder wrapper that treats outboxes like metadata boxes
 * @param outBoxBuilder - builder supplied by context to wrap
 */
class MetadataOutputBuilder(outBoxBuilder: OutBoxBuilder){

  final val asOutBoxBuilder = outBoxBuilder
  var shareConsensus: ShareConsensus = _
  var memberList: MemberList = _
  var poolFees: PoolFees = _
  var poolInfo: PoolInfo = _
  var poolOps: PoolOperators = _
  val registerList: Array[ErgoValue[_]] = new Array[ErgoValue[_]](5)
  var smartPoolId: ErgoId = _
  var tokenList: List[ErgoToken] = List[ErgoToken]()

  def value(value: Long): MetadataOutputBuilder = { asOutBoxBuilder.value(value); this}

  def contract(contract: ErgoContract): MetadataOutputBuilder = { asOutBoxBuilder.contract(contract); this}

  def tokens(tokens: ErgoToken*): MetadataOutputBuilder = {tokenList = tokenList++List(tokens:_*); this}


  /**
   * Custom set registers
   * @param ergoValues register registers to set
   * @return Returns this template builder
   */
  def registers(ergoValues: ErgoValue[_]*): MetadataOutputBuilder = {
    asOutBoxBuilder.registers(ergoValues: _*)
    this
  }

  def creationHeight(height: Int): MetadataOutputBuilder = {
    asOutBoxBuilder.creationHeight(height)
    this
  }

  def setPoolInfo(info: PoolInfo): MetadataOutputBuilder ={
    poolInfo = info
    this
  }

  def setConsensus(consensus: ShareConsensus): MetadataOutputBuilder = {
    shareConsensus = consensus
    this
  }

  def setMembers(members: MemberList): MetadataOutputBuilder = {
    memberList = members
    this
  }

  def setPoolFees(fees: PoolFees): MetadataOutputBuilder = {
    poolFees = fees
    this
  }

  def setPoolOps(ops: PoolOperators): MetadataOutputBuilder = {
    poolOps = ops
    this
  }


  def setSmartPoolId(id: ErgoId): MetadataOutputBuilder = {
    smartPoolId = id
    this
  }



  /**
   * Sets registers and smart pool id of metadata box
   * @return This metadata box builder
   */
  def setMetadata(): MetadataOutputBuilder = {
    registerList(0) = shareConsensus.getErgoValue
    registerList(1) = memberList.getErgoValue
    registerList(2) = poolFees.getErgoValue
    registerList(3) = poolInfo.getErgoValue
    registerList(4) = poolOps.getErgoValue
    asOutBoxBuilder.registers(registerList: _*)
    val completeTokenList =
      if(smartPoolId != null) {
        List[ErgoToken](new ErgoToken(smartPoolId, 1L))++tokenList
      }else{
        tokenList
      }
    if(completeTokenList.nonEmpty)
      asOutBoxBuilder.tokens(completeTokenList:_*)

    this
  }



  def build(): MetadataOutBox = {
    new MetadataOutBox(asOutBoxBuilder.build(), shareConsensus, memberList, poolFees, poolInfo, poolOps, smartPoolId)
  }

}
