package contracts.voting

import app.AppParameters
import org.ergoplatform.appkit.{Address, BlockchainContext, Constants, ConstantsBuilder, ErgoContract, ErgoId}
import registers.BytesColl
import scorex.crypto.hash.Blake2b256
import sigmastate.Values

class ProxyBallotContract(ergoContract: ErgoContract) extends ErgoContract {
  override def getConstants: Constants = ergoContract.getConstants

  override def getErgoScript: String = ergoContract.getErgoScript

  override def substConstant(name: String, value: Any): ErgoContract = ergoContract.substConstant(name, value)

  override def getErgoTree: Values.ErgoTree = ergoContract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  def asErgoContract: ErgoContract = ergoContract
}

object ProxyBallotContract{

  // Uses arbitrary script differentiation to ensure right contracts are being used
  def script(voteYes: Boolean): String =
    s"""
    {
       val tokensExist = SELF.tokens.size > 1 && INPUTS(0).tokens.size > 1

       // Arbitrary difference to ensure different proposition bytes
       val regCheck = ${if(voteYes) "INPUTS(0).R4[Long].isDefined" else "INPUTS(0).R5[Long].isDefined"}

       val tokensValid =
        if(tokensExist){
          SELF.tokens(0)._1 == const_voteTokenId && INPUTS(0).tokens(0)._1 == const_recordingNFT
        }else{
          true
        }

       sigmaProp(tokensValid) && sigmaProp(regCheck)
    }
      """.stripMargin

  /**
   * To simplify voting process, we only allow one P2PK address to reclaim lost funds above minimum amount required.
   * @param ctx
   * @param voteTokenId
   * @param voteYes
   * @return
   */
  def generateContract(ctx: BlockchainContext, voteTokenId: ErgoId, voteYes: Boolean, recordingNFT: ErgoId): ProxyBallotContract = {

    val voteTokenBytes = BytesColl.fromConversionValues(voteTokenId.getBytes)
    val recordingBytes = BytesColl.fromConversionValues(recordingNFT.getBytes)
    val constants = ConstantsBuilder.create()
      .item("const_voteTokenId", voteTokenBytes.nValue)
      .item("const_recordingNFT", recordingBytes.nValue)
      .build()
    val contract: ErgoContract = ctx.compileContract(constants, script(voteYes))
    new ProxyBallotContract(contract)
  }

}
