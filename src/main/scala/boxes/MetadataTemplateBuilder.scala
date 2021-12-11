package boxes

import org.ergoplatform.appkit._
import special.collection.Coll
import values.{MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}

/**
 * Outbox Builder wrapper that treats outboxes like metadata/command boxes
 * @param outBoxBuilder - builder supplied by context to wrap
 */
class MetadataTemplateBuilder(outBoxBuilder: OutBoxBuilder){

  final val asOutBoxBuilder = outBoxBuilder
  var shareConsensus: ShareConsensus = _
  var memberList: MemberList = _
  var poolFees: PoolFees = _
  var poolInfo: PoolInfo = _
  var poolOps: PoolOperators = _
  val registerList: Array[ErgoValue[_]] = new Array[ErgoValue[_]](5)

  def value(value: Long): MetadataTemplateBuilder = { asOutBoxBuilder.value(value); this}

  def contract(contract: ErgoContract): MetadataTemplateBuilder = { asOutBoxBuilder.contract(contract); this}

  def tokens(tokens: ErgoToken*): MetadataTemplateBuilder = { asOutBoxBuilder.tokens(tokens:_*); this}

  def mintToken(token: ErgoToken, tokenName: String, tokenDescription: String, tokenNumberOfDecimals: Int): MetadataTemplateBuilder = {
    asOutBoxBuilder.mintToken(token, tokenName, tokenDescription, tokenNumberOfDecimals);
    this
  }

  /**
   * Custom set registers
   * @param ergoValues register values to set
   * @return Returns this template builder
   */
  def registers(ergoValues: ErgoValue[_]*): MetadataTemplateBuilder = {
    asOutBoxBuilder.registers(ergoValues: _*)
    this
  }

  def creationHeight(height: Int): MetadataTemplateBuilder = {
    asOutBoxBuilder.creationHeight(height)
    this
  }

  def setPoolInfo(info: PoolInfo): MetadataTemplateBuilder ={
    poolInfo = info
    this
  }

  def setConsensus(consensus: ShareConsensus): MetadataTemplateBuilder = {
    shareConsensus = consensus
    this
  }

  def setMembers(members: MemberList): MetadataTemplateBuilder = {
    memberList = members
    this
  }

  def setPoolFees(fees: PoolFees): MetadataTemplateBuilder = {
    poolFees = fees
    this
  }

  def setPoolOps(ops: PoolOperators): MetadataTemplateBuilder = {
    poolOps = ops
    this
  }

  /**
   * Sets registers in format of metadata/command box
   * @return This metadata box builder
   */
  def setMetadata(): MetadataTemplateBuilder = {
    registerList(0) = shareConsensus.getErgoValue
    registerList(1) = memberList.getErgoValue
    registerList(2) = poolFees.getErgoValue
    registerList(3) = poolInfo.getErgoValue
    registerList(4) = poolOps.getErgoValue
    asOutBoxBuilder.registers(registerList: _*)
    this
  }



  def build(): MetadataOutBox = {
    new MetadataOutBox(asOutBoxBuilder.build(), shareConsensus, memberList, poolFees, poolInfo, poolOps)
  }

}
