package boxes.builders

import boxes.MetadataOutBox
import org.ergoplatform.appkit._
import registers.MetadataRegisters

/**
 * Outbox Builder wrapper that treats outboxes like metadata boxes
 * @param outBoxBuilder - builder supplied by context to wrap
 */
class MetadataOutputBuilder(outBoxBuilder: OutBoxBuilder){

  final val asOutBoxBuilder = outBoxBuilder
  var metadataRegisters: MetadataRegisters = _
  var registerList: Array[ErgoValue[_]] = new Array[ErgoValue[_]](5)
  var smartPoolId: ErgoId = _
  var tokenList: List[ErgoToken] = List[ErgoToken]()
  var boxValue: Long = 0L
  var boxContract: ErgoContract = _
  var boxCreationHeight: Int = _

  def value(value: Long): MetadataOutputBuilder = { asOutBoxBuilder.value(value); boxValue = value; this}

  def contract(contract: ErgoContract): MetadataOutputBuilder = { asOutBoxBuilder.contract(contract); boxContract = contract; this}

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
    boxCreationHeight = height
    this
  }

  def setSmartPoolId(id: ErgoId): MetadataOutputBuilder = {
    smartPoolId = id
    this
  }

  /**
   * Sets registers and smart pool id of metadata box, this function should only be called once.
   * @return This metadata box builder
   */
  def setMetadata(metadataRegs: MetadataRegisters): MetadataOutputBuilder = {
    metadataRegisters = metadataRegs
    registerList(0) = metadataRegs.shareConsensus.getErgoValue
    registerList(1) = metadataRegs.memberList.getErgoValue
    registerList(2) = metadataRegs.poolFees.getErgoValue
    registerList(3) = metadataRegs.poolInfo.getErgoValue
    registerList(4) = metadataRegs.poolOps.getErgoValue

    this
  }

  def build(): MetadataOutBox = {
    val completeTokenList = List[ErgoToken](new ErgoToken(smartPoolId, 1))++tokenList

    asOutBoxBuilder.tokens(completeTokenList:_*)
    asOutBoxBuilder.registers(registerList: _*)
    new MetadataOutBox(asOutBoxBuilder.build(), metadataRegisters, smartPoolId)
  }

  /**
   * We return a simple outbox if building for the initial metadata box. This ensures
   * smartpool id is not set and that tokens are not minted.
   */
  def buildInitial(): OutBox = {
    asOutBoxBuilder.registers(registerList: _*)
    asOutBoxBuilder.build()
  }

  /**
   * Builds the metadata box with a specific SmartPool NFT token, thereby allowing tokens to have custom names
   * and descriptions according to EIP-004
   */
  def buildInitialWithToken(smartPoolNFT: ErgoToken): OutBox = {
    val completeTokenList = List[ErgoToken](smartPoolNFT)++tokenList

    asOutBoxBuilder.tokens(completeTokenList:_*)
    asOutBoxBuilder.registers(registerList: _*)
    asOutBoxBuilder.build()
  }

}
