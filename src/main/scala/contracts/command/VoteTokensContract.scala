package contracts.command

import app.AppParameters
import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import registers.BytesColl
import special.sigma.SigmaProp
import transactions.DistributionTx

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
   * This contract adds tokens to the holding outputs
   */
  override def applyToHolding(distributionTx: DistributionTx): HoldingOutputBuilder = {
    val hOB = distributionTx.holdingOutputBuilder
    var holdingMap = hOB.getOutBoxBuilders
    println("Old map hashcode " + holdingMap.hashCode())
    holdingMap = holdingMap.map{hm => hm}
    for(outB <- holdingMap){
      println("HoldingOutputValue: " + outB._2._1)
      println("isConsensusVal: " + outB._2._2)

      if(outB._2._2) {
        println("Output is consensus val, adding tokens to output")
        val voteTokens = new ErgoToken(voteTokenId, outB._2._1)
        println("Old hashcode " + outB._1.hashCode())
        val newOutB = outB._1.tokens(voteTokens)
        println("New hashcode " + newOutB.hashCode())
        holdingMap = holdingMap--List(outB._1)
        holdingMap = holdingMap++Map((newOutB, outB._2))
      }
    }
    println("New map hashcode: " + holdingMap.hashCode())
    new HoldingOutputBuilder(holdingMap)

  }


}

object VoteTokensContract {

  val script: String =
    """
      |{
      | val currentConsensus = SELF.R4[Coll[(Coll[Byte], Coll[Long])]].get
      | val outputProps = OUTPUTS.map{(box:Box) => box.propositionBytes}
      | val tokensDistributed = currentConsensus.forall{
      |   (consVal: (Coll[Byte], Coll[Long])) =>
      |   val outputIndex = outputProps.indexOf(consVal._1, 0)
      |   if(outputIndex != -1){
      |     if(OUTPUTS(outputIndex).tokens.size > 0){
      |       OUTPUTS(outputIndex).tokens(0)._1 == const_voteTokenId && OUTPUTS(outputIndex).tokens(0)._2 == OUTPUTS(outputIndex).value
      |     }else{
      |       false
      |     }
      |   }else{
      |     true
      |   }
      | }
      | sigmaProp(tokensDistributed) && proveDlog(const_nodeGE)
      |}
      |""".stripMargin

  def generateContract(ctx: BlockchainContext, voteTokenId: ErgoId, nodeAddress: Address): CommandContract ={
    val nodeGE = nodeAddress.getPublicKeyGE
    val voteTokenBytes: BytesColl = BytesColl.convert(voteTokenId.getBytes)
    val constantsBuilder = ConstantsBuilder.create()

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_nodeGE", nodeGE)
      .item("const_voteTokenId", voteTokenBytes.nValue)
      .build(), script)
    new VoteTokensContract(compiledContract, voteTokenId)
  }
}





