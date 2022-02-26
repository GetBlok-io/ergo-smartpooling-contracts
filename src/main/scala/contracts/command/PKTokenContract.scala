package contracts.command

import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import transactions.DistributionTx

class PKTokenContract(p2pkAddress: Address, voteTokenId: ErgoId) extends CommandContract(new ErgoTreeContract(p2pkAddress.getErgoAddress.script, p2pkAddress.getNetworkType)) {

  override def getAddress: Address = p2pkAddress

  /**
   * A simple P2PK Command Contract performs no specifically coded changes to the command box. Therefore,
   * we may simply return the command output builder that was passed in.
   */
  def applyToCommand(commandOutputBuilder: CommandOutputBuilder): CommandOutputBuilder = commandOutputBuilder

  /**
   * A simple P2PK Command Contract performs no specifically coded changes to the holding outputs. Therefore,
   * we may simply return the holding output builder that was passed in.
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


