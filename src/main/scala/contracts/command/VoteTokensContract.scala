package contracts.command

import app.AppParameters
import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import registers.BytesColl
import special.sigma.SigmaProp

/**
 * Command contract that distributes vote tokens equal to share number in contract
 * @param voteTokenId id of vote tokens to use.
 */
class VoteTokensContract(ergoContract: ErgoContract, voteTokenId: ErgoId) extends CommandContract(ergoContract) {

  override def getAddress: Address = Address.fromErgoTree(ergoContract.getErgoTree, AppParameters.networkType)

  /**
   * To apply this command contract to the outputted command box, we must ensure that payouts are stored.
   **/
  def applyToCommand(commandOutputBuilder: CommandOutputBuilder): CommandOutputBuilder = commandOutputBuilder

  /**
   * This contract performs no specific changes to the holding outputs.
   */
  override def applyToHolding(holdingOutputBuilder: HoldingOutputBuilder): HoldingOutputBuilder = {
    val outBoxBuilders = holdingOutputBuilder.getOutBoxBuilders

    for(outB <- outBoxBuilders if outB._2._2){
      val voteTokens = new ErgoToken(voteTokenId, outB._2._1)
      outB._1.tokens(voteTokens)
    }
    holdingOutputBuilder
  }


}

object VoteTokensContract {

  val script: String =
    """
      |{
      | val currentConsensus = SELF.R4[Coll[(Coll[Byte], Coll[Long])]].get
      | val tokensDistributed = currentConsensus.forall{
      |   (consVal: (Coll[Byte], Coll[Long]) =>
      |   val outputIndex = OUTPUTS.indexOf(consVal._1, 0)
      |   if(outputIndex != -1){
      |    OUTPUTS(outputIndex).tokens(0)._1 == const_voteTokenId && OUTPUTS(outputIndex).tokens(0)._2 == OUTPUTS(outputIndex).value
      |   }else{
      |     true
      |   }
      | sigmaProp(tokensDistributed) && const_nodePK
      |}
      |""".stripMargin

  def generateContract(ctx: BlockchainContext, voteTokenId: ErgoId, nodeAddress: Address): ErgoContract ={
    val nodePK = nodeAddress.getPublicKey
    val voteTokenBytes: BytesColl = BytesColl.fromConversionValues(voteTokenId.getBytes)
    val constantsBuilder = ConstantsBuilder.create()

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_nodePK", nodePK)
      .item("const_voteTokenId", voteTokenBytes.nValue)
      .build(), script)
    new VoteTokensContract(compiledContract, voteTokenId)
  }
}





